#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <memory>
#include <span>
#include <stop_token>
#include <string>
#include <string_view>
#include <vector>

#include "domain/ssh_config.h"

namespace linecode::application {

enum class SshErrorCode : std::uint8_t {
  invalid_argument,
  not_configured,
  name_resolution,
  connection_failed,
  handshake_failed,
  host_key_unknown,
  host_key_mismatch,
  authentication_failed,
  timeout,
  cancelled,
  protocol,
  command_failed,
  not_found,
  conflict,
  outside_workspace,
  symbolic_link,
  protected_path,
  size_limit,
  io,
};

struct SshError final {
  SshErrorCode code{SshErrorCode::protocol};
  // Messages are safe for presentation/logging and must never contain a
  // password, private key, passphrase, or raw authentication payload.
  std::string message;

  bool operator==(const SshError &) const = default;
};

template <class Value>
using SshResult = std::expected<Value, SshError>;

enum class SshHostKeyPolicy : std::uint8_t {
  strict,
  trust_on_first_use,
};

struct SshCommandRequest final {
  std::string command;
  std::string working_directory;
  std::chrono::milliseconds timeout{30'000};
  std::size_t maximum_output_bytes{4U * 1024U * 1024U};

  bool operator==(const SshCommandRequest &) const = default;
};

struct SshCommandOutput final {
  std::int32_t exit_status{};
  std::string standard_output;
  std::string standard_error;

  bool operator==(const SshCommandOutput &) const = default;
};

enum class SshFileKind : std::uint8_t {
  regular,
  directory,
  symbolic_link,
  other,
};

struct SshFileEntry final {
  std::string name;
  std::string path;
  SshFileKind kind{SshFileKind::other};
  std::uint64_t size{};

  bool operator==(const SshFileEntry &) const = default;
};

// Local capability used by host-key verification.  Keeping persistence behind
// this port makes TOFU atomic and testable without exposing local paths to the
// SSH protocol adapter.
class SshKnownHostsStore {
public:
  virtual ~SshKnownHostsStore() = default;

  [[nodiscard]] virtual SshResult<std::string> Load() = 0;
  [[nodiscard]] virtual SshResult<void> Replace(std::string value) = 0;
};

// One authenticated SSH connection.  Calls are blocking and must run inside a
// HuxerUI worker.  Implementations observe the supplied stop token while
// waiting on the network.
class SshSession {
public:
  virtual ~SshSession() = default;

  [[nodiscard]] virtual SshResult<SshCommandOutput>
  Execute(const SshCommandRequest &request, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<std::string>
  CanonicalPath(std::string_view path, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<SshFileEntry>
  Stat(std::string_view path, bool follow_links, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<std::vector<SshFileEntry>>
  List(std::string_view path, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<std::vector<std::byte>>
  Read(std::string_view path, std::size_t maximum_bytes,
       std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<void>
  Write(std::string_view path, std::span<const std::byte> value,
        bool overwrite, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<void>
  CreateDirectory(std::string_view path, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<void>
  Rename(std::string_view source, std::string_view destination,
         bool overwrite, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<void>
  RemoveFile(std::string_view path, std::stop_token stop) = 0;
  [[nodiscard]] virtual SshResult<void>
  RemoveDirectory(std::string_view path, std::stop_token stop) = 0;
};

class SshTransport {
public:
  virtual ~SshTransport() = default;

  [[nodiscard]] virtual SshResult<std::unique_ptr<SshSession>>
  Connect(const domain::SshConfig &config, std::chrono::milliseconds timeout,
          std::stop_token stop) = 0;
};

} // namespace linecode::application
