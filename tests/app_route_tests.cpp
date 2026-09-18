#include <cassert>
#include <string>

#include "domain/app_state.h"

int main() {
  using linecode::domain::AppRoute;

  const auto command = AppRoute::ShellCommand("printf 'linecode'");
  assert(command.ShellCommandValue() != nullptr);
  assert(command.ShellCommandValue()->command == "printf 'linecode'");
  assert(command.BrowserValue() == nullptr);
  assert(command.PageValue() == nullptr);
  assert(command == AppRoute::ShellCommand("printf 'linecode'"));
  assert(!(command == AppRoute::ShellCommand("pwd")));

  const auto browser = AppRoute::Browser("https://example.test", true, true);
  assert(browser.BrowserValue() != nullptr);
  assert(browser.BrowserValue()->java_script_enabled);
  assert(browser.BrowserValue()->allow_any_http);
  assert(browser.ShellCommandValue() == nullptr);
}
