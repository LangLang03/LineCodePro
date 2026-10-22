#include "infrastructure/libssh2_transport.h"

#include <algorithm>
#include <array>
#include <cerrno>
#include <chrono>
#include <climits>
#include <cstdlib>
#include <cstring>
#include <limits>
#include <mutex>
#include <string>
#include <thread>
#include <utility>

#include <libssh2.h>
#include <libssh2_sftp.h>

#ifdef _WIN32
#include <winsock2.h>
#include <ws2tcpip.h>
#else
#include <fcntl.h>
#include <netdb.h>
#include <netinet/in.h>
#include <netinet/tcp.h>
#include <sys/select.h>
#include <sys/socket.h>
#include <unistd.h>
#endif

namespace linecode::infrastructure {
namespace {

using application::SshError;
using application::SshErrorCode;
using application::SshFileEntry;
using application::SshFileKind;
using application::SshResult;
using Clock = std::chrono::steady_clock;

#ifdef _WIN32
using Socket = SOCKET;
constexpr Socket kInvalidSocket = INVALID_SOCKET;
#else
using Socket = int;
constexpr Socket kInvalidSocket = -1;
#endif

SshError Error(SshErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

SshError Cancelled() {
  return Error(SshErrorCode::cancelled, "SSH operation was cancelled");
}

SshError TimedOut() {
  return Error(SshErrorCode::timeout, "SSH operation timed out");
}

void CloseSocket(Socket socket) noexcept {
  if (socket == kInvalidSocket)
    return;
#ifdef _WIN32
  closesocket(socket);
#else
  close(socket);
#endif
}

bool SetNonBlocking(Socket socket) noexcept {
#ifdef _WIN32
  u_long enabled = 1;
  return ioctlsocket(socket, FIONBIO, &enabled) == 0;
#else
  const int flags = fcntl(socket, F_GETFL, 0);
  return flags >= 0 && fcntl(socket, F_SETFL, flags | O_NONBLOCK) == 0;
#endif
}

// Small interactive writes (channel requests, keystrokes, keepalives) should
// not wait for Nagle to coalesce them; SSH already batches its own packets.
void SetNoDelay(Socket socket) noexcept {
  const int enabled = 1;
#ifdef _WIN32
  static_cast<void>(setsockopt(
      socket, IPPROTO_TCP, TCP_NODELAY,
      reinterpret_cast<const char *>(&enabled), sizeof(enabled)));
#else
  static_cast<void>(setsockopt(socket, IPPROTO_TCP, TCP_NODELAY, &enabled,
                               sizeof(enabled)));
#endif
}

bool ConnectInProgress() noexcept {
#ifdef _WIN32
  const int error = WSAGetLastError();
  return error == WSAEWOULDBLOCK || error == WSAEINPROGRESS ||
         error == WSAEINVAL;
#else
  return errno == EINPROGRESS || errno == EWOULDBLOCK;
#endif
}

SshResult<void> WaitSocket(Socket socket, bool read, bool write,
                           Clock::time_point deadline, std::stop_token stop) {
  while (true) {
    if (stop.stop_requested())
      return std::unexpected(Cancelled());
    const auto now = Clock::now();
    if (now >= deadline)
      return std::unexpected(TimedOut());
    const auto remaining =
        std::chrono::duration_cast<std::chrono::microseconds>(deadline - now);
    const auto micros =
        std::min(remaining, std::chrono::microseconds{50'000});
    timeval timeout{.tv_sec = static_cast<long>(micros.count() / 1'000'000),
                    .tv_usec = static_cast<long>(micros.count() % 1'000'000)};
    fd_set read_set;
    fd_set write_set;
    FD_ZERO(&read_set);
    FD_ZERO(&write_set);
    if (read)
      FD_SET(socket, &read_set);
    if (write)
      FD_SET(socket, &write_set);
#ifdef _WIN32
    const int selected = select(0, read ? &read_set : nullptr,
                                write ? &write_set : nullptr, nullptr, &timeout);
#else
    const int selected = select(socket + 1, read ? &read_set : nullptr,
                                write ? &write_set : nullptr, nullptr, &timeout);
#endif
    if (selected > 0)
      return {};
    if (selected == 0)
      continue;
#ifndef _WIN32
    if (errno == EINTR)
      continue;
#endif
    return std::unexpected(
        Error(SshErrorCode::connection_failed, "SSH socket wait failed"));
  }
}

SshResult<void> WaitSession(LIBSSH2_SESSION *session, Socket socket,
                            Clock::time_point deadline, std::stop_token stop) {
  const int directions = libssh2_session_block_directions(session);
  const bool read = (directions & LIBSSH2_SESSION_BLOCK_INBOUND) != 0;
  const bool write = (directions & LIBSSH2_SESSION_BLOCK_OUTBOUND) != 0;
  return WaitSocket(socket, read || !write, write || !read, deadline, stop);
}

template <class Operation>
SshResult<int> RetryInt(LIBSSH2_SESSION *session, Socket socket,
                        Clock::time_point deadline, std::stop_token stop,
                        Operation operation) {
  while (true) {
    if (stop.stop_requested())
      return std::unexpected(Cancelled());
    const int result = operation();
    if (result != LIBSSH2_ERROR_EAGAIN)
      return result;
    if (auto ready = WaitSession(session, socket, deadline, stop); !ready)
      return std::unexpected(std::move(ready.error()));
  }
}

template <class Pointer, class Operation>
SshResult<Pointer *> RetryPointer(LIBSSH2_SESSION *session, Socket socket,
                                  Clock::time_point deadline,
                                  std::stop_token stop, Operation operation) {
  while (true) {
    if (stop.stop_requested())
      return std::unexpected(Cancelled());
    if (auto *result = operation())
      return result;
    if (libssh2_session_last_errno(session) != LIBSSH2_ERROR_EAGAIN)
      return static_cast<Pointer *>(nullptr);
    if (auto ready = WaitSession(session, socket, deadline, stop); !ready)
      return std::unexpected(std::move(ready.error()));
  }
}

SshError SessionError(LIBSSH2_SESSION *session, SshErrorCode code,
                      std::string fallback) {
  char *text{};
  int length{};
  static_cast<void>(libssh2_session_last_error(session, &text, &length, 0));
  // A retried call leaves LIBSSH2_ERROR_EAGAIN ("Would block ...") behind in
  // the session's single error slot. That text describes an earlier, already
  // handled condition, so appending it to an unrelated failure produced
  // messages such as "SSH public key authentication failed: Would block
  // requesting userauth list" that pointed at the wrong problem.
  if (text && length > 0 &&
      libssh2_session_last_errno(session) != LIBSSH2_ERROR_EAGAIN) {
    fallback += ": ";
    fallback.append(text, static_cast<std::size_t>(length));
  }
  return Error(code, std::move(fallback));
}

// Errors that mean the session itself is unusable. No other credential can
// repair them, so authentication must stop instead of trying the next method.
bool IsFatalTransportError(int code) noexcept {
  switch (code) {
  case LIBSSH2_ERROR_ALLOC:
  case LIBSSH2_ERROR_SOCKET_SEND:
  case LIBSSH2_ERROR_SOCKET_RECV:
  case LIBSSH2_ERROR_SOCKET_DISCONNECT:
  case LIBSSH2_ERROR_SOCKET_TIMEOUT:
  case LIBSSH2_ERROR_BAD_SOCKET:
  case LIBSSH2_ERROR_TIMEOUT:
    return true;
  default:
    return false;
  }
}

SshResult<Socket> ConnectSocket(std::string_view host, std::int32_t port,
                                Clock::time_point deadline,
                                std::stop_token stop) {
#ifdef _WIN32
  static std::once_flag winsock_once;
  static int winsock_status{};
  std::call_once(winsock_once, [] {
    WSADATA data{};
    winsock_status = WSAStartup(MAKEWORD(2, 2), &data);
  });
  if (winsock_status != 0)
    return std::unexpected(Error(SshErrorCode::connection_failed,
                                 "Windows socket initialization failed"));
#endif
  addrinfo hints{};
  hints.ai_family = AF_UNSPEC;
  hints.ai_socktype = SOCK_STREAM;
  hints.ai_protocol = IPPROTO_TCP;
  addrinfo *addresses{};
  const std::string host_text{host};
  const std::string port_text = std::to_string(port);
  if (getaddrinfo(host_text.c_str(), port_text.c_str(), &hints, &addresses) !=
      0) {
    return std::unexpected(
        Error(SshErrorCode::name_resolution, "Unable to resolve SSH host"));
  }
  struct AddressOwner final {
    addrinfo *value;
    ~AddressOwner() { freeaddrinfo(value); }
  } owner{addresses};

  // A host frequently resolves to both an IPv6 and an IPv4 address while only
  // one of the two is routable (IPv6 on a mobile network without prefix
  // delegation, for example). Spending the whole connect budget on the first
  // unreachable address skipped every later address: the loop then reported
  // "timed out" although a reachable address was already known.
  std::size_t remaining_addresses{};
  for (auto *address = addresses; address; address = address->ai_next)
    ++remaining_addresses;
  for (auto *address = addresses; address; address = address->ai_next) {
    if (stop.stop_requested())
      return std::unexpected(Cancelled());
    const auto now = Clock::now();
    if (now >= deadline)
      return std::unexpected(TimedOut());
    const auto remaining =
        std::chrono::duration_cast<std::chrono::milliseconds>(deadline - now);
    const auto attempt_deadline =
        now + domain::ConnectAttemptBudget(remaining, remaining_addresses);
    if (remaining_addresses > 0U)
      --remaining_addresses;
    Socket socket = ::socket(address->ai_family, address->ai_socktype,
                             address->ai_protocol);
    if (socket == kInvalidSocket)
      continue;
    if (!SetNonBlocking(socket)) {
      CloseSocket(socket);
      continue;
    }
    const int connected =
        ::connect(socket, address->ai_addr,
                  static_cast<decltype(address->ai_addrlen)>(address->ai_addrlen));
    if (connected == 0) {
      SetNoDelay(socket);
      return socket;
    }
    if (!ConnectInProgress()) {
      CloseSocket(socket);
      continue;
    }
    auto ready = WaitSocket(socket, false, true, attempt_deadline, stop);
    if (!ready) {
      CloseSocket(socket);
      if (ready.error().code == SshErrorCode::cancelled)
        return std::unexpected(std::move(ready.error()));
      // A per-address timeout only rules out this address; the remaining ones
      // keep whatever budget is left.
      continue;
    }
    int socket_error{};
#ifdef _WIN32
    int error_size = sizeof(socket_error);
#else
    socklen_t error_size = sizeof(socket_error);
#endif
    if (getsockopt(socket, SOL_SOCKET, SO_ERROR,
#ifdef _WIN32
                   reinterpret_cast<char *>(&socket_error),
#else
                   &socket_error,
#endif
                   &error_size) == 0 &&
        socket_error == 0) {
      SetNoDelay(socket);
      return socket;
    }
    CloseSocket(socket);
  }
  if (Clock::now() >= deadline)
    return std::unexpected(TimedOut());
  return std::unexpected(Error(SshErrorCode::connection_failed,
                               "Unable to connect to SSH host"));
}

int KnownHostKeyType(int host_key_type) noexcept {
  switch (host_key_type) {
  case LIBSSH2_HOSTKEY_TYPE_RSA:
    return LIBSSH2_KNOWNHOST_KEY_SSHRSA;
  case LIBSSH2_HOSTKEY_TYPE_DSS:
    return LIBSSH2_KNOWNHOST_KEY_SSHDSS;
  case LIBSSH2_HOSTKEY_TYPE_ECDSA_256:
    return LIBSSH2_KNOWNHOST_KEY_ECDSA_256;
  case LIBSSH2_HOSTKEY_TYPE_ECDSA_384:
    return LIBSSH2_KNOWNHOST_KEY_ECDSA_384;
  case LIBSSH2_HOSTKEY_TYPE_ECDSA_521:
    return LIBSSH2_KNOWNHOST_KEY_ECDSA_521;
  case LIBSSH2_HOSTKEY_TYPE_ED25519:
    return LIBSSH2_KNOWNHOST_KEY_ED25519;
  default:
    return LIBSSH2_KNOWNHOST_KEY_UNKNOWN;
  }
}

std::string KnownHostName(std::string_view host, std::int32_t port) {
  if (port == 22)
    return std::string{host};
  return "[" + std::string{host} + "]:" + std::to_string(port);
}

SshResult<std::string> SerializeKnownHosts(LIBSSH2_KNOWNHOSTS *hosts) {
  std::string result;
  libssh2_knownhost *current{};
  libssh2_knownhost *previous{};
  while (libssh2_knownhost_get(hosts, &current, previous) == 0) {
    std::vector<char> line(512);
    std::size_t written{};
    int status = libssh2_knownhost_writeline(
        hosts, current, line.data(), line.size(), &written,
        LIBSSH2_KNOWNHOST_FILE_OPENSSH);
    while (status == LIBSSH2_ERROR_BUFFER_TOO_SMALL) {
      line.resize(line.size() * 2U);
      status = libssh2_knownhost_writeline(
          hosts, current, line.data(), line.size(), &written,
          LIBSSH2_KNOWNHOST_FILE_OPENSSH);
    }
    if (status < 0)
      return std::unexpected(Error(SshErrorCode::io,
                                   "Unable to serialize SSH known hosts"));
    result.append(line.data(), written);
    previous = current;
  }
  return result;
}

SshResult<void> VerifyHostKey(
    LIBSSH2_SESSION *session, const domain::SshConfig &config,
    application::SshKnownHostsStore &store,
    application::SshHostKeyPolicy policy) {
  auto content = store.Load();
  if (!content)
    return std::unexpected(std::move(content.error()));
  LIBSSH2_KNOWNHOSTS *hosts = libssh2_knownhost_init(session);
  if (!hosts)
    return std::unexpected(SessionError(session, SshErrorCode::protocol,
                                        "Unable to initialize known hosts"));
  struct Owner final {
    LIBSSH2_KNOWNHOSTS *value;
    ~Owner() { libssh2_knownhost_free(value); }
  } owner{hosts};
  std::size_t offset{};
  while (offset < content->size()) {
    const auto end = content->find('\n', offset);
    const auto length = end == std::string::npos ? content->size() - offset
                                                  : end + 1U - offset;
    const std::string_view line{*content};
    if (const auto row = line.substr(offset, length);
        row.find_first_not_of(" \t\r\n") != std::string_view::npos &&
        row[row.find_first_not_of(" \t\r\n")] != '#') {
      if (libssh2_knownhost_readline(hosts, row.data(), row.size(),
                                    LIBSSH2_KNOWNHOST_FILE_OPENSSH) < 0) {
        return std::unexpected(
            Error(SshErrorCode::io, "SSH known-hosts store is malformed"));
      }
    }
    offset += length;
  }

  std::size_t key_length{};
  int host_key_type{};
  const char *key =
      libssh2_session_hostkey(session, &key_length, &host_key_type);
  if (!key || key_length == 0)
    return std::unexpected(SessionError(session, SshErrorCode::protocol,
                                        "SSH server did not provide a host key"));
  libssh2_knownhost *matched{};
  const int checked = libssh2_knownhost_checkp(
      hosts, config.host.c_str(), config.port, key, key_length,
      LIBSSH2_KNOWNHOST_TYPE_PLAIN | LIBSSH2_KNOWNHOST_KEYENC_RAW, &matched);
  if (checked == LIBSSH2_KNOWNHOST_CHECK_MATCH)
    return {};
  if (checked == LIBSSH2_KNOWNHOST_CHECK_MISMATCH) {
    return std::unexpected(Error(
        SshErrorCode::host_key_mismatch,
        "SSH host key changed; connection was refused"));
  }
  if (checked != LIBSSH2_KNOWNHOST_CHECK_NOTFOUND) {
    return std::unexpected(
        Error(SshErrorCode::protocol, "Unable to verify SSH host key"));
  }
  if (policy == application::SshHostKeyPolicy::strict) {
    return std::unexpected(Error(SshErrorCode::host_key_unknown,
                                 "SSH host key is not trusted"));
  }
  const auto known_name = KnownHostName(config.host, config.port);
  const int added = libssh2_knownhost_addc(
      hosts, known_name.c_str(), nullptr, key, key_length, "LineCode TOFU",
      std::strlen("LineCode TOFU"),
      LIBSSH2_KNOWNHOST_TYPE_PLAIN | LIBSSH2_KNOWNHOST_KEYENC_RAW |
          KnownHostKeyType(host_key_type),
      nullptr);
  if (added < 0)
    return std::unexpected(SessionError(session, SshErrorCode::io,
                                        "Unable to trust SSH host key"));
  auto serialized = SerializeKnownHosts(hosts);
  if (!serialized)
    return std::unexpected(std::move(serialized.error()));
  return store.Replace(std::move(*serialized));
}

SshFileKind FileKind(const LIBSSH2_SFTP_ATTRIBUTES &attributes) noexcept {
  if ((attributes.flags & LIBSSH2_SFTP_ATTR_PERMISSIONS) == 0)
    return SshFileKind::other;
  const auto mode = attributes.permissions;
  if (LIBSSH2_SFTP_S_ISREG(mode))
    return SshFileKind::regular;
  if (LIBSSH2_SFTP_S_ISDIR(mode))
    return SshFileKind::directory;
  if (LIBSSH2_SFTP_S_ISLNK(mode))
    return SshFileKind::symbolic_link;
  return SshFileKind::other;
}

std::string Basename(std::string_view path) {
  while (path.size() > 1U && path.back() == '/')
    path.remove_suffix(1);
  const auto separator = path.find_last_of('/');
  return std::string{separator == std::string_view::npos
                         ? path
                         : path.substr(separator + 1U)};
}

std::string Join(std::string_view parent, std::string_view name) {
  while (parent.size() > 1U && parent.back() == '/')
    parent.remove_suffix(1);
  if (parent == "/")
    return "/" + std::string{name};
  return std::string{parent} + "/" + std::string{name};
}

std::string ShellQuote(std::string_view value) {
  std::string result{"'"};
  for (const char character : value) {
    if (character == '\'')
      result += "'\\''";
    else
      result.push_back(character);
  }
  result.push_back('\'');
  return result;
}

class Libssh2Session final : public application::SshSession {
public:
  Libssh2Session(Socket socket, LIBSSH2_SESSION *session,
                 std::chrono::milliseconds operation_timeout)
      : socket_(socket), session_(session), operation_timeout_(operation_timeout) {}

  ~Libssh2Session() override {
    if (sftp_)
      static_cast<void>(libssh2_sftp_shutdown(sftp_));
    if (session_) {
      static_cast<void>(libssh2_session_disconnect(session_, "LineCode close"));
      static_cast<void>(libssh2_session_free(session_));
    }
    CloseSocket(socket_);
  }

  SshResult<application::SshCommandOutput>
  Execute(const application::SshCommandRequest &request,
          std::stop_token stop) override;
  SshResult<std::string> CanonicalPath(std::string_view path,
                                       std::stop_token stop) override;
  SshResult<SshFileEntry> Stat(std::string_view path, bool follow_links,
                               std::stop_token stop) override;
  SshResult<std::vector<SshFileEntry>> List(std::string_view path,
                                            std::stop_token stop) override;
  SshResult<std::vector<std::byte>> Read(std::string_view path,
                                         std::size_t maximum_bytes,
                                         std::stop_token stop) override;
  SshResult<void> Write(std::string_view path,
                        std::span<const std::byte> value, bool overwrite,
                        std::stop_token stop) override;
  SshResult<void> CreateDirectory(std::string_view path,
                                  std::stop_token stop) override;
  SshResult<void> Rename(std::string_view source, std::string_view destination,
                         bool overwrite, std::stop_token stop) override;
  SshResult<void> RemoveFile(std::string_view path,
                             std::stop_token stop) override;
  SshResult<void> RemoveDirectory(std::string_view path,
                                  std::stop_token stop) override;

private:
  SshResult<LIBSSH2_SFTP *> Sftp(Clock::time_point deadline,
                                 std::stop_token stop);
  SshError SftpError(std::string fallback) const;
  Clock::time_point Deadline() const { return Clock::now() + operation_timeout_; }

  Socket socket_{kInvalidSocket};
  LIBSSH2_SESSION *session_{};
  LIBSSH2_SFTP *sftp_{};
  std::chrono::milliseconds operation_timeout_;
};

SshResult<LIBSSH2_SFTP *> Libssh2Session::Sftp(Clock::time_point deadline,
                                               std::stop_token stop) {
  if (sftp_)
    return sftp_;
  auto opened = RetryPointer<LIBSSH2_SFTP>(
      session_, socket_, deadline, stop,
      [this] { return libssh2_sftp_init(session_); });
  if (!opened)
    return std::unexpected(std::move(opened.error()));
  if (!*opened)
    return std::unexpected(SessionError(session_, SshErrorCode::protocol,
                                        "Unable to start SSH file transfer"));
  sftp_ = *opened;
  return sftp_;
}

SshError Libssh2Session::SftpError(std::string fallback) const {
  const auto code = sftp_ ? libssh2_sftp_last_error(sftp_) : 0UL;
  switch (code) {
  case LIBSSH2_FX_NO_SUCH_FILE:
  case LIBSSH2_FX_NO_SUCH_PATH:
    return Error(SshErrorCode::not_found, std::move(fallback));
  case LIBSSH2_FX_FILE_ALREADY_EXISTS:
    return Error(SshErrorCode::conflict, std::move(fallback));
  default:
    return SessionError(session_, SshErrorCode::protocol, std::move(fallback));
  }
}

SshResult<application::SshCommandOutput>
Libssh2Session::Execute(const application::SshCommandRequest &request,
                        std::stop_token stop) {
  const auto deadline = Clock::now() + request.timeout;
  auto opened = RetryPointer<LIBSSH2_CHANNEL>(
      session_, socket_, deadline, stop,
      [this] { return libssh2_channel_open_session(session_); });
  if (!opened)
    return std::unexpected(std::move(opened.error()));
  if (!*opened)
    return std::unexpected(SessionError(session_, SshErrorCode::protocol,
                                        "Unable to open SSH command channel"));
  LIBSSH2_CHANNEL *channel = *opened;
  struct Owner final {
    LIBSSH2_CHANNEL *value;
    ~Owner() { static_cast<void>(libssh2_channel_free(value)); }
  } owner{channel};
  std::string command = request.command;
  if (!request.working_directory.empty())
    command = "cd " + ShellQuote(request.working_directory) + " && " + command;
  auto started = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_channel_exec(channel, command.c_str());
  });
  if (!started)
    return std::unexpected(std::move(started.error()));
  if (*started != 0)
    return std::unexpected(SessionError(session_, SshErrorCode::protocol,
                                        "Unable to execute SSH command"));

  application::SshCommandOutput output;
  std::array<char, 8192> buffer{};
  while (true) {
    bool progressed{};
    for (int stream : {0, SSH_EXTENDED_DATA_STDERR}) {
      while (true) {
        const auto read = libssh2_channel_read_ex(channel, stream, buffer.data(),
                                                   buffer.size());
        if (read > 0) {
          progressed = true;
          auto &target = stream == 0 ? output.standard_output
                                     : output.standard_error;
          const auto bytes = static_cast<std::size_t>(read);
          if (output.standard_output.size() + output.standard_error.size() +
                  bytes >
              request.maximum_output_bytes) {
            return std::unexpected(Error(SshErrorCode::size_limit,
                                         "SSH command output exceeds its limit"));
          }
          target.append(buffer.data(), bytes);
          continue;
        }
        if (read != 0 && read != LIBSSH2_ERROR_EAGAIN)
          return std::unexpected(SessionError(session_, SshErrorCode::protocol,
                                              "Unable to read SSH command output"));
        break;
      }
    }
    if (libssh2_channel_eof(channel) != 0)
      break;
    if (!progressed) {
      if (auto ready = WaitSession(session_, socket_, deadline, stop); !ready)
        return std::unexpected(std::move(ready.error()));
    }
  }
  output.exit_status = libssh2_channel_get_exit_status(channel);
  static_cast<void>(RetryInt(session_, socket_, deadline, stop,
                             [&] { return libssh2_channel_close(channel); }));
  return output;
}

// `LIBSSH2_SFTP_REALPATH` with a growing buffer.  The reply is a path, not an
// attribute set, so a too-small buffer is reported by libssh2 itself rather
// than through `libssh2_sftp_last_error`.
SshResult<std::string> RealPath(LIBSSH2_SESSION *session, Socket socket,
                                LIBSSH2_SFTP *sftp, std::string_view path,
                                Clock::time_point deadline,
                                std::stop_token stop,
                                std::string_view missing_message) {
  std::vector<char> value(512);
  while (true) {
    auto resolved = RetryInt(session, socket, deadline, stop, [&] {
      return static_cast<int>(libssh2_sftp_symlink_ex(
          sftp, path.data(), static_cast<unsigned int>(path.size()),
          value.data(), static_cast<unsigned int>(value.size()),
          LIBSSH2_SFTP_REALPATH));
    });
    if (!resolved)
      return std::unexpected(std::move(resolved.error()));
    if (*resolved >= 0)
      return std::string{value.data(), static_cast<std::size_t>(*resolved)};
    if (*resolved == LIBSSH2_ERROR_BUFFER_TOO_SMALL &&
        value.size() < 64U * 1024U) {
      value.resize(value.size() * 2U);
      continue;
    }
    if (libssh2_sftp_last_error(sftp) == LIBSSH2_FX_NO_SUCH_FILE ||
        libssh2_sftp_last_error(sftp) == LIBSSH2_FX_NO_SUCH_PATH)
      return std::unexpected(
          Error(SshErrorCode::not_found, std::string{missing_message}));
    return std::unexpected(
        SessionError(session, SshErrorCode::protocol,
                     std::string{missing_message}));
  }
}

SshResult<std::string>
Libssh2Session::CanonicalPath(std::string_view path, std::stop_token stop) {
  if (path.empty() || path.find('\0') != std::string_view::npos)
    return std::unexpected(Error(SshErrorCode::invalid_argument,
                                 "SSH path is empty or malformed"));
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));

  std::string requested{path};
  if (requested == "~" || requested.starts_with("~/")) {
    // Tilde is shell syntax: neither SFTP nor `sftp-server` expands it, so
    // `realpath("~")` failed with "no such file" and every caller that used a
    // relative-to-home path (`Root` in the workspace service, the default SSH
    // project) reported the connection as unusable.  SFTP resolves `.` to the
    // authenticated user's home directory, which is the same value `~` means.
    auto home = RealPath(session_, socket_, *sftp, ".", deadline, stop,
                         "Unable to resolve the SSH home directory");
    if (!home)
      return std::unexpected(std::move(home.error()));
    requested = *home;
    if (path.size() > 1U)
      requested += std::string{path.substr(1)};
  }
  return RealPath(session_, socket_, *sftp, requested, deadline, stop,
                  "Unable to resolve SSH path");
}

SshResult<SshFileEntry> Libssh2Session::Stat(std::string_view path,
                                             bool follow_links,
                                             std::stop_token stop) {
  if (path.empty() || path.find('\0') != std::string_view::npos)
    return std::unexpected(Error(SshErrorCode::invalid_argument,
                                 "SSH path is empty or malformed"));
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  LIBSSH2_SFTP_ATTRIBUTES attributes{};
  auto status = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_sftp_stat_ex(
        *sftp, path.data(), static_cast<unsigned int>(path.size()),
        follow_links ? LIBSSH2_SFTP_STAT : LIBSSH2_SFTP_LSTAT, &attributes);
  });
  if (!status)
    return std::unexpected(std::move(status.error()));
  if (*status < 0)
    return std::unexpected(SftpError("Unable to inspect SSH path"));
  return SshFileEntry{
      .name = Basename(path),
      .path = std::string{path},
      .kind = FileKind(attributes),
      .size = (attributes.flags & LIBSSH2_SFTP_ATTR_SIZE) != 0
                  ? attributes.filesize
                  : 0U,
  };
}

SshResult<std::vector<SshFileEntry>>
Libssh2Session::List(std::string_view path, std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  auto opened = RetryPointer<LIBSSH2_SFTP_HANDLE>(
      session_, socket_, deadline, stop, [&] {
        return libssh2_sftp_open_ex(
            *sftp, path.data(), static_cast<unsigned int>(path.size()), 0, 0,
            LIBSSH2_SFTP_OPENDIR);
      });
  if (!opened)
    return std::unexpected(std::move(opened.error()));
  if (!*opened)
    return std::unexpected(SftpError("Unable to open SSH directory"));
  LIBSSH2_SFTP_HANDLE *directory = *opened;
  struct Owner final {
    LIBSSH2_SFTP_HANDLE *value;
    ~Owner() { static_cast<void>(libssh2_sftp_closedir(value)); }
  } owner{directory};
  std::vector<SshFileEntry> entries;
  std::array<char, 4096> name{};
  std::array<char, 8192> description{};
  while (true) {
    LIBSSH2_SFTP_ATTRIBUTES attributes{};
    auto read = RetryInt(session_, socket_, deadline, stop, [&] {
      return static_cast<int>(libssh2_sftp_readdir_ex(
          directory, name.data(), name.size(), description.data(),
          description.size(), &attributes));
    });
    if (!read)
      return std::unexpected(std::move(read.error()));
    if (*read == 0)
      break;
    if (*read < 0)
      return std::unexpected(SftpError("Unable to enumerate SSH directory"));
    const std::string child{name.data(), static_cast<std::size_t>(*read)};
    if (child == "." || child == "..")
      continue;
    entries.push_back(SshFileEntry{
        .name = child,
        .path = Join(path, child),
        .kind = FileKind(attributes),
        .size = (attributes.flags & LIBSSH2_SFTP_ATTR_SIZE) != 0
                    ? attributes.filesize
                    : 0U,
    });
  }
  return entries;
}

SshResult<std::vector<std::byte>>
Libssh2Session::Read(std::string_view path, std::size_t maximum_bytes,
                     std::stop_token stop) {
  auto info = Stat(path, false, stop);
  if (!info)
    return std::unexpected(std::move(info.error()));
  if (info->kind != SshFileKind::regular)
    return std::unexpected(Error(SshErrorCode::invalid_argument,
                                 "SSH path is not a regular file"));
  if (maximum_bytes > 0 && info->size > maximum_bytes)
    return std::unexpected(
        Error(SshErrorCode::size_limit, "SSH file exceeds its read limit"));
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  auto opened = RetryPointer<LIBSSH2_SFTP_HANDLE>(
      session_, socket_, deadline, stop, [&] {
        return libssh2_sftp_open_ex(
            *sftp, path.data(), static_cast<unsigned int>(path.size()),
            LIBSSH2_FXF_READ, 0, LIBSSH2_SFTP_OPENFILE);
      });
  if (!opened)
    return std::unexpected(std::move(opened.error()));
  if (!*opened)
    return std::unexpected(SftpError("Unable to open SSH file"));
  LIBSSH2_SFTP_HANDLE *file = *opened;
  struct Owner final {
    LIBSSH2_SFTP_HANDLE *value;
    ~Owner() { static_cast<void>(libssh2_sftp_close(value)); }
  } owner{file};
  std::vector<std::byte> output;
  output.reserve(static_cast<std::size_t>(
      std::min<std::uint64_t>(info->size, maximum_bytes)));
  std::array<char, 8192> buffer{};
  while (true) {
    auto read = RetryInt(session_, socket_, deadline, stop, [&] {
      return static_cast<int>(libssh2_sftp_read(file, buffer.data(),
                                                buffer.size()));
    });
    if (!read)
      return std::unexpected(std::move(read.error()));
    if (*read == 0)
      break;
    if (*read < 0)
      return std::unexpected(SftpError("Unable to read SSH file"));
    const auto bytes = static_cast<std::size_t>(*read);
    if (maximum_bytes > 0 && output.size() + bytes > maximum_bytes)
      return std::unexpected(
          Error(SshErrorCode::size_limit, "SSH file exceeds its read limit"));
    const auto *first = reinterpret_cast<const std::byte *>(buffer.data());
    output.insert(output.end(), first, first + bytes);
  }
  return output;
}

SshResult<void> Libssh2Session::Write(std::string_view path,
                                      std::span<const std::byte> value,
                                      bool overwrite,
                                      std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  const unsigned long flags = LIBSSH2_FXF_WRITE | LIBSSH2_FXF_CREAT |
                              LIBSSH2_FXF_TRUNC |
                              (overwrite ? 0UL : LIBSSH2_FXF_EXCL);
  auto opened = RetryPointer<LIBSSH2_SFTP_HANDLE>(
      session_, socket_, deadline, stop, [&] {
        return libssh2_sftp_open_ex(
            *sftp, path.data(), static_cast<unsigned int>(path.size()), flags,
            0600, LIBSSH2_SFTP_OPENFILE);
      });
  if (!opened)
    return std::unexpected(std::move(opened.error()));
  if (!*opened)
    return std::unexpected(SftpError("Unable to open SSH file for writing"));
  LIBSSH2_SFTP_HANDLE *file = *opened;
  struct Owner final {
    LIBSSH2_SFTP_HANDLE *value;
    ~Owner() { static_cast<void>(libssh2_sftp_close(value)); }
  } owner{file};
  std::size_t offset{};
  while (offset < value.size()) {
    auto written = RetryInt(session_, socket_, deadline, stop, [&] {
      return static_cast<int>(libssh2_sftp_write(
          file, reinterpret_cast<const char *>(value.data() + offset),
          value.size() - offset));
    });
    if (!written)
      return std::unexpected(std::move(written.error()));
    if (*written <= 0)
      return std::unexpected(SftpError("Unable to write SSH file"));
    offset += static_cast<std::size_t>(*written);
  }
  return {};
}

SshResult<void> Libssh2Session::CreateDirectory(std::string_view path,
                                                std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  auto status = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_sftp_mkdir_ex(*sftp, path.data(),
                                 static_cast<unsigned int>(path.size()), 0700);
  });
  if (!status)
    return std::unexpected(std::move(status.error()));
  if (*status < 0)
    return std::unexpected(SftpError("Unable to create SSH directory"));
  return {};
}

SshResult<void> Libssh2Session::Rename(std::string_view source,
                                       std::string_view destination,
                                       bool overwrite,
                                       std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  long flags = LIBSSH2_SFTP_RENAME_ATOMIC | LIBSSH2_SFTP_RENAME_NATIVE;
  if (overwrite)
    flags |= LIBSSH2_SFTP_RENAME_OVERWRITE;
  auto status = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_sftp_rename_ex(
        *sftp, source.data(), static_cast<unsigned int>(source.size()),
        destination.data(), static_cast<unsigned int>(destination.size()),
        flags);
  });
  if (!status)
    return std::unexpected(std::move(status.error()));
  if (*status < 0)
    return std::unexpected(SftpError("Unable to rename SSH path"));
  return {};
}

SshResult<void> Libssh2Session::RemoveFile(std::string_view path,
                                           std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  auto status = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_sftp_unlink_ex(*sftp, path.data(),
                                  static_cast<unsigned int>(path.size()));
  });
  if (!status)
    return std::unexpected(std::move(status.error()));
  if (*status < 0)
    return std::unexpected(SftpError("Unable to remove SSH file"));
  return {};
}

SshResult<void> Libssh2Session::RemoveDirectory(std::string_view path,
                                                std::stop_token stop) {
  const auto deadline = Deadline();
  auto sftp = Sftp(deadline, stop);
  if (!sftp)
    return std::unexpected(std::move(sftp.error()));
  auto status = RetryInt(session_, socket_, deadline, stop, [&] {
    return libssh2_sftp_rmdir_ex(*sftp, path.data(),
                                 static_cast<unsigned int>(path.size()));
  });
  if (!status)
    return std::unexpected(std::move(status.error()));
  if (*status < 0)
    return std::unexpected(SftpError("Unable to remove SSH directory"));
  return {};
}

} // namespace

Libssh2Transport::Libssh2Transport(
    std::shared_ptr<application::SshKnownHostsStore> known_hosts,
    application::SshHostKeyPolicy host_key_policy)
    : known_hosts_(std::move(known_hosts)), host_key_policy_(host_key_policy) {
  if (!known_hosts_)
    throw std::invalid_argument("SSH known-hosts store must not be empty");
  static std::once_flag initialized;
  static int initialize_status{};
  std::call_once(initialized, [] { initialize_status = libssh2_init(0); });
  if (initialize_status != 0)
    throw std::runtime_error("Unable to initialize libssh2");
}

application::SshResult<std::unique_ptr<application::SshSession>>
Libssh2Transport::Connect(const domain::SshConfig &input,
                          std::chrono::milliseconds timeout,
                          std::stop_token stop) {
  const auto config = domain::NormalizeSshConfig(input);
  if (!config.IsConfigured()) {
    return std::unexpected(Error(SshErrorCode::not_configured,
                                 "SSH is not configured"));
  }
  timeout = std::clamp(timeout, std::chrono::milliseconds{1'000},
                       std::chrono::milliseconds{300'000});
  const auto deadline = Clock::now() + timeout;
  auto connected = ConnectSocket(config.host, config.port, deadline, stop);
  if (!connected)
    return std::unexpected(std::move(connected.error()));
  Socket socket = *connected;
  LIBSSH2_SESSION *session = libssh2_session_init();
  if (!session) {
    CloseSocket(socket);
    return std::unexpected(
        Error(SshErrorCode::protocol, "Unable to create SSH session"));
  }
  libssh2_session_set_blocking(session, 0);
  auto fail = [&](SshError error) -> application::SshResult<
                                  std::unique_ptr<application::SshSession>> {
    static_cast<void>(libssh2_session_free(session));
    CloseSocket(socket);
    return std::unexpected(std::move(error));
  };
  auto handshake = RetryInt(session, socket, deadline, stop, [&] {
    return libssh2_session_handshake(session, socket);
  });
  if (!handshake)
    return fail(std::move(handshake.error()));
  if (*handshake != 0)
    return fail(SessionError(session, SshErrorCode::handshake_failed,
                             "SSH handshake failed"));
  if (auto verified = VerifyHostKey(session, config, *known_hosts_,
                                    host_key_policy_);
      !verified) {
    return fail(std::move(verified.error()));
  }
  // A mobile network drops idle NAT mappings within a couple of minutes, and
  // the socket then looks open until the next write.  Asking for a reply every
  // 20 seconds keeps the mapping alive and turns a silently dead session into
  // an error the next operation can report.
  libssh2_keepalive_config(session, 1, 20);

  domain::SshAuthMethods advertised;
  {
    auto listed = RetryPointer<char>(
        session, socket, deadline, stop, [&] {
          return libssh2_userauth_list(
              session, config.username.c_str(),
              static_cast<unsigned int>(config.username.size()));
        });
    if (!listed)
      return fail(std::move(listed.error()));
    if (*listed)
      advertised = domain::ParseSshAuthMethods(*listed);
    else if (libssh2_userauth_authenticated(session) != 0)
      // The list query itself authenticates when the server accepts the "none"
      // method; libssh2 reports that as a successful call with no list.
      return std::unique_ptr<application::SshSession>{
          std::make_unique<Libssh2Session>(socket, session, timeout)};
  }

  const auto plan = domain::PlanSshAuthentication(config, advertised);
  if (plan.empty()) {
    std::string message{"SSH authentication failed: the server offers "};
    message += domain::DescribeSshAuthMethods(advertised);
    message += " but no matching credential is configured";
    return fail(Error(SshErrorCode::authentication_failed, std::move(message)));
  }

  std::string failures;
  for (const auto method : plan) {
    if (stop.stop_requested())
      return fail(Cancelled());
    if (Clock::now() >= deadline)
      return fail(TimedOut());
    const auto append_failure = [&failures](std::string_view text) {
      if (!failures.empty())
        failures += "; ";
      failures += text;
    };
    switch (method) {
    case domain::SshAuthMethod::public_key: {
      auto key_auth = RetryInt(session, socket, deadline, stop, [&] {
        return libssh2_userauth_publickey_frommemory(
            session, config.username.c_str(), config.username.size(), nullptr,
            0, config.private_key.c_str(), config.private_key.size(),
            config.passphrase.empty() ? nullptr : config.passphrase.c_str());
      });
      if (!key_auth)
        return fail(std::move(key_auth.error()));
      if (*key_auth == 0)
        return std::unique_ptr<application::SshSession>{
            std::make_unique<Libssh2Session>(socket, session, timeout)};
      if (IsFatalTransportError(*key_auth))
        return fail(SessionError(session, SshErrorCode::authentication_failed,
                                 "SSH public key authentication failed"));
      // Every other rejection is local to this method: the key does not parse,
      // the server does not accept it, or the passphrase is wrong. Returning
      // here aborted the whole plan, so a stored key that had been truncated or
      // exported in an unsupported format made a correct password unusable too.
      append_failure(*key_auth == LIBSSH2_ERROR_FILE
                         ? "publickey could not be loaded (bad key format or "
                           "passphrase)"
                         : "publickey was rejected (wrong key or passphrase)");
      continue;
    }
    case domain::SshAuthMethod::password: {
      auto password_auth = RetryInt(session, socket, deadline, stop, [&] {
        return libssh2_userauth_password_ex(
            session, config.username.c_str(),
            static_cast<unsigned int>(config.username.size()),
            config.password.c_str(),
            static_cast<unsigned int>(config.password.size()), nullptr);
      });
      if (!password_auth)
        return fail(std::move(password_auth.error()));
      if (*password_auth == 0)
        return std::unique_ptr<application::SshSession>{
            std::make_unique<Libssh2Session>(socket, session, timeout)};
      if (IsFatalTransportError(*password_auth))
        return fail(SessionError(session, SshErrorCode::authentication_failed,
                                 "SSH password authentication failed"));
      append_failure("password was rejected");
      continue;
    }
    case domain::SshAuthMethod::keyboard_interactive: {
      // The prompt exchange is where PAM-backed servers ask for the password.
      // Every prompt gets the configured password: servers that also ask for a
      // one-time code cannot be satisfied here, and libssh2 frees the response
      // buffers itself after the callback returns.
      //
      // The callback only receives the session's abstract pointer, so the
      // secret travels through it and is cleared again afterwards: the pointer
      // outlives this function inside the session and must not refer to a
      // local.
      auto *abstract = libssh2_session_abstract(session);
      *abstract = const_cast<std::string *>(&config.password);
      struct AbstractReset final {
        void **value;
        ~AbstractReset() { *value = nullptr; }
      } abstract_reset{abstract};
      auto interactive = RetryInt(session, socket, deadline, stop, [&] {
        return libssh2_userauth_keyboard_interactive_ex(
            session, config.username.c_str(),
            static_cast<unsigned int>(config.username.size()),
            [](const char *, int, const char *, int, int num_prompts,
               const LIBSSH2_USERAUTH_KBDINT_PROMPT *,
               LIBSSH2_USERAUTH_KBDINT_RESPONSE *responses, void **abstract) {
              // `abstract` is the session's user pointer (see
              // libssh2_session_abstract), which Connect fills with the
              // password for the duration of this call.
              if (num_prompts <= 0 || !responses)
                return;
              const auto *secret =
                  abstract ? static_cast<const std::string *>(*abstract)
                           : nullptr;
              const std::string empty;
              const std::string &password = secret ? *secret : empty;
              for (int index = 0; index < num_prompts; ++index) {
                responses[index].text =
                    static_cast<char *>(malloc(password.size() + 1U));
                if (!responses[index].text) {
                  responses[index].length = 0;
                  continue;
                }
                std::memcpy(responses[index].text, password.c_str(),
                            password.size() + 1U);
                responses[index].length =
                    static_cast<unsigned int>(password.size());
              }
            });
      });
      if (!interactive)
        return fail(std::move(interactive.error()));
      if (*interactive == 0)
        return std::unique_ptr<application::SshSession>{
            std::make_unique<Libssh2Session>(socket, session, timeout)};
      append_failure("keyboard-interactive was rejected");
      continue;
    }
    }
  }
  std::string message{"SSH authentication failed"};
  if (!failures.empty()) {
    message += " (";
    message += failures;
    message += ")";
  }
  return fail(Error(SshErrorCode::authentication_failed, std::move(message)));
}

} // namespace linecode::infrastructure
