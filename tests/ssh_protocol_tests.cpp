#include <cassert>
#include <chrono>
#include <cstddef>
#include <cstdlib>
#include <memory>
#include <stop_token>
#include <string>
#include <string_view>

#include <huxerui/file.h>

#include "domain/ssh_config.h"
#include "infrastructure/hux_known_hosts_store.h"
#include "infrastructure/libssh2_transport.h"

namespace {

using namespace std::chrono_literals;
using namespace linecode;

std::string ReadText(std::string_view path) {
  auto read = huxerui::File{path}.ReadString();
  assert(read.Succeeded());
  return std::move(read.Value());
}

} // namespace

int main(int argc, char **argv) {
  assert(argc == 7);
  const auto port = domain::ParseSshPort(argv[1]);
  assert(port > 0);
  const std::string user{argv[2]};
  const std::string private_key = ReadText(argv[3]);
  const huxerui::File known_hosts{argv[4]};
  const std::string remote_root{argv[5]};
  const std::string alternate_host_public_key = ReadText(argv[6]);
  const domain::SshConfig config{
      .host = "127.0.0.1",
      .port = port,
      .username = user,
      .password = {},
      .private_key = private_key,
      .passphrase = {},
  };
  std::stop_source cancellation;

  auto strict_store = std::make_shared<infrastructure::HuxKnownHostsStore>(
      huxerui::File{known_hosts.Path() + ".strict"});
  infrastructure::Libssh2Transport strict_transport{
      strict_store, application::SshHostKeyPolicy::strict};
  auto untrusted = strict_transport.Connect(config, 5s, cancellation.get_token());
  assert(!untrusted);
  assert(untrusted.error().code == application::SshErrorCode::host_key_unknown);

  auto store =
      std::make_shared<infrastructure::HuxKnownHostsStore>(known_hosts);
  infrastructure::Libssh2Transport transport{store};
  auto connected = transport.Connect(config, 5s, cancellation.get_token());
  assert(connected);
  auto &session = **connected;

  auto command = session.Execute(
      application::SshCommandRequest{
          .command = "printf 'stdout-ok'; printf 'stderr-ok' >&2; exit 7",
          .working_directory = remote_root,
          .timeout = 5s,
          .maximum_output_bytes = 64U * 1024U,
      },
      cancellation.get_token());
  assert(command);
  assert(command->exit_status == 7);
  assert(command->standard_output == "stdout-ok");
  assert(command->standard_error == "stderr-ok");

  auto timeout = session.Execute(
      application::SshCommandRequest{
          .command = "sleep 2",
          .working_directory = remote_root,
          .timeout = 100ms,
          .maximum_output_bytes = 1024U,
      },
      cancellation.get_token());
  assert(!timeout);
  assert(timeout.error().code == application::SshErrorCode::timeout);
  connected->reset();

  // A fresh connection verifies the TOFU entry persisted, then exercises the
  // actual SFTP subsystem rather than a repository fake.
  connected = transport.Connect(config, 5s, cancellation.get_token());
  assert(connected);
  auto &files = **connected;
  const std::string directory = remote_root + "/workspace";
  const std::string file = directory + "/hello.txt";
  const std::string renamed = directory + "/renamed.txt";
  assert(files.CreateDirectory(directory, cancellation.get_token()));
  const std::string content{"hello over sftp"};
  assert(files.Write(file, std::as_bytes(std::span{content}), false,
                     cancellation.get_token()));
  auto listed = files.List(directory, cancellation.get_token());
  assert(listed && listed->size() == 1U);
  assert(listed->front().name == "hello.txt");
  auto read = files.Read(file, 1024U, cancellation.get_token());
  assert(read);
  assert(std::string(reinterpret_cast<const char *>(read->data()), read->size()) ==
         content);
  assert(files.Rename(file, renamed, false, cancellation.get_token()));
  assert(files.RemoveFile(renamed, cancellation.get_token()));
  assert(files.RemoveDirectory(directory, cancellation.get_token()));

  std::stop_source stopped;
  stopped.request_stop();
  auto cancelled = files.Execute(
      application::SshCommandRequest{
          .command = "sleep 2",
          .working_directory = remote_root,
          .timeout = 5s,
          .maximum_output_bytes = 1024U,
      },
      stopped.get_token());
  assert(!cancelled);
  assert(cancelled.error().code == application::SshErrorCode::cancelled);
  connected->reset();

  auto trusted = store->Load();
  assert(trusted && trusted->contains("[127.0.0.1]:" + std::to_string(port)));

  // Replace only the key material with a different valid host key.  The next
  // handshake must be rejected as a mismatch and must not silently overwrite
  // the previously trusted identity.
  const auto first_space = alternate_host_public_key.find(' ');
  const auto second_space = alternate_host_public_key.find(' ', first_space + 1U);
  assert(first_space != std::string::npos);
  const std::string algorithm = alternate_host_public_key.substr(0, first_space);
  const std::string encoded = alternate_host_public_key.substr(
      first_space + 1U,
      second_space == std::string::npos
          ? std::string::npos
          : second_space - first_space - 1U);
  assert(store->Replace("[127.0.0.1]:" + std::to_string(port) + " " +
                        algorithm + " " + encoded + "\n"));
  auto mismatch = transport.Connect(config, 5s, cancellation.get_token());
  assert(!mismatch);
  assert(mismatch.error().code ==
         application::SshErrorCode::host_key_mismatch);
}
