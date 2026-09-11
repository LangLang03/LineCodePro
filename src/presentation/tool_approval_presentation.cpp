#include "presentation/tool_approval_presentation.h"

#include <algorithm>
#include <array>
#include <functional>
#include <numeric>
#include <ranges>
#include <string_view>

#include "infrastructure/archive_json.h"

namespace linecode::presentation {
namespace {

namespace json = infrastructure::archive_json;

std::string Trim(std::string value) {
  const auto first = std::ranges::find_if_not(value, [](unsigned char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r';
  });
  const auto last = std::ranges::find_if_not(value | std::views::reverse,
                                             [](unsigned char c) {
                                               return c == ' ' || c == '\t' ||
                                                      c == '\n' || c == '\r';
                                             })
                        .base();
  if (first >= last)
    return {};
  return std::string(first, last);
}

struct ApprovalArguments final {
  const json::Object *object{};
  std::string raw;

  [[nodiscard]] std::string String(std::string_view key) const {
    if (object == nullptr)
      return {};
    const auto *value = json::AsString(json::Find(*object, key));
    return value == nullptr ? std::string{} : *value;
  }

  [[nodiscard]] const json::Array *Array(std::string_view key) const {
    return object == nullptr ? nullptr
                             : json::AsArray(json::Find(*object, key));
  }
};

std::string Explanation(const ApprovalArguments &arguments) {
  if (arguments.object == nullptr)
    return {};
  if (json::Find(*arguments.object, "reason") != nullptr)
    return arguments.String("reason");
  return arguments.String("description");
}

std::string ShellAction(const ApprovalArguments &arguments) {
  const auto cwd = Trim(arguments.String("cwd"));
  const auto command = arguments.String("command");
  return cwd.empty() ? command : cwd + "\n" + command;
}

std::string GenericAction(const ApprovalArguments &arguments) {
  if (arguments.object != nullptr &&
      json::Find(*arguments.object, "file_path") != nullptr)
    return arguments.String("file_path");
  if (arguments.object != nullptr &&
      json::Find(*arguments.object, "path") != nullptr)
    return arguments.String("path");
  return arguments.raw;
}

std::string DeleteAction(const ApprovalArguments &arguments) {
  std::string action;
  if (const auto *paths = arguments.Array("paths"); paths != nullptr) {
    for (const auto &item : *paths) {
      const auto *path = json::AsString(&item);
      if (path == nullptr)
        continue;
      if (!action.empty())
        action += '\n';
      action += *path;
    }
    for (const auto field :
         {std::string_view{"file_path"}, std::string_view{"path"}}) {
      if (const auto path = arguments.String(field); !path.empty()) {
        action += '\n' + path;
      }
    }
    return action;
  }
  return GenericAction(arguments);
}

using ActionPresenter = std::string (*)(const ApprovalArguments &);

struct ToolPresentationPolicy final {
  std::string_view exact_name;
  ToolApprovalVisualKind visual;
  ActionPresenter present_action;
};

constexpr std::array kToolPresentationPolicies{
    ToolPresentationPolicy{"shell_execute", ToolApprovalVisualKind::terminal,
                           &ShellAction},
    ToolPresentationPolicy{"file_delete", ToolApprovalVisualKind::deletion,
                           &DeleteAction},
};

constexpr ToolPresentationPolicy kFallbackPolicy{
    {}, ToolApprovalVisualKind::generic, &GenericAction};

const ToolPresentationPolicy &PolicyFor(std::string_view tool_name) {
  const auto found = std::ranges::find(kToolPresentationPolicies, tool_name,
                                       &ToolPresentationPolicy::exact_name);
  return found == kToolPresentationPolicies.end() ? kFallbackPolicy : *found;
}

} // namespace

ToolApprovalPresentation
PresentToolApproval(const ToolApprovalViewState &state) {
  auto parsed = json::Parse(state.arguments);
  const auto *object = parsed ? json::AsObject(&*parsed) : nullptr;
  const ApprovalArguments arguments{.object = object, .raw = state.arguments};
  const auto &policy = PolicyFor(state.tool_name);

  auto explanation = Explanation(arguments);
  return ToolApprovalPresentation{
      .visual = policy.visual,
      .tool_title = state.tool_name,
      .explanation = Trim(std::move(explanation)),
      .action = std::invoke(policy.present_action, arguments),
      .show_allow_always = state.can_allow_permanently,
      .actions_enabled = !state.submitted,
  };
}

ToolApprovalActionsLayout
ChooseToolApprovalActionsLayout(float available_width,
                                std::span<const float> button_widths,
                                float spacing) noexcept {
  const float buttons_width =
      std::accumulate(button_widths.begin(), button_widths.end(), 0.0F);
  const auto gaps = button_widths.empty() ? 0U : button_widths.size() - 1U;
  return buttons_width + spacing * static_cast<float>(gaps) > available_width
             ? ToolApprovalActionsLayout::stacked
             : ToolApprovalActionsLayout::horizontal;
}

} // namespace linecode::presentation
