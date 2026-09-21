#include "gtest_support.h"
#include <string>
#include <string_view>

#include "application/ssh_settings_service.h"
#include "domain/ssh_config.h"
#include "infrastructure/ssh_config_codec.h"
#include "presentation/ssh_settings_presentation.h"

namespace {

void VerifyDefaultsAndNormalization() {
  using namespace linecode::domain;
  const SshConfig defaults;
  EXPECT_EXPRESSION(defaults.host == "127.0.0.1");
  EXPECT_EXPRESSION(defaults.port == 8022);
  EXPECT_EXPRESSION(!defaults.IsConfigured());

  auto normalized = NormalizeSshConfig(SshConfig{
      .host = "  ssh.example.test  ",
      .port = -1,
      .username = "  line  ",
      .password = " password with spaces ",
      .private_key = " key\n",
      .passphrase = " passphrase ",
  });
  EXPECT_EXPRESSION(normalized.host == "ssh.example.test");
  EXPECT_EXPRESSION(normalized.port == 8022);
  EXPECT_EXPRESSION(normalized.username == "line");
  EXPECT_EXPRESSION(normalized.password == " password with spaces ");
  EXPECT_EXPRESSION(normalized.private_key == " key\n");
  EXPECT_EXPRESSION(normalized.passphrase == " passphrase ");
  EXPECT_EXPRESSION(normalized.IsConfigured());

  EXPECT_EXPRESSION(ParseSshPort("22") == 22);
  EXPECT_EXPRESSION(ParseSshPort(" 8022\n") == 8022);
  EXPECT_EXPRESSION(ParseSshPort("0") == 8022);
  EXPECT_EXPRESSION(ParseSshPort("invalid") == 8022);
  EXPECT_EXPRESSION(IsTermuxSshHost("127.0.0.1"));
  EXPECT_EXPRESSION(IsTermuxSshHost(" localhost "));
  EXPECT_EXPRESSION(IsTermuxSshHost("LOCALHOST"));
  EXPECT_EXPRESSION(!IsTermuxSshHost("192.168.1.2"));
}

void VerifyLegacyCodec() {
  using namespace linecode;
  const domain::SshConfig config{
      .host = "server.example.test",
      .port = 2222,
      .username = "operator",
      .password = "secret \" value",
      .private_key = "-----BEGIN OPENSSH PRIVATE KEY-----\nabc\n",
      .passphrase = "phrase",
  };
  const auto decoded =
      infrastructure::DecodeSshConfig(infrastructure::EncodeSshConfig(config));
  EXPECT_EXPRESSION(decoded);
  EXPECT_EXPRESSION(*decoded == config);

  const auto partial =
      infrastructure::DecodeSshConfig(R"({"username":" user "})");
  EXPECT_EXPRESSION(partial);
  EXPECT_EXPRESSION(partial->host == domain::kDefaultSshHost);
  EXPECT_EXPRESSION(partial->port == domain::kDefaultSshPort);
  EXPECT_EXPRESSION(partial->username == "user");

  const auto string_port = infrastructure::DecodeSshConfig(
      R"({"host":"host","port":"2200","username":"u","password":"p"})");
  EXPECT_EXPRESSION(string_port);
  EXPECT_EXPRESSION(string_port->port == 2200);
  EXPECT_EXPRESSION(string_port->IsConfigured());

  const auto scalar_host =
      infrastructure::DecodeSshConfig(R"({"host":4,"port":22})");
  EXPECT_EXPRESSION(scalar_host);
  EXPECT_EXPRESSION(scalar_host->host == "4");

  EXPECT_EXPRESSION(!infrastructure::DecodeSshConfig("not-json"));
  EXPECT_EXPRESSION(!infrastructure::DecodeSshConfig("[]"));
  static_assert(application::ssh_setting_keys::config ==
                std::string_view{"@lineai_ssh_config"});
}

void VerifyTermuxEntryRemainsActionableWithoutCapability() {
  using linecode::presentation::ResolveTermuxEntryAction;
  using linecode::presentation::TermuxEntryAction;
  EXPECT_EXPRESSION(ResolveTermuxEntryAction(true) ==
         TermuxEntryAction::open_integration);
  EXPECT_EXPRESSION(ResolveTermuxEntryAction(false) ==
         TermuxEntryAction::show_unavailable);
}

} // namespace

TEST(ssh_settings_tests, LegacySuite) {
  VerifyDefaultsAndNormalization();
  VerifyLegacyCodec();
  VerifyTermuxEntryRemainsActionableWithoutCapability();
}
