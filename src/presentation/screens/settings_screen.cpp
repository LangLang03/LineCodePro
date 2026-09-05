#include "presentation/screens/settings_screen.h"

#include <algorithm>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/line_theme.h"
#include "presentation/platform_features.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

struct SettingsItem final {
  domain::AppRoute route;
  StringResource title;
  StringResource description;
  ImageResource icon;
};

/// Keeps setting cards inside the scroll viewport instead of relying on Padding's
/// outer-frame semantics. A vertical ScrollView supplies a tight cross-axis width;
/// measuring the card at that width minus 32 DIP preserves the original 16 DIP
/// gutter and makes both 12 DIP corners visible.
class SettingsCardFrame final : public Layout<SettingsCardFrame> {
public:
  using Layout::Layout;

  static LayoutResult Measure(LayoutContext& context, ViewNode& node, Constraints constraints) {
    constexpr float kHorizontalGutter = 16.0F;
    constexpr float kMaximumCardWidth = 760.0F;

    LayoutResult result;
    if (node.ChildCount() == 0) {
      return result.SetSize(constraints.Constrain({0.0F, 0.0F}));
    }

    auto child_constraints = constraints.Loose();
    if (constraints.HasBoundedWidth()) {
      const float available = std::max(0.0F, constraints.max_width - (kHorizontalGutter * 2.0F));
      const float card_width = std::min(kMaximumCardWidth, available);
      child_constraints.min_width = card_width;
      child_constraints.max_width = card_width;
    } else {
      child_constraints.max_width = kMaximumCardWidth;
    }

    ViewNode& card = node.ChildAt(0);
    const Size card_size = context.Measure(card, child_constraints);
    const float desired_width = card_size.width + (kHorizontalGutter * 2.0F);
    const float frame_width = constraints.HasBoundedWidth() ? constraints.max_width : desired_width;
    const Size frame_size = constraints.Constrain({frame_width, card_size.height});
    const float card_x = std::max(0.0F, (frame_size.width - card_size.width) * 0.5F);
    return result.Place(card, {card_x, 0.0F}).SetSize(frame_size);
  }
};

TextStyle LabelStyle(float size, FontWeight weight = FontWeight::Regular, Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon)).Tint(tint).With(Frame{.width = size, .height = size});
}

View IconDisc(ImageResource icon) {
  return Stack {
    Glyph(std::move(icon), 20.0F, colors::accent),
  }.With(
      Frame{.width = 36.0F, .height = 36.0F},
      Align(HorizontalAlignment::Center, VerticalAlignment::Center),
      Background(colors::accent_muted),
      CornerRadius(18.0F)
  );
}

View ScreenHeader(StringResource title, const RouteNavigationController<domain::AppRoute>& navigation) {
  return Row {
    Stack {
      Glyph(app::images::chevron_left, 22.0F, colors::text),
    }.OnClick([navigation] {
      navigation.Pop();
    }).With(
        Frame{.width = 36.0F, .height = 36.0F},
        Align(HorizontalAlignment::Center, VerticalAlignment::Center),
        Focusable(),
        PointerCursor(PointerCursorKind::Hand)
    ),
    Text(title).Style(LabelStyle(17.0F, FontWeight::Bold)).Align(TextAlign::Center).With(Grow()),
    Spacer().With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(
      Frame{.min_height = 60.0F},
      Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
      CrossAlign(CrossAxisAlignment::Center),
      Background(colors::background),
      Border(Color::Transparent(), 0.0F)
  );
}

View SettingsRow(SettingsItem item, const RouteNavigationController<domain::AppRoute>& navigation) {
  return Row {
    IconDisc(item.icon),
    Column {
      Text(item.title).Style(LabelStyle(16.0F, FontWeight::Medium)),
      Text(item.description).Style(LabelStyle(11.0F, FontWeight::Regular, colors::tertiary)),
    }.With(Spacing(2.0F), Grow()),
    Glyph(app::images::chevron_right, 17.0F, colors::tertiary)
        .With(Frame{.width = 20.0F, .height = 20.0F}),
  }.OnClick([navigation, next = item.route] {
    navigation.Push(next);
  }).With(
      Frame{.min_height = 68.0F},
      Spacing(12.0F),
      Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
      CrossAlign(CrossAxisAlignment::Center),
      Focusable(),
      PointerCursor(PointerCursorKind::Hand)
  );
}

void AppendSection(std::vector<View>& content, StringResource title, std::vector<SettingsItem> items,
                   const RouteNavigationController<domain::AppRoute>& navigation) {
  content.push_back(
      Text(title)
          .Style(LabelStyle(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 20.0F, .right = 16.0F, .bottom = 12.0F, .left = 16.0F}))
  );

  std::vector<View> rows;
  rows.reserve(items.size() * 2);
  std::size_t index = 0;
  for (const auto& item : items) {
    rows.push_back(SettingsRow(item, navigation).Key(item.route));
    if (++index < items.size()) {
      rows.push_back(Divider().With(Padding(EdgeInsets{.left = 68.0F})));
    }
  }
  content.push_back(SettingsCardFrame {
    Column(std::move(rows)).With(
        CornerRadius(12.0F),
        Background(colors::elevated),
        CrossAlign(CrossAxisAlignment::Stretch)
    ),
  });
}

StringResource RouteTitle(domain::AppRoute route) {
  switch (route) {
    case domain::AppRoute::tutorial: return app::strings::settings_row_tutorial_title;
    case domain::AppRoute::models: return app::strings::settings_row_models_title;
    case domain::AppRoute::llm: return app::strings::settings_row_llm_title;
    case domain::AppRoute::mcp: return app::strings::settings_row_mcp_title;
    case domain::AppRoute::tool_settings: return app::strings::settings_row_tool_settings_title;
    case domain::AppRoute::extensions: return app::strings::settings_row_extensions_title;
    case domain::AppRoute::input: return app::strings::settings_row_input_title;
    case domain::AppRoute::theme: return app::strings::settings_row_theme_title;
    case domain::AppRoute::output: return app::strings::settings_row_output_title;
    case domain::AppRoute::security: return app::strings::settings_row_security_title;
    case domain::AppRoute::storage: return app::strings::settings_row_storage_title;
    case domain::AppRoute::memory: return app::strings::settings_row_memory_title;
    case domain::AppRoute::data: return app::strings::settings_row_data_title;
    case domain::AppRoute::error_logs: return app::strings::settings_row_error_logs_title;
    case domain::AppRoute::keep_alive: return app::strings::settings_row_keep_alive_title;
    case domain::AppRoute::about: return app::strings::settings_row_about_title;
    case domain::AppRoute::settings: return app::strings::screen_settings_title;
  }
  return app::strings::screen_settings_title;
}

} // namespace

[[huxerui::composable]] View SettingsScreen() {
  using namespace huxerui;
  const auto navigation = UseNavigation<domain::AppRoute>();
  std::vector<View> content;
  content.reserve(24);

  content.push_back(SettingsRow({
      domain::AppRoute::tutorial,
      app::strings::settings_row_tutorial_title,
      app::strings::settings_row_tutorial_desc,
      app::images::sparkles,
  }, navigation));
  AppendSection(content, app::strings::screen_settings_section_ai, {
      {domain::AppRoute::models, app::strings::settings_row_models_title, app::strings::settings_row_models_desc,
       app::images::box},
      {domain::AppRoute::llm, app::strings::settings_row_llm_title, app::strings::settings_row_llm_desc,
       app::images::brain},
  }, navigation);
  AppendSection(content, app::strings::screen_settings_section_tools, {
      {domain::AppRoute::mcp, app::strings::settings_row_mcp_title, app::strings::settings_row_mcp_desc,
       app::images::mcp},
      {domain::AppRoute::tool_settings, app::strings::settings_row_tool_settings_title,
       app::strings::settings_row_tool_settings_desc, app::images::sliders_horizontal},
      {domain::AppRoute::extensions, app::strings::settings_row_extensions_title,
       app::strings::settings_row_extensions_desc, app::images::package},
  }, navigation);
  AppendSection(content, app::strings::screen_settings_section_ui, {
      {domain::AppRoute::input, app::strings::settings_row_input_title, app::strings::settings_row_input_desc,
       app::images::message_square_text},
      {domain::AppRoute::theme, app::strings::settings_row_theme_title, app::strings::settings_row_theme_desc,
       app::images::palette},
      {domain::AppRoute::output, app::strings::settings_row_output_title, app::strings::settings_row_output_desc,
       app::images::monitor},
  }, navigation);
  AppendSection(content, app::strings::screen_settings_section_security, {
      {domain::AppRoute::security, app::strings::settings_row_security_title,
       app::strings::settings_row_security_desc, app::images::shield_check},
  }, navigation);
  std::vector<SettingsItem> data_items {
      {domain::AppRoute::storage, app::strings::settings_row_storage_title, app::strings::settings_row_storage_desc,
       app::images::database},
      {domain::AppRoute::memory, app::strings::settings_row_memory_title, app::strings::settings_row_memory_desc,
       app::images::book_open},
      {domain::AppRoute::data, app::strings::settings_row_data_title, app::strings::settings_row_data_desc,
       app::images::archive},
      {domain::AppRoute::error_logs, app::strings::settings_row_error_logs_title,
       app::strings::settings_row_error_logs_desc, app::images::bug},
  };
  IfFeatureAvailable<PlatformFeature::keep_alive>([&] {
    data_items.push_back({
        domain::AppRoute::keep_alive,
        app::strings::settings_row_keep_alive_title,
        app::strings::settings_row_keep_alive_desc,
        app::images::battery_charging,
    });
  });
  AppendSection(content, app::strings::screen_settings_section_data, std::move(data_items), navigation);
  AppendSection(content, app::strings::screen_settings_section_info, {
      {domain::AppRoute::about, app::strings::settings_row_about_title, app::strings::settings_row_about_desc,
       app::images::cpu},
  }, navigation);
  content.push_back(Spacer().With(Frame{.height = 100.0F}));

  return Column {
    ScreenHeader(app::strings::screen_settings_title, navigation),
    Divider(),
    ScrollView(Column(std::move(content)).With(
        CrossAlign(CrossAxisAlignment::Stretch),
        Background(colors::background)
    )).ScrollAxis(Axis::Vertical).With(Grow(), ScrollBar()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background));
}

[[huxerui::composable]] View PendingScreen(domain::AppRoute current) {
  using namespace huxerui;
  const auto navigation = UseNavigation<domain::AppRoute>();
  return Column {
    ScreenHeader(RouteTitle(current), navigation),
    Divider(),
    Column {
      Text(RouteTitle(current)).Style(LabelStyle(20.0F, FontWeight::Bold)),
      Text(app::strings::migration_pending).Style(LabelStyle(13.0F, FontWeight::Regular, colors::tertiary)),
    }.With(Spacing(8.0F), Padding(20.0F), Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background));
}

} // namespace linecode::presentation
