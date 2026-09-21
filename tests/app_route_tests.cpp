#include "gtest_support.h"
#include <string>

#include "domain/app_state.h"

TEST(app_route_tests, LegacySuite) {
  using linecode::domain::AppRoute;

  const auto command = AppRoute::ShellCommand("printf 'linecode'");
  EXPECT_EXPRESSION(command.ShellCommandValue() != nullptr);
  EXPECT_EXPRESSION(command.ShellCommandValue()->command == "printf 'linecode'");
  EXPECT_EXPRESSION(command.BrowserValue() == nullptr);
  EXPECT_EXPRESSION(command.PageValue() == nullptr);
  EXPECT_EXPRESSION(command == AppRoute::ShellCommand("printf 'linecode'"));
  EXPECT_EXPRESSION(!(command == AppRoute::ShellCommand("pwd")));

  const auto browser = AppRoute::Browser("https://example.test", true, true);
  EXPECT_EXPRESSION(browser.BrowserValue() != nullptr);
  EXPECT_EXPRESSION(browser.BrowserValue()->java_script_enabled);
  EXPECT_EXPRESSION(browser.BrowserValue()->allow_any_http);
  EXPECT_EXPRESSION(browser.ShellCommandValue() == nullptr);

  const AppRoute tutorial = AppRoute::tutorial;
  const AppRoute prompts = AppRoute::prompt_templates;
  EXPECT_EXPRESSION(tutorial == AppRoute::tutorial);
  EXPECT_EXPRESSION(!(tutorial == AppRoute::prompt_templates));
  EXPECT_EXPRESSION(prompts == AppRoute::prompt_templates);

  const auto new_mcp = AppRoute::McpExtensionEditor();
  EXPECT_EXPRESSION(new_mcp.McpExtensionEditorValue() != nullptr);
  EXPECT_EXPRESSION(!new_mcp.McpExtensionEditorValue()->id.has_value());
  EXPECT_EXPRESSION(new_mcp.PageValue() == nullptr);

  const auto existing_mcp = AppRoute::McpExtensionEditor("mcp-id");
  EXPECT_EXPRESSION(existing_mcp.McpExtensionEditorValue() != nullptr);
  EXPECT_EXPRESSION(existing_mcp.McpExtensionEditorValue()->id == "mcp-id");
  EXPECT_EXPRESSION(existing_mcp == AppRoute::McpExtensionEditor("mcp-id"));
}
