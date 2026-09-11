#include <array>
#include <cassert>
#include <string>

#include "presentation/tool_approval_presentation.h"

namespace {

using linecode::presentation::ChooseToolApprovalActionsLayout;
using linecode::presentation::PresentToolApproval;
using linecode::presentation::ToolApprovalActionsLayout;
using linecode::presentation::ToolApprovalViewState;
using linecode::presentation::ToolApprovalVisualKind;

void ShellPresentationMatchesLegacyFields() {
  const auto view = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "call-1",
      .tool_name = "shell_execute",
      .arguments =
          R"({"reason":"  inspect build  ","command":"ninja test","cwd":"  /work  "})",
      .can_allow_permanently = true,
  });

  assert(view.visual == ToolApprovalVisualKind::terminal);
  assert(view.tool_title == "shell_execute");
  assert(view.explanation == "inspect build");
  assert(view.action == "/work\nninja test");
  assert(view.show_allow_always);
  assert(view.actions_enabled);
}

void ExplanationUsesDescriptionOnlyWhenReasonIsAbsent() {
  const auto description = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-description",
      .tool_name = "file_write",
      .arguments = R"({"description":"explain write","path":"a.cpp"})",
  });
  assert(description.explanation == "explain write");
  assert(description.action == "a.cpp");

  const auto explicit_empty_path = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-empty-path",
      .tool_name = "file_write",
      .arguments = R"({"file_path":"","path":"must-not-be-used"})",
  });
  assert(explicit_empty_path.action.empty());

  const auto empty_reason = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-empty-reason",
      .tool_name = "file_write",
      .arguments =
          R"({"reason":"","description":"must not replace reason","path":"b.cpp"})",
  });
  assert(empty_reason.explanation.empty());
}

void DeletePresentationPreservesLegacyPathOrdering() {
  const auto view = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "delete-1",
      .tool_name = "file_delete",
      .arguments =
          R"({"paths":["one.cpp","two.cpp"],"file_path":"three.cpp","path":"four.cpp"})",
      .submitted = true,
  });

  assert(view.visual == ToolApprovalVisualKind::deletion);
  assert(view.action == "one.cpp\ntwo.cpp\nthree.cpp\nfour.cpp");
  assert(!view.show_allow_always);
  assert(!view.actions_enabled);
}

void GenericAndMalformedArgumentsRemainVisible() {
  const auto generic = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "generic-path",
      .tool_name = "mcpx_custom",
      .arguments = R"({"path":"src/main.cpp"})",
  });
  assert(generic.visual == ToolApprovalVisualKind::generic);
  assert(generic.tool_title == "mcpx_custom");
  assert(generic.action == "src/main.cpp");

  const auto malformed = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "generic-malformed",
      .tool_name = "mcpx_custom",
      .arguments = "not-json",
  });
  assert(malformed.action == "not-json");
  assert(malformed.explanation.empty());
}

void ActionLayoutUsesTheMeasuredLegacyThreshold() {
  constexpr std::array widths{74.0F, 91.0F, 103.0F};
  assert(ChooseToolApprovalActionsLayout(280.0F, widths) ==
         ToolApprovalActionsLayout::horizontal);
  assert(ChooseToolApprovalActionsLayout(279.9F, widths) ==
         ToolApprovalActionsLayout::stacked);

  constexpr std::array<float, 0> none{};
  assert(ChooseToolApprovalActionsLayout(0.0F, none) ==
         ToolApprovalActionsLayout::horizontal);
}

} // namespace

int main() {
  ShellPresentationMatchesLegacyFields();
  ExplanationUsesDescriptionOnlyWhenReasonIsAbsent();
  DeletePresentationPreservesLegacyPathOrdering();
  GenericAndMalformedArgumentsRemainVisible();
  ActionLayoutUsesTheMeasuredLegacyThreshold();
}
