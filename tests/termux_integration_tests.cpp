#include "gtest_support.h"
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
  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->config.host == "127.0.0.1");
  EXPECT_EXPRESSION(result->config.port == 8022);
  EXPECT_EXPRESSION(result->config.username == "u0_a123");
  EXPECT_EXPRESSION(result->config.private_key == kPrivateKey);
  EXPECT_EXPRESSION(result->shell == "zsh");
  EXPECT_EXPRESSION(result->rc_path.ends_with("/.zshrc"));
  EXPECT_EXPRESSION(result->verification_output.starts_with("LineAI SSH OK"));
  EXPECT_EXPRESSION(result->ConnectionVerified());
}

void NormalizesMissingHostAndInvalidPort() {
  const std::string output =
      "LINEAI_TERMUX_USERNAME=user\n"
      "LINEAI_TERMUX_PORT=not-a-port\n"
      "LINEAI_TERMUX_TEST_EXIT=255\n"
      "LINEAI_PRIVATE_KEY_BEGIN\nkey\nLINEAI_PRIVATE_KEY_END\n";

  const auto result = linecode::domain::ParseTermuxSetupOutput(output);
  EXPECT_EXPRESSION(result.has_value());
  EXPECT_EXPRESSION(result->config.host == linecode::domain::kDefaultSshHost);
  EXPECT_EXPRESSION(result->config.port == linecode::domain::kDefaultSshPort);
  EXPECT_EXPRESSION(!result->ConnectionVerified());
}

void RejectsAndRedactsMalformedOutput() {
  const std::string output =
      "LINEAI_TERMUX_USERNAME=user\n"
      "LINEAI_PRIVATE_KEY_BEGIN\nnever-log-this-key\n";

  const auto result = linecode::domain::ParseTermuxSetupOutput(output);
  EXPECT_EXPRESSION(!result.has_value());
  EXPECT_EXPRESSION(result.error().code == linecode::domain::TermuxErrorCode::parse_failed);
  EXPECT_EXPRESSION(!result.error().detail.contains("never-log-this-key"));
  EXPECT_EXPRESSION(result.error().detail.contains("saved to SSH Private key"));
}

void RedactsEveryPrivateKeyBlock() {
  const std::string output =
      "before\nLINEAI_PRIVATE_KEY_BEGIN\nfirst\nLINEAI_PRIVATE_KEY_END\n"
      "middle\nLINEAI_PRIVATE_KEY_BEGIN\nsecond\nLINEAI_PRIVATE_KEY_END\nafter";
  const auto redacted =
      linecode::domain::RedactTermuxPrivateKey(output, "[redacted]");
  EXPECT_EXPRESSION(redacted == "before\n[redacted]\nmiddle\n[redacted]\nafter");
}

void SetupContractContainsRequiredTermuxOperations() {
  using namespace linecode::application;
  EXPECT_EXPRESSION(kTermuxAllowExternalAppsCommand.contains(
      "allow-external-apps=true"));
  EXPECT_EXPRESSION(kTermuxAllowExternalAppsCommand.contains("termux-reload-settings"));
  EXPECT_EXPRESSION(kTermuxOpenSshSetupScript.contains("pkg install -y openssh"));
  EXPECT_EXPRESSION(kTermuxOpenSshSetupScript.contains("ssh-keygen -t rsa -b 4096"));
  EXPECT_EXPRESSION(kTermuxOpenSshSetupScript.contains("LINEAI_TERMUX_TEST_EXIT"));
  EXPECT_EXPRESSION(kTermuxOpenSshSetupScript.contains("LINEAI_PRIVATE_KEY_BEGIN"));
  EXPECT_EXPRESSION(kTermuxOpenSshSetupScript.contains("-p 8022"));
}

} // namespace

TEST(termux_integration_tests, LegacySuite) {
  ParsesCompleteSetupOutput();
  NormalizesMissingHostAndInvalidPort();
  RejectsAndRedactsMalformedOutput();
  RedactsEveryPrivateKeyBlock();
  SetupContractContainsRequiredTermuxOperations();
}
