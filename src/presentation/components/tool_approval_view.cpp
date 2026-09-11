#include "presentation/components/tool_approval_view.h"

#include <algorithm>
#include <array>
#include <functional>
#include <numeric>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

constexpr float kActionSpacing = 6.0F;

TextStyle Label(float size, Color color) {
  return TextStyle{Font::System(size), color};
}

class LegacyApprovalActionsLayout final
    : public Layout<LegacyApprovalActionsLayout> {
public:
  using Layout::Layout;

  static LayoutResult Measure(LayoutContext &context, ViewNode &node,
                              Constraints constraints) {
    LayoutResult result;
    if (node.ChildCount() == 0)
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));

    std::vector<float> natural_widths;
    natural_widths.reserve(node.ChildCount());
    for (auto &child : node.Children()) {
      const auto size = context.Measure(child, constraints.Loose());
      natural_widths.push_back(size.width);
    }

    const float natural_width =
        std::accumulate(natural_widths.begin(), natural_widths.end(), 0.0F) +
        kActionSpacing * static_cast<float>(node.ChildCount() - 1U);
    const float available_width =
        constraints.HasBoundedWidth() ? constraints.max_width : natural_width;
    const auto layout = ChooseToolApprovalActionsLayout(
        available_width, natural_widths, kActionSpacing);

    if (layout == ToolApprovalActionsLayout::horizontal) {
      const float child_width = std::max(
          0.0F, (available_width -
                 kActionSpacing * static_cast<float>(node.ChildCount() - 1U)) /
                    static_cast<float>(node.ChildCount()));
      float x = 0.0F;
      float height = 0.0F;
      for (std::size_t index = 0; index < node.ChildCount(); ++index) {
        auto child_constraints = constraints.Loose().TightWidth(child_width);
        const auto size =
            context.Measure(node.ChildAt(index), child_constraints);
        result.Place(node.ChildAt(index), {x, 0.0F});
        x += child_width + kActionSpacing;
        height = std::max(height, size.height);
      }
      return result.SetSize(constraints.Constrain({available_width, height}));
    }

    const float stacked_width = constraints.HasBoundedWidth()
                                    ? constraints.max_width
                                    : *std::ranges::max_element(natural_widths);
    float y = 0.0F;
    for (std::size_t index = 0; index < node.ChildCount(); ++index) {
      auto child_constraints = constraints.Loose().TightWidth(stacked_width);
      const auto size = context.Measure(node.ChildAt(index), child_constraints);
      result.Place(node.ChildAt(index), {0.0F, y});
      y += size.height;
      if (index + 1U < node.ChildCount())
        y += kActionSpacing;
    }
    return result.SetSize(constraints.Constrain({stacked_width, y}));
  }
};

StringVariant TerminalTitle(const ToolApprovalPresentation &) {
  return app::strings::chat_approval_terminal;
}

StringVariant DeleteTitle(const ToolApprovalPresentation &) {
  return app::strings::chat_approval_delete;
}

StringVariant ToolNameTitle(const ToolApprovalPresentation &presentation) {
  return presentation.tool_title;
}

using TitlePresenter = StringVariant (*)(const ToolApprovalPresentation &);

struct ToolApprovalVisualPolicy final {
  ToolApprovalVisualKind kind;
  ImageResource icon;
  TitlePresenter present_title;
};

const std::array kVisualPolicies{
    ToolApprovalVisualPolicy{ToolApprovalVisualKind::terminal,
                             app::images::terminal, &TerminalTitle},
    ToolApprovalVisualPolicy{ToolApprovalVisualKind::deletion,
                             app::images::wrench, &DeleteTitle},
    ToolApprovalVisualPolicy{ToolApprovalVisualKind::generic,
                             app::images::wrench, &ToolNameTitle},
};

const ToolApprovalVisualPolicy &VisualPolicyFor(ToolApprovalVisualKind kind) {
  const auto found =
      std::ranges::find(kVisualPolicies, kind, &ToolApprovalVisualPolicy::kind);
  return found == kVisualPolicies.end() ? kVisualPolicies.back() : *found;
}

void Submit(const std::function<void(std::string)> &callback,
            std::string tool_call_id) {
  if (callback)
    std::invoke(callback, std::move(tool_call_id));
}

View ApprovalAction(StringVariant label, bool primary, bool enabled,
                    std::string tool_call_id,
                    std::function<void(std::string)> callback) {
  const auto accessible_label = label;
  return Stack{
      Text(std::move(label))
          .Style(Label(13.0F, primary ? colors::text_on_color : colors::text))
          .Align(TextAlign::Center)
          .VerticalAlign(TextVerticalAlign::Center),
  }
      .OnClick([enabled, tool_call_id = std::move(tool_call_id),
                callback = std::move(callback)]() mutable {
        if (enabled)
          Submit(callback, std::move(tool_call_id));
      })
      .With(Frame{.min_height = 44.0F}, Padding(EdgeInsets::All(8.0F)),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(primary ? colors::accent : colors::background),
            Border(primary ? static_cast<Color>(colors::accent)
                           : static_cast<Color>(colors::border),
                   1.0F),
            CornerRadius(18.0F), Enabled{enabled}, Focusable(),
            PointerCursor(PointerCursorKind::Hand),
            Semantics{.role = SemanticRole::Button, .label = accessible_label});
}

View ApprovalActions(const ToolApprovalViewState &state,
                     const ToolApprovalPresentation &presentation,
                     const ToolApprovalCallbacks &callbacks) {
  std::vector<View> actions;
  actions.reserve(presentation.show_allow_always ? 3U : 2U);
  actions.push_back(ApprovalAction(app::strings::chat_approval_deny, false,
                                   presentation.actions_enabled,
                                   state.tool_call_id, callbacks.on_reject));
  actions.push_back(ApprovalAction(app::strings::chat_approval_allow_once, true,
                                   presentation.actions_enabled,
                                   state.tool_call_id,
                                   callbacks.on_allow_once));
  if (presentation.show_allow_always) {
    actions.push_back(ApprovalAction(app::strings::chat_approval_allow_always,
                                     false, presentation.actions_enabled,
                                     state.tool_call_id,
                                     callbacks.on_allow_always)
                          .With(Tooltip(app::strings::chat_approval_scope)));
  }
  return LegacyApprovalActionsLayout(std::move(actions))
      .With(Padding(EdgeInsets{.top = 10.0F}));
}

View ApprovalDetails(const ToolApprovalPresentation &presentation) {
  const StringVariant explanation =
      presentation.explanation.empty()
          ? StringVariant{app::strings::chat_approval_reason}
          : StringVariant{presentation.explanation};
  return ScrollView(Column{
                        Text(explanation)
                            .Style(Label(15.0F, colors::text))
                            .With(Padding(EdgeInsets{.top = 6.0F}),
                                  Semantics{.live_region =
                                                SemanticLiveRegion::Polite}),
                        ScrollView(SelectionArea(Text(presentation.action)
                                                     .Style(TextStyle{
                                                         Font::Monospace(13.0F),
                                                         colors::secondary})))
                            .ScrollAxis(Axis::Horizontal)
                            .With(Padding(EdgeInsets{.top = 16.0F,
                                                     .right = 0.0F,
                                                     .bottom = 10.0F,
                                                     .left = 0.0F})),
                    }
                        .With(CrossAlign(CrossAxisAlignment::Stretch),
                              Padding(EdgeInsets{.right = 4.0F})))
      .ScrollAxis(Axis::Vertical)
      .With(Frame{.max_height = 156.0F});
}

} // namespace

View ToolApprovalView(const ToolApprovalViewState &state,
                      ToolApprovalCallbacks callbacks) {
  const auto presentation = PresentToolApproval(state);
  const auto &visual = VisualPolicyFor(presentation.visual);

  return Column{
      Column{
          Row{
              Stack{Image(visual.icon)
                        .Tint(colors::secondary)
                        .With(Frame{.width = 16.0F, .height = 16.0F})}
                  .With(Frame{.width = 24.0F, .height = 28.0F},
                        Align(HorizontalAlignment::Center,
                              VerticalAlignment::Center)),
              Text(std::invoke(visual.present_title, presentation))
                  .Style(Label(12.0F, colors::secondary)),
          }
              .With(Spacing(4.0F), CrossAlign(CrossAxisAlignment::Center)),
          ApprovalDetails(presentation),
          ApprovalActions(state, presentation, callbacks),
      }
          .With(
              Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
              CrossAlign(CrossAxisAlignment::Stretch),
              Background(colors::background), Border(colors::border, 1.0F),
              CornerRadius(20.0F),
              Shadow{.color = Color::Rgb(0, 0, 0, 0.18F), .blur_radius = 2.0F}),
  }
      .With(Padding(EdgeInsets{
                .top = 10.0F, .right = 16.0F, .bottom = 16.0F, .left = 16.0F}),
            CrossAlign(CrossAxisAlignment::Stretch));
}

} // namespace linecode::presentation
