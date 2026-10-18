#include "presentation/screens/llm_settings_screen.h"

#include <array>
#include <functional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "application/behavior_settings_repository.h"
#include "domain/app_state.h"
#include "presentation/components/behavior_settings_state.h"
#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_settings_card_frame.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/legacy_text_presentation.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {
using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon)).Tint(tint).With(Frame{.width = size, .height = size});
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::screen_llm_title).Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(), Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Stack{}.With(Frame{.width = 36.0F, .height = 36.0F}),
  }.With(Frame{.min_height = 60.0F}, Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
         Background(colors::background));
}

View OptionRow(ImageResource icon, StringResource title, StringResource description,
               bool active, std::function<void()> changed) {
  return Row{
      Glyph(std::move(icon), 20.0F, active ? colors::accent : colors::secondary),
      Column{
          Text(title).Style(Label(16.0F, active ? FontWeight::Medium : FontWeight::Regular,
                                  active ? colors::accent : colors::text)),
          Text(description).Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
      }.With(Spacing(2.0F), Grow()),
  }.OnClick(std::move(changed))
      // OptionRowView's 16sp title, 11sp description, 2dp gap and vertical
      // padding resolve to 64.75dp on the legacy Android layout. 56dp was
      // only the Java minimum and let HuxerUI's tighter font metrics shrink
      // every row by about 5 physical pixels.
      .With(Frame{.min_height = 64.75F}, Spacing(12.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Background(active ? colors::accent_muted : Color::Transparent()),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View SwitchRow(ImageResource icon, StringResource title, StringResource description,
               bool checked, std::function<void(bool)> changed,
               float minimum_height = 0.0F) {
  auto row_changed = changed;
  return Row{
      Glyph(std::move(icon), 20.0F, colors::secondary),
      Column{Text(title).Style(Label(16.0F, FontWeight::Medium)),
             Text(description).Style(Label(11.0F, FontWeight::Regular, colors::tertiary))}
          .With(Spacing(2.0F), Grow()),
      LegacySwitch(checked, std::move(changed)),
  }.OnClick([checked, changed = std::move(row_changed)] { changed(!checked); })
      .With(Frame{.min_height = minimum_height}, Spacing(12.0F),
            Padding(EdgeInsets::All(16.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

View Section(std::string title, std::vector<View> rows) {
  std::vector<View> children;
  children.reserve(rows.size() * 2);
  for (std::size_t index = 0; index < rows.size(); ++index) {
    children.push_back(std::move(rows[index]));
    if (index + 1 < rows.size()) children.push_back(Divider());
  }
  return Column{
      Text(std::move(title))
          .Style(Label(11.0F, FontWeight::Medium, colors::tertiary))
          .With(Frame{.height = 47.625F},
                Padding(EdgeInsets{.top = 20.0F,
                                   .right = 16.0F,
                                   .bottom = 12.0F,
                                   .left = 16.0F})),
      LegacySettingsCardFrame{Column(std::move(children))
          .With(CornerRadius(12.0F), Background(colors::elevated),
                CrossAlign(CrossAxisAlignment::Stretch))},
  }.With(CrossAlign(CrossAxisAlignment::Stretch));
}

struct ReasoningMeta final {
  domain::ReasoningEffort value;
  StringResource title;
  StringResource description;
};

// Shown instead of the switches while the shared settings cell has no
// successfully loaded value. Rendering the controls with struct defaults here
// is what used to make this screen claim "minimal mode is off" while the store
// still had it on and every request kept running in minimal mode.
View StatusCard(StringResource title, std::string detail, bool can_retry,
                std::function<void()> retry, std::string retry_label) {
  std::vector<View> children;
  children.push_back(
      Text(title).Style(Label(15.0F, FontWeight::Medium))
          .With(Padding(EdgeInsets{.top = 14.0F,
                                   .right = 16.0F,
                                   .bottom = 8.0F,
                                   .left = 16.0F}),
                CrossAlign(CrossAxisAlignment::Stretch)));
  if (!detail.empty()) {
    children.push_back(
        Text(std::move(detail))
            .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(EdgeInsets{.right = 16.0F,
                                     .bottom = 14.0F,
                                     .left = 16.0F})));
  }
  View card = LegacySettingsCardFrame{Column(std::move(children))
      .With(CornerRadius(12.0F), Background(colors::elevated),
            CrossAlign(CrossAxisAlignment::Stretch))};
  if (!can_retry)
    return card;
  return Column{
      std::move(card),
      Text(std::move(retry_label))
          .Style(Label(15.0F, FontWeight::Medium, colors::accent))
          .OnClick([retry = std::move(retry)] {
            if (retry)
              std::invoke(retry);
          })
          .With(Frame{.min_height = 52.0F}, Padding(EdgeInsets::All(16.0F)),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
  }.With(CrossAlign(CrossAxisAlignment::Stretch));
}
} // namespace

[[huxerui::composable]] View LlmSettingsScreen(
    std::shared_ptr<application::AiBehaviorSettingsRepository> repository,
    huxerui::State<BehaviorSettingsState> behavior_state,
    std::function<void()> reload) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const auto current = behavior_state->value;
  const auto ready = behavior_state->loaded;
  const auto error = behavior_state->error;

  // Optimistic write with rollback: a switch may only show a value the store
  // accepted, and a newer edit always wins over an older failure.
  auto apply = [tasks, toast, behavior_state](auto operation, auto mutate) {
    domain::AiBehaviorSettings previous{};
    domain::AiBehaviorSettings optimistic{};
    behavior_state.Update([&](auto &state) {
      previous = state.value;
      optimistic = previous;
      mutate(optimistic);
      static_cast<void>(ApplyBehaviorEdit(state, optimistic));
    });
    tasks.Launch([operation = std::move(operation), toast, behavior_state,
                  previous, optimistic]() mutable -> Task<void> {
      auto result = co_await operation();
      if (result)
        co_return;
      const auto message = result.error().message;
      behavior_state.Update([&message, previous, optimistic](auto &state) {
        static_cast<void>(ApplyBehaviorPersistFailure(state, optimistic,
                                                      previous, message));
      });
      toast.Show(message);
    });
  };

  const std::array reasoning{
      ReasoningMeta{domain::ReasoningEffort::off, app::strings::screen_llm_thinking_off_label,
                    app::strings::screen_llm_thinking_off_desc},
      ReasoningMeta{domain::ReasoningEffort::automatic, app::strings::screen_llm_thinking_auto_label,
                    app::strings::screen_llm_thinking_auto},
      ReasoningMeta{domain::ReasoningEffort::low, app::strings::screen_llm_thinking_low_label,
                    app::strings::screen_llm_thinking_low},
      ReasoningMeta{domain::ReasoningEffort::medium, app::strings::screen_llm_thinking_medium_label,
                    app::strings::screen_llm_thinking_medium},
      ReasoningMeta{domain::ReasoningEffort::high, app::strings::screen_llm_thinking_high_label,
                    app::strings::screen_llm_thinking_high},
      ReasoningMeta{domain::ReasoningEffort::maximum, app::strings::screen_llm_thinking_max_label,
                    app::strings::screen_llm_thinking_max},
  };
  std::vector<View> reasoning_rows;
  for (const auto &item : reasoning) {
    reasoning_rows.push_back(OptionRow(
        app::images::sparkles, item.title, item.description,
        current.reasoning == item.value,
        [apply, repository, value = item.value] {
          apply([repository, value] { return repository->SetReasoning(value); },
                [value](auto &next) { next.reasoning = value; });
        }).Key(item.value));
  }

  std::vector<View> learning_rows;
  learning_rows.push_back(SwitchRow(
      app::images::zap, app::strings::screen_llm_minimal_label,
      app::strings::screen_llm_minimal_desc, current.minimal_mode,
      [apply, repository](bool value) {
        apply([repository, value] { return repository->SetMinimalMode(value); },
              [value](auto &next) { next.minimal_mode = value; });
      }, 89.5F));
  learning_rows.push_back(SwitchRow(
      app::images::brain, app::strings::screen_llm_learning_label,
      app::strings::screen_llm_learning_desc, current.learning_mode,
      [apply, repository](bool value) {
        apply([repository, value] { return repository->SetLearningMode(value); },
              [value](auto &next) { next.learning_mode = value; });
      }, 89.5F));
  learning_rows.push_back(SwitchRow(
      app::images::rotate_ccw, app::strings::screen_llm_soft_compact_label,
      app::strings::screen_llm_soft_compact_desc, current.soft_compaction,
      [apply, repository](bool value) {
        apply([repository, value] { return repository->SetSoftCompaction(value); },
              [value](auto &next) { next.soft_compaction = value; });
      }, 106.75F));

  std::vector<View> tone_rows;
  tone_rows.push_back(OptionRow(
      app::images::zap, app::strings::screen_llm_tone_coding,
      app::strings::screen_llm_tone_coding_desc,
      current.tone == domain::ToneMode::coding,
      [apply, repository] {
        apply([repository] { return repository->SetTone(domain::ToneMode::coding); },
              [](auto &next) { next.tone = domain::ToneMode::coding; });
      }));
  tone_rows.push_back(OptionRow(
      app::images::smile, app::strings::screen_llm_tone_chat,
      app::strings::screen_llm_tone_chat_desc,
      current.tone == domain::ToneMode::chat,
      [apply, repository] {
        apply([repository] { return repository->SetTone(domain::ToneMode::chat); },
              [](auto &next) { next.tone = domain::ToneMode::chat; });
      }));

  std::vector<View> prompt_rows;
  prompt_rows.push_back(OptionRow(
      app::images::file_pen_line, app::strings::screen_llm_prompts_label,
      app::strings::screen_llm_prompts_desc, false,
      [navigation] { navigation.Push(domain::AppRoute::prompt_templates); }));

  std::vector<View> display_rows;
  auto boolean_row = [&](ImageResource icon, StringResource title, StringResource description,
                         bool value, auto member, auto save) {
    display_rows.push_back(SwitchRow(std::move(icon), title, description, value,
        [apply, repository, member, save](bool next_value) {
          apply([repository, save, next_value] {
                  return (repository.get()->*save)(next_value);
                },
                [member, next_value](auto &next) { next.*member = next_value; });
        }));
  };
  boolean_row(app::images::scroll_text, app::strings::screen_llm_scroll_label,
              app::strings::screen_llm_scroll_desc, current.thinking_scroll,
              &domain::AiBehaviorSettings::thinking_scroll,
              &application::AiBehaviorSettingsRepository::SetThinkingScroll);
  boolean_row(app::images::expand, app::strings::screen_llm_auto_expand_label,
              app::strings::screen_llm_auto_expand_desc, current.thinking_auto_expand,
              &domain::AiBehaviorSettings::thinking_auto_expand,
              &application::AiBehaviorSettingsRepository::SetThinkingAutoExpand);
  boolean_row(app::images::brain, app::strings::screen_llm_keep_reasoning_label,
              app::strings::screen_llm_keep_reasoning_desc, current.preserve_reasoning,
              &domain::AiBehaviorSettings::preserve_reasoning,
              &application::AiBehaviorSettingsRepository::SetPreserveReasoning);

  if (!ready) {
    // No successfully loaded value: show why, plus a way to try again, instead
    // of a switch panel that would be inventing values.
    const auto can_retry = static_cast<bool>(reload);
    const auto retry_label = UseString(app::strings::screen_memory_retry);
    return Column{
        Header(navigation), LegacyScreenHeaderDivider(),
        ScrollView(Column{
            StatusCard(error.empty()
                           ? app::strings::screen_llm_settings_loading
                           : app::strings::screen_llm_settings_unavailable,
                       error, can_retry, std::move(reload),
                       std::string{retry_label}),
            Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}),
        }.With(CrossAlign(CrossAxisAlignment::Stretch),
               Background(colors::background)))
            .ScrollAxis(Axis::Vertical).With(Grow()),
    }.With(CrossAlign(CrossAxisAlignment::Stretch),
           Background(colors::background), SafeAreaPadding{});
  }

  return Column{
      Header(navigation), LegacyScreenHeaderDivider(),
      ScrollView(Column{
          Section(LegacySectionTitle(
                      UseString(app::strings::screen_llm_section_thinking)),
                  std::move(reasoning_rows)),
          Section(LegacySectionTitle(
                      UseString(app::strings::screen_llm_section_learning)),
                  std::move(learning_rows)),
          Section(LegacySectionTitle(
                      UseString(app::strings::screen_llm_section_tone)),
                  std::move(tone_rows)),
          Section(LegacySectionTitle(
                      UseString(app::strings::screen_llm_section_prompts)),
                  std::move(prompt_rows)),
          Section(LegacySectionTitle(UseString(
                      app::strings::screen_llm_section_thinking_display)),
                  std::move(display_rows)),
          Stack{}.With(Frame{.width = 1.0F, .height = 100.0F}),
      }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background)))
          .ScrollAxis(Axis::Vertical).With(Grow()),
  }.With(CrossAlign(CrossAxisAlignment::Stretch), Background(colors::background),
         SafeAreaPadding{});
}

} // namespace linecode::presentation
