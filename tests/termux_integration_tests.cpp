#include <cassert>
#include <string>

#include "application/ports/termux_integration.h"
#include "domain/termux_integration.h"

namespace {

constexpr auto kPrivateKey = R"KEY(-----BEGIN RSA PRIVATE KEY-----
secret-material
-----END RSA PRIVATE KEY-----)KEY";

void ParsesCompleteSetupOutput() {
  const std::string output = std::string{
      "LINEAI_TERMUX_USERNAME=u0_a123\r\n"
      "LINEAI_TERMUX_SHELL=zsh\n"
      "LINEAI_TERMUX_RC=/data/data/com.termux/files/home/.zshrc\n"
      "LINEAI_TERMUX_HOST=127.0.0.1\n"
      "LINEAI_TERMUX_PORT=8022\n"
      "LINEAI_TERMUX_TEST_EXIT=0\n"
      "LINEAI_TERMUX_TEST_BEGIN\nLineAI SSH OK\nu0_a123\n/home\n"
      "LINEAI_TERMUX_TEST_END\nLINEAI_PRIVATE_KEY_BEGIN\n"} +
      kPrivateKey + "\nLINEAI_PRIVATE_KEY_END\n";

  const auto result = linecode::domain::ParseTermuxSetupOutput(output);
  assert(result.has_value());
  assert(result->config.host == "127.0.0.1");
  assert(result->config.port == 8022);
  assert(result->config.username == "u0_a123");
  assert(result->config.private_key == kPrivateKey);
  assert(result->shell == "zsh");
  assert(result->rc_path.ends_with("/.zshrc"));
  assert(result->verification_output.starts_with("LineAI SSH OK"));
  assert(result->ConnectionVerified());
}

void NormalizesMissingHostAndInvalidPort() {
  const std::string output =
      "LINEAI_TERMUX_USERNAME=user\n"
      "LINEAI_TERMUX_PORT=not-a-port\n"
      "LINEAI_TERMUX_TEST_EXIT=255\n"
      "LINEAI_PRIVATE_KEY_BEGIN\nkey\nLINEAI_PRIVATE_KEY_END\n";

  const auto result = linecode::domain::ParseTermuxSetupOutput(output);
  assert(result.has_value());
  assert(result->config.host == linecode::domain::kDefaultSshHost);
  assert(result->config.port == linecode::domain::kDefaultSshPort);
  assert(!result->ConnectionVerified());
}

void RejectsAndRedactsMalformedOutput() {
  const std::string output =
      "LINEAI_TERMUX_USERNAME=user\n"
      "LINEAI_PRIVATE_KEY_BEGIN\nnever-log-this-key\n";

  const auto result = linecode::domain::ParseTermuxSetupOutput(output);
  assert(!result.has_value());
  assert(result.error().code == linecode::domain::TermuxErrorCode::parse_failed);
  assert(!result.error().detail.contains("never-log-this-key"));
  assert(result.error().detail.contains("saved to SSH Private key"));
}

void RedactsEveryPrivateKeyBlock() {
  const std::string output =
      "before\nLINEAI_PRIVATE_KEY_BEGIN\nfirst\nLINEAI_PRIVATE_KEY_END\n"
      "middle\nLINEAI_PRIVATE_KEY_BEGIN\nsecond\nLINEAI_PRIVATE_KEY_END\nafter";
  const auto redacted =
      linecode::domain::RedactTermuxPrivateKey(output, "[redacted]");
  assert(redacted == "before\n[redacted]\nmiddle\n[redacted]\nafter");
}

void SetupContractContainsRequiredTermuxOperations() {
  using namespace linecode::application;
  assert(kTermuxAllowExternalAppsCommand.contains(
      "allow-external-apps=true"));
  assert(kTermuxAllowExternalAppsCommand.contains("termux-reload-settings"));
  assert(kTermuxOpenSshSetupScript.contains("pkg install -y openssh"));
  assert(kTermuxOpenSshSetupScript.contains("ssh-keygen -t rsa -b 4096"));
  assert(kTermuxOpenSshSetupScript.contains("LINEAI_TERMUX_TEST_EXIT"));
  assert(kTermuxOpenSshSetupScript.contains("LINEAI_PRIVATE_KEY_BEGIN"));
  assert(kTermuxOpenSshSetupScript.contains("-p 8022"));
}

} // namespace

int main() {
  ParsesCompleteSetupOutput();
  NormalizesMissingHostAndInvalidPort();
  RejectsAndRedactsMalformedOutput();
  RedactsEveryPrivateKeyBlock();
  SetupContractContainsRequiredTermuxOperations();
}
