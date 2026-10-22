#include "gtest_support.h"
#include <chrono>
#include <cstddef>
#include <string>
#include <string_view>
#include <vector>

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

void VerifyAuthMethodNegotiation() {
  using namespace linecode::domain;
  using namespace std::chrono_literals;
  namespace application = linecode::application;

  const auto advertised =
      ParseSshAuthMethods("publickey,keyboard-interactive");
  EXPECT_EXPRESSION(advertised.public_key);
  EXPECT_EXPRESSION(!advertised.password);
  EXPECT_EXPRESSION(advertised.keyboard_interactive);
  EXPECT_EXPRESSION(advertised.Any());

  // Whitespace and unknown method names must not hide the known ones.
  const auto padded =
      ParseSshAuthMethods(" publickey , gssapi-with-mic ,password ");
  EXPECT_EXPRESSION(padded.public_key);
  EXPECT_EXPRESSION(padded.password);
  EXPECT_EXPRESSION(!padded.keyboard_interactive);
  EXPECT_EXPRESSION(!ParseSshAuthMethods("").Any());
  EXPECT_EXPRESSION(DescribeSshAuthMethods(ParseSshAuthMethods("")) == "none");
  EXPECT_EXPRESSION(DescribeSshAuthMethods(padded) == "publickey, password");

  const SshConfig both{.host = "host",
                       .port = 22,
                       .username = "line",
                       .password = "secret",
                       .private_key = "-----BEGIN-----",
                       .passphrase = {}};
  const SshConfig password_only{.host = "host",
                                .port = 22,
                                .username = "line",
                                .password = "secret",
                                .private_key = {},
                                .passphrase = {}};
  const SshConfig key_only{.host = "host",
                           .port = 22,
                           .username = "line",
                           .password = {},
                           .private_key = "-----BEGIN-----",
                           .passphrase = {}};

  // Keys are tried before secrets, and a PAM-only server still gets a chance
  // through the keyboard-interactive exchange.
  EXPECT_EXPRESSION(
      PlanSshAuthentication(both, ParseSshAuthMethods("publickey,password")) ==
      std::vector<SshAuthMethod>{SshAuthMethod::public_key,
                                 SshAuthMethod::password});
  EXPECT_EXPRESSION(
      PlanSshAuthentication(password_only,
                            ParseSshAuthMethods("publickey,keyboard-interactive")) ==
      std::vector<SshAuthMethod>{SshAuthMethod::keyboard_interactive});
  EXPECT_EXPRESSION(
      PlanSshAuthentication(both,
                            ParseSshAuthMethods("keyboard-interactive")) ==
      std::vector<SshAuthMethod>{SshAuthMethod::keyboard_interactive});
  EXPECT_EXPRESSION(
      PlanSshAuthentication(key_only, ParseSshAuthMethods("password")).empty());
  // A server that never answers the list query must not be written off.
  EXPECT_EXPRESSION(
      PlanSshAuthentication(both, SshAuthMethods{}) ==
      std::vector<SshAuthMethod>{SshAuthMethod::public_key,
                                 SshAuthMethod::password,
                                 SshAuthMethod::keyboard_interactive});
  EXPECT_EXPRESSION(PlanSshAuthentication(SshConfig{}, SshAuthMethods{}).empty());

  static_assert(application::ssh_setting_keys::config ==
                std::string_view{"@lineai_ssh_config"});
}

void VerifyConnectAttemptBudget() {
  using namespace linecode::domain;
  using namespace std::chrono_literals;
  // One address keeps the whole budget; several addresses get an even share so
  // a dead IPv6 address cannot consume the IPv4 attempt.
  EXPECT_EXPRESSION(ConnectAttemptBudget(30s, 1U) == 30s);
  EXPECT_EXPRESSION(ConnectAttemptBudget(30s, 0U) == 30s);
  EXPECT_EXPRESSION(ConnectAttemptBudget(30s, 3U) == 10s);
  EXPECT_EXPRESSION(ConnectAttemptBudget(30s, 2U) == 15s);
  // Never less than five seconds per address, and never more than what is left.
  EXPECT_EXPRESSION(ConnectAttemptBudget(10s, 4U) == 5s);
  EXPECT_EXPRESSION(ConnectAttemptBudget(3s, 4U) == 3s);
  EXPECT_EXPRESSION(ConnectAttemptBudget(1s, 8U) == 1s);
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
  VerifyAuthMethodNegotiation();
  VerifyConnectAttemptBudget();
  VerifyTermuxEntryRemainsActionableWithoutCapability();
}
