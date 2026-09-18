#include <cassert>
#include <string>
#include <string_view>

#include "application/ssh_settings_service.h"
#include "domain/ssh_config.h"
#include "infrastructure/ssh_config_codec.h"

namespace {

void VerifyDefaultsAndNormalization() {
  using namespace linecode::domain;
  const SshConfig defaults;
  assert(defaults.host == "127.0.0.1");
  assert(defaults.port == 8022);
  assert(!defaults.IsConfigured());

  auto normalized = NormalizeSshConfig(SshConfig{
      .host = "  ssh.example.test  ",
      .port = -1,
      .username = "  line  ",
      .password = " password with spaces ",
      .private_key = " key\n",
      .passphrase = " passphrase ",
  });
  assert(normalized.host == "ssh.example.test");
  assert(normalized.port == 8022);
  assert(normalized.username == "line");
  assert(normalized.password == " password with spaces ");
  assert(normalized.private_key == " key\n");
  assert(normalized.passphrase == " passphrase ");
  assert(normalized.IsConfigured());

  assert(ParseSshPort("22") == 22);
  assert(ParseSshPort(" 8022\n") == 8022);
  assert(ParseSshPort("0") == 8022);
  assert(ParseSshPort("invalid") == 8022);
  assert(IsTermuxSshHost("127.0.0.1"));
  assert(IsTermuxSshHost(" localhost "));
  assert(IsTermuxSshHost("LOCALHOST"));
  assert(!IsTermuxSshHost("192.168.1.2"));
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
  assert(decoded);
  assert(*decoded == config);

  const auto partial =
      infrastructure::DecodeSshConfig(R"({"username":" user "})");
  assert(partial);
  assert(partial->host == domain::kDefaultSshHost);
  assert(partial->port == domain::kDefaultSshPort);
  assert(partial->username == "user");

  const auto string_port = infrastructure::DecodeSshConfig(
      R"({"host":"host","port":"2200","username":"u","password":"p"})");
  assert(string_port);
  assert(string_port->port == 2200);
  assert(string_port->IsConfigured());

  const auto scalar_host =
      infrastructure::DecodeSshConfig(R"({"host":4,"port":22})");
  assert(scalar_host);
  assert(scalar_host->host == "4");

  assert(!infrastructure::DecodeSshConfig("not-json"));
  assert(!infrastructure::DecodeSshConfig("[]"));
  static_assert(application::ssh_setting_keys::config ==
                std::string_view{"@lineai_ssh_config"});
}

} // namespace

int main() {
  VerifyDefaultsAndNormalization();
  VerifyLegacyCodec();
}
