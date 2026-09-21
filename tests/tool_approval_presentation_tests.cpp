#include <array>
#include "gtest_support.h"
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

  EXPECT_EXPRESSION(view.visual == ToolApprovalVisualKind::terminal);
  EXPECT_EXPRESSION(view.tool_title == "shell_execute");
  EXPECT_EXPRESSION(view.explanation == "inspect build");
  EXPECT_EXPRESSION(view.action == "/work\nninja test");
  EXPECT_EXPRESSION(view.show_allow_always);
  EXPECT_EXPRESSION(view.actions_enabled);
}

void ExplanationUsesDescriptionOnlyWhenReasonIsAbsent() {
  const auto description = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-description",
      .tool_name = "file_write",
      .arguments = R"({"description":"explain write","path":"a.cpp"})",
  });
  EXPECT_EXPRESSION(description.explanation == "explain write");
  EXPECT_EXPRESSION(description.action == "a.cpp");

  const auto explicit_empty_path = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-empty-path",
      .tool_name = "file_write",
      .arguments = R"({"file_path":"","path":"must-not-be-used"})",
  });
  EXPECT_EXPRESSION(explicit_empty_path.action.empty());

  const auto empty_reason = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "write-empty-reason",
      .tool_name = "file_write",
      .arguments =
          R"({"reason":"","description":"must not replace reason","path":"b.cpp"})",
  });
  EXPECT_EXPRESSION(empty_reason.explanation.empty());
}

void DeletePresentationPreservesLegacyPathOrdering() {
  const auto view = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "delete-1",
      .tool_name = "file_delete",
      .arguments =
          R"({"paths":["one.cpp","two.cpp"],"file_path":"three.cpp","path":"four.cpp"})",
      .submitted = true,
  });

  EXPECT_EXPRESSION(view.visual == ToolApprovalVisualKind::deletion);
  EXPECT_EXPRESSION(view.action == "one.cpp\ntwo.cpp\nthree.cpp\nfour.cpp");
  EXPECT_EXPRESSION(!view.show_allow_always);
  EXPECT_EXPRESSION(!view.actions_enabled);
}

void GenericAndMalformedArgumentsRemainVisible() {
  const auto generic = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "generic-path",
      .tool_name = "mcpx_custom",
      .arguments = R"({"path":"src/main.cpp"})",
  });
  EXPECT_EXPRESSION(generic.visual == ToolApprovalVisualKind::generic);
  EXPECT_EXPRESSION(generic.tool_title == "mcpx_custom");
  EXPECT_EXPRESSION(generic.action == "src/main.cpp");

  const auto malformed = PresentToolApproval(ToolApprovalViewState{
      .tool_call_id = "generic-malformed",
      .tool_name = "mcpx_custom",
      .arguments = "not-json",
  });
  EXPECT_EXPRESSION(malformed.action == "not-json");
  EXPECT_EXPRESSION(malformed.explanation.empty());
}

void ActionLayoutUsesTheMeasuredLegacyThreshold() {
  constexpr std::array widths{74.0F, 91.0F, 103.0F};
  EXPECT_EXPRESSION(ChooseToolApprovalActionsLayout(280.0F, widths) ==
         ToolApprovalActionsLayout::horizontal);
  EXPECT_EXPRESSION(ChooseToolApprovalActionsLayout(279.9F, widths) ==
         ToolApprovalActionsLayout::stacked);

  constexpr std::array<float, 0> none{};
  EXPECT_EXPRESSION(ChooseToolApprovalActionsLayout(0.0F, none) ==
         ToolApprovalActionsLayout::horizontal);
}

} // namespace

TEST(tool_approval_presentation_tests, LegacySuite) {
  ShellPresentationMatchesLegacyFields();
  ExplanationUsesDescriptionOnlyWhenReasonIsAbsent();
  DeletePresentationPreservesLegacyPathOrdering();
  GenericAndMalformedArgumentsRemainVisible();
  ActionLayoutUsesTheMeasuredLegacyThreshold();
}
