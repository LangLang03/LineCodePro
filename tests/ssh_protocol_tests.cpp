#include "gtest_support.h"

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

struct SshFixtureArguments final {
  std::string port;
  std::string user;
  std::string private_key_path;
  std::string known_hosts_path;
  std::string remote_root;
  std::string alternate_host_public_key_path;
};

SshFixtureArguments fixture_arguments;

std::string ReadText(std::string_view path) {
  auto read = huxerui::File{path}.ReadString();
  EXPECT_EXPRESSION(read.Succeeded());
  if (!read.Succeeded())
    return {};
  return std::move(read.Value());
}

} // namespace

TEST(ssh_protocol_tests, ProtocolFixture) {
  const auto port = domain::ParseSshPort(fixture_arguments.port);
  ASSERT_GT(port, 0);
  const std::string private_key =
      ReadText(fixture_arguments.private_key_path);
  const huxerui::File known_hosts{fixture_arguments.known_hosts_path};
  const std::string alternate_host_public_key =
      ReadText(fixture_arguments.alternate_host_public_key_path);
  const domain::SshConfig config{
      .host = "127.0.0.1",
      .port = port,
      .username = fixture_arguments.user,
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
  EXPECT_EXPRESSION(!untrusted);
  EXPECT_EXPRESSION(untrusted.error().code == application::SshErrorCode::host_key_unknown);

  auto store =
      std::make_shared<infrastructure::HuxKnownHostsStore>(known_hosts);
  infrastructure::Libssh2Transport transport{store};
  auto connected = transport.Connect(config, 5s, cancellation.get_token());
  EXPECT_EXPRESSION(connected);
  auto &session = **connected;

  auto command = session.Execute(
      application::SshCommandRequest{
          .command = "printf 'stdout-ok'; printf 'stderr-ok' >&2; exit 7",
          .working_directory = fixture_arguments.remote_root,
          .timeout = 5s,
          .maximum_output_bytes = 64U * 1024U,
      },
      cancellation.get_token());
  EXPECT_EXPRESSION(command);
  EXPECT_EXPRESSION(command->exit_status == 7);
  EXPECT_EXPRESSION(command->standard_output == "stdout-ok");
  EXPECT_EXPRESSION(command->standard_error == "stderr-ok");

  auto timeout = session.Execute(
      application::SshCommandRequest{
          .command = "sleep 2",
          .working_directory = fixture_arguments.remote_root,
          .timeout = 100ms,
          .maximum_output_bytes = 1024U,
      },
      cancellation.get_token());
  EXPECT_EXPRESSION(!timeout);
  EXPECT_EXPRESSION(timeout.error().code == application::SshErrorCode::timeout);
  connected->reset();

  // A fresh connection verifies the TOFU entry persisted, then exercises the
  // actual SFTP subsystem rather than a repository fake.
  connected = transport.Connect(config, 5s, cancellation.get_token());
  EXPECT_EXPRESSION(connected);
  auto &files = **connected;
  const std::string directory = fixture_arguments.remote_root + "/workspace";
  const std::string file = directory + "/hello.txt";
  const std::string renamed = directory + "/renamed.txt";
  EXPECT_EXPRESSION(files.CreateDirectory(directory, cancellation.get_token()));
  const std::string content{"hello over sftp"};
  EXPECT_EXPRESSION(files.Write(file, std::as_bytes(std::span{content}), false,
                     cancellation.get_token()));
  auto listed = files.List(directory, cancellation.get_token());
  EXPECT_EXPRESSION(listed && listed->size() == 1U);
  EXPECT_EXPRESSION(listed->front().name == "hello.txt");
  auto read = files.Read(file, 1024U, cancellation.get_token());
  EXPECT_EXPRESSION(read);
  EXPECT_EXPRESSION(std::string(reinterpret_cast<const char *>(read->data()), read->size()) ==
         content);
  EXPECT_EXPRESSION(files.Rename(file, renamed, false, cancellation.get_token()));
  EXPECT_EXPRESSION(files.RemoveFile(renamed, cancellation.get_token()));
  EXPECT_EXPRESSION(files.RemoveDirectory(directory, cancellation.get_token()));

  std::stop_source stopped;
  stopped.request_stop();
  auto cancelled = files.Execute(
      application::SshCommandRequest{
          .command = "sleep 2",
          .working_directory = fixture_arguments.remote_root,
          .timeout = 5s,
          .maximum_output_bytes = 1024U,
      },
      stopped.get_token());
  EXPECT_EXPRESSION(!cancelled);
  EXPECT_EXPRESSION(cancelled.error().code == application::SshErrorCode::cancelled);
  connected->reset();

  auto trusted = store->Load();
  EXPECT_EXPRESSION(trusted && trusted->contains("[127.0.0.1]:" + std::to_string(port)));

  // Replace only the key material with a different valid host key.  The next
  // handshake must be rejected as a mismatch and must not silently overwrite
  // the previously trusted identity.
  const auto first_space = alternate_host_public_key.find(' ');
  const auto second_space = alternate_host_public_key.find(' ', first_space + 1U);
  EXPECT_EXPRESSION(first_space != std::string::npos);
  const std::string algorithm = alternate_host_public_key.substr(0, first_space);
  const std::string encoded = alternate_host_public_key.substr(
      first_space + 1U,
      second_space == std::string::npos
          ? std::string::npos
          : second_space - first_space - 1U);
  EXPECT_EXPRESSION(store->Replace("[127.0.0.1]:" + std::to_string(port) + " " +
                        algorithm + " " + encoded + "\n"));
  auto mismatch = transport.Connect(config, 5s, cancellation.get_token());
  EXPECT_EXPRESSION(!mismatch);
  EXPECT_EXPRESSION(mismatch.error().code ==
         application::SshErrorCode::host_key_mismatch);
}

int main(int argc, char **argv) {
  if (argc != 7) {
    std::cerr << "ssh_protocol_tests expects six fixture arguments\n";
    return 2;
  }
  fixture_arguments = SshFixtureArguments{
      .port = argv[1],
      .user = argv[2],
      .private_key_path = argv[3],
      .known_hosts_path = argv[4],
      .remote_root = argv[5],
      .alternate_host_public_key_path = argv[6],
  };
  ::testing::InitGoogleTest(&argc, argv);
  return RUN_ALL_TESTS();
}
#include <iostream>
