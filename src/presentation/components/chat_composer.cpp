#include "presentation/components/chat_composer.h"

#include <algorithm>
#include <array>
#include <functional>
#include <ranges>
#include <span>
#include <utility>

#include <app_resources.h>

#include "application/behavior_settings_repository.h"
#include "application/chat_session.h"
#include "application/generation_controller.h"
#include "application/mcp_completion_loop.h"
#include "application/memory_context_service.h"
#include "application/ports/model_store.h"
#include "application/ports/todo_state_store.h"
#include "application/skill_repository.h"
#include "application/slash_command_catalog.h"
#include "application/token_usage_tracker.h"
#include "application/tool_review_coordinator.h"
#include "presentation/components/chat_generation_runner.h"
#include "presentation/line_theme.h"

namespace linecode::presentation::chat_composer {
namespace {

using namespace huxerui;

template <class... Visitors> struct Overloaded final : Visitors... {
  using Visitors::operator()...;
};

TextStyle ChatTextStyle(float size, FontWeight weight = FontWeight::Regular,
                        Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

struct ComposerActionVisual final {
  ImageResource icon;
  Color tint;
  Color background;
  float icon_size = 20.0F;
  float opacity = 1.0F;
};

enum class ComposerPrimaryActionState : std::size_t {
  send,
  stop,
};

struct ComposerPrimaryActionPresentation final {
  ImageResource icon;
  float icon_size;
};

const std::array kComposerPrimaryActionPresentations{
    ComposerPrimaryActionPresentation{
        .icon = app::images::arrow_up,
        .icon_size = 20.0F,
    },
    ComposerPrimaryActionPresentation{
        .icon = app::images::stop,
        .icon_size = 17.0F,
    },
};

static_assert(kComposerPrimaryActionPresentations.size() == 2);

const ComposerPrimaryActionPresentation &
PrimaryActionPresentationFor(ComposerPrimaryActionState state) {
  return kComposerPrimaryActionPresentations[std::to_underlying(state)];
}

View ComposerAction(ComposerActionVisual visual, bool enabled,
                    std::function<void()> action) {
  return Stack{
      Image(std::move(visual.icon))
          .Tint(visual.tint)
          .With(Frame{.width = visual.icon_size, .height = visual.icon_size}),
  }
      .OnClick(std::move(action))
      .With(Frame{.width = 44.0F, .height = 44.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(visual.background), CornerRadius(22.0F),
            Opacity(visual.opacity), Enabled{enabled}, Focusable(enabled),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

View ComposerImagePreview(State<std::optional<ImageSelection>> selection) {
  if (!selection.Get())
    return Stack{}.With(Frame{.height = 0.0F});

  const auto &image = *selection.Get();
  const StringVariant label = image.message.name.empty()
                                  ? StringVariant{"image"}
                                  : StringVariant{image.message.name};
  return Column{
      Row{
          Image(image.preview)
              .Fit(ImageFit::Cover)
              .With(Frame{.width = 56.0F, .height = 56.0F},
                    Background(colors::surface_light), ClipChildren()),
          Text(label)
              .Style(
                  ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary))
              .With(Frame{.max_width = 220.0F, .max_height = 20.0F}, Grow(),
                    ClipChildren()),
          Stack{Image(app::images::x)
                    .Tint(colors::tertiary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .OnClick([selection] { selection = std::nullopt; })
              .With(
                  Frame{.width = 28.0F, .height = 28.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
      }
          .With(Padding(8.0F), Spacing(8.0F),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::elevated),
                Border(colors::border_light, 1.0F), CornerRadius(14.0F),
                ClipChildren()),
  }
      .With(Padding(EdgeInsets{.bottom = 8.0F}));
}

bool HasVisibleText(const std::string &text) {
  return std::ranges::any_of(
      text, [](unsigned char value) { return std::isspace(value) == 0; });
}

View PendingMessages(
    const std::shared_ptr<application::PendingMessageQueue> &queue,
    State<std::size_t> revision) {
  static_cast<void>(revision.Get());
  constexpr std::size_t kVisibleLimit = 4;
  std::vector<View> rows;
  const auto items = queue->Items();
  const auto visible = std::min(items.size(), kVisibleLimit);
  rows.reserve(visible + (items.size() > visible ? 1U : 0U));
  for (std::size_t index = 0; index < visible; ++index) {
    std::string preview = items[index].text;
    if (preview.empty() && items[index].image)
      preview =
          items[index].image->name.empty() ? "image" : items[index].image->name;
    if (preview.size() > 30U)
      preview = preview.substr(0U, 30U) + "...";
    preview = std::to_string(index + 1U) + ". " + preview;
    rows.emplace_back(Row{
        Stack{}.With(Frame{.width = 3.0F, .height = 20.0F},
                     Background(colors::warning)),
        Text(std::move(preview))
            .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                 Color::Rgb(255, 170, 51)))
            .With(Grow(), ClipChildren()),
        Stack{Image(app::images::x)
                  .Tint(colors::tertiary)
                  .With(Frame{.width = 12.0F, .height = 12.0F})}
            .OnClick([queue, revision, index] {
              if (queue->Remove(index))
                revision += 1;
            })
            .With(Frame{.width = 20.0F, .height = 20.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
    }
                          .With(Padding(EdgeInsets{.top = 4.0F,
                                                   .right = 8.0F,
                                                   .bottom = 4.0F,
                                                   .left = 16.0F}),
                                Spacing(8.0F),
                                CrossAlign(CrossAxisAlignment::Center),
                                Background(colors::input))
                          .Key("pending:" + std::to_string(index)));
  }
  if (items.size() > visible) {
    rows.emplace_back(
        Text::Format(app::strings::common_more_queued, items.size() - visible)
            .Style(ChatTextStyle(12.0F, FontWeight::Regular,
                                 Color::Rgb(204, 136, 0)))
            .With(Padding(
                EdgeInsets{.top = 2.0F, .bottom = 4.0F, .left = 16.0F})));
  }
  if (rows.empty())
    return Stack{}.With(Frame{.height = 0.0F});
  return Column(std::move(rows)).With(CrossAlign(CrossAxisAlignment::Stretch));
}

std::string WithQuote(const std::optional<std::string> &quote,
                      std::string text) {
  if (!quote || quote->empty())
    return text;
  std::string quoted = "> ";
  for (const char character : *quote) {
    quoted += character;
    if (character == '\n')
      quoted += "> ";
  }
  quoted += "\n\n";
  quoted += text;
  return quoted;
}

View QuotePreview(State<std::optional<std::string>> quote) {
  if (!quote.Get())
    return Stack{}.With(Frame{.height = 0.0F});
  View panel =
      Row{
          Stack{}.With(Frame{.width = 3.0F}, Background(colors::accent),
                       CornerRadius(2.0F)),
          Text(*quote.Get())
              .Style(
                  ChatTextStyle(13.0F, FontWeight::Regular, colors::secondary))
              .With(Frame{.max_height = 36.0F}, Grow(), ClipChildren()),
          Stack{Image(app::images::x)
                    .Tint(colors::tertiary)
                    .With(Frame{.width = 16.0F, .height = 16.0F})}
              .OnClick([quote] { quote = std::nullopt; })
              .With(
                  Frame{.width = 28.0F, .height = 28.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
      }
          .With(Frame{.min_height = 44.0F}, Padding(8.0F), Spacing(8.0F),
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::elevated),
                Border(colors::border_light, 1.0F), CornerRadius(14.0F),
                ClipChildren());
  return Column{std::move(panel)}.With(Padding(EdgeInsets{.bottom = 8.0F}));
}

StringVariant SlashDescription(std::string_view token) {
  if (token == "/chat")
    return app::strings::slash_command_chat_desc;
  if (token == "/plan")
    return app::strings::slash_command_plan_desc;
  if (token == "/agent")
    return app::strings::slash_command_agent_desc;
  return app::strings::slash_command_model_desc;
}

bool SlashModeSelected(std::string_view token, domain::ChatMode mode) {
  return (token == "/chat" && mode == domain::ChatMode::chat) ||
         (token == "/plan" && mode == domain::ChatMode::plan) ||
         (token == "/agent" && mode == domain::ChatMode::agent);
}

StringVariant ReasoningDescription(domain::ReasoningEffort effort) {
  switch (effort) {
  case domain::ReasoningEffort::off:
    return app::strings::slash_command_reasoning_off_desc;
  case domain::ReasoningEffort::automatic:
    return std::string{domain::SerializeReasoningEffort(effort)};
  case domain::ReasoningEffort::low:
    return app::strings::slash_command_reasoning_low_desc;
  case domain::ReasoningEffort::medium:
    return app::strings::slash_command_reasoning_medium_desc;
  case domain::ReasoningEffort::high:
    return app::strings::slash_command_reasoning_high_desc;
  case domain::ReasoningEffort::maximum:
    return app::strings::slash_command_reasoning_max_desc;
  }
  std::unreachable();
}

const domain::ModelConfig *
FindSlashModel(std::span<const domain::ModelConfig> models,
               std::string_view id) {
  const auto found = std::ranges::find(models, id, &domain::ModelConfig::id);
  return found == models.end() ? nullptr : &*found;
}

std::string SlashModelName(const domain::ModelConfig &model) {
  if (!model.name.empty() && model.name != model.id)
    return model.name;
  if (!model.model_id.empty())
    return model.model_id;
  return model.provider_label;
}

std::string SlashModelDetail(const domain::ModelConfig &model) {
  const auto name = SlashModelName(model);
  if (!model.model_id.empty() && model.model_id != name)
    return model.provider_label + " · " + model.model_id;
  return model.provider_label;
}

struct SlashPopupRow final {
  std::string label;
  StringVariant description;
  bool selected{};
  std::string replacement;
};

[[huxerui::composable]] View
SlashSuggestionsPopup(PopupContext context,
                      application::SlashSuggestionState state,
                      std::vector<domain::ModelConfig> models,
                      std::string selected_model_id, domain::ChatMode chat_mode,
                      std::function<void(std::string)> select_replacement) {
  StringVariant title = app::strings::slash_command_main_title;
  std::vector<SlashPopupRow> popup_rows;
  std::visit(
      Overloaded{
          [&](application::MainSlashSuggestions main) {
            popup_rows.reserve(main.commands.size());
            for (auto &suggestion : main.commands) {
              popup_rows.push_back(SlashPopupRow{
                  .label = suggestion.token,
                  .description = SlashDescription(suggestion.token),
                  .selected = SlashModeSelected(suggestion.token, chat_mode),
                  .replacement = suggestion.token + " ",
              });
            }
          },
          [&](application::ModelSlashSuggestions model_state) {
            title = app::strings::slash_command_model_title;
            popup_rows.reserve(model_state.model_ids.size());
            for (auto &id : model_state.model_ids) {
              const auto *model = FindSlashModel(models, id);
              if (!model)
                continue;
              popup_rows.push_back(SlashPopupRow{
                  .label = SlashModelName(*model),
                  .description = SlashModelDetail(*model),
                  .selected = id == selected_model_id,
                  .replacement = "/model " + id + " ",
              });
            }
          },
          [&](application::ReasoningSlashSuggestions reasoning_state) {
            title = UseString(app::strings::slash_command_reasoning_title,
                              reasoning_state.model_id);
            popup_rows.reserve(reasoning_state.levels.size());
            for (const auto effort : reasoning_state.levels) {
              const auto name =
                  std::string{domain::SerializeReasoningEffort(effort)};
              popup_rows.push_back(SlashPopupRow{
                  .label = name,
                  .description = ReasoningDescription(effort),
                  .selected = false,
                  .replacement =
                      "/model " + reasoning_state.model_id + " " + name,
              });
            }
          },
      },
      std::move(state));

  std::vector<View> rows;
  rows.reserve(popup_rows.size() + 1U);
  rows.emplace_back(
      Text(title)
          .Style(ChatTextStyle(13.0F, FontWeight::Medium))
          .With(
              Frame{.height = 22.0F},
              Padding(EdgeInsets{.top = 1.0F, .right = 12.0F, .left = 12.0F})));
  for (auto &row : popup_rows) {
    const bool command_label = row.label.starts_with('/');
    rows.emplace_back(Column{
        Row{
            Text(row.label)
                .Style(ChatTextStyle(
                    13.0F,
                    command_label ? FontWeight::Bold : FontWeight::Medium,
                    command_label ? colors::accent : colors::text))
                .With(Grow(), ClipChildren()),
            row.selected
                ? Stack{}.With(Frame{.width = 7.0F, .height = 7.0F},
                               Background(colors::accent), CornerRadius(4.0F))
                : Stack{}.With(Frame{.width = 7.0F, .height = 7.0F}),
        }
            .With(CrossAlign(CrossAxisAlignment::Center)),
        Text(row.description)
            .Style(ChatTextStyle(11.0F, FontWeight::Regular, colors::tertiary))
            .With(Padding(EdgeInsets{.top = 1.0F}), ClipChildren()),
    }
                          .OnClick([context, select_replacement,
                                    replacement = row.replacement] {
                            if (!select_replacement) {
                              context.Dismiss();
                              return;
                            }
                            select_replacement(replacement);
                          })
                          .With(Frame{.min_height = 52.0F},
                                Padding(EdgeInsets::Symmetric(16.0F, 14.0F)),
                                CrossAlign(CrossAxisAlignment::Stretch),
                                Focusable(),
                                PointerCursor(PointerCursorKind::Hand))
                          .Key(row.replacement));
  }
  return ScrollView(Column(std::move(rows))
                        .With(CrossAlign(CrossAxisAlignment::Stretch),
                              Padding(EdgeInsets::All(8.0F))))
      .ScrollAxis(Axis::Vertical)
      .With(Frame{.width = 328.0F, .max_height = 300.0F},
            Background(colors::input), Border(colors::border_light, 1.0F),
            CornerRadius(14.0F), ClipChildren());
}

} // namespace

using namespace huxerui;

[[huxerui::composable]] View Composer(Services services, ViewState state,
                                      Actions actions) {
  auto session = std::move(services.session);
  auto generation = std::move(services.generation);
  auto model_store = std::move(services.model_store);
  auto completion_loop = std::move(services.completion_loop);
  auto memory_context = std::move(services.memory_context);
  auto behavior_settings = std::move(services.behavior_settings);
  auto todo_state = std::move(services.todo_state);
  auto skills = std::move(services.skills);
  auto tool_reviews = std::move(services.tool_reviews);
  auto pending_messages = std::move(services.pending_messages);
  auto compaction_service = std::move(services.compaction);
  auto draft = std::move(state.draft);
  const auto has_selected_model = state.has_selected_model;
  auto tasks = std::move(state.tasks);
  auto active_generation = std::move(state.active_generation);
  auto revision = std::move(state.revision);
  auto selected_attachments = std::move(state.attachments);
  auto selected_image = std::move(state.image);
  auto quote_text = std::move(state.quote);
  const auto chat_mode = state.chat_mode;
  auto slash_models = std::move(state.slash_models);
  auto slash_selected_model_id = std::move(state.selected_model_id);
  const auto input_settings = state.input_settings;
  auto current_project_id = std::move(state.current_project_id);
  auto prompt_context = std::move(state.prompt_context);
  const auto permission_mode = state.permission_mode;
  auto toast = std::move(state.toast);
  auto auto_compaction = std::move(state.auto_compaction);
  auto retry_labels = std::move(state.retry_labels);
  auto show_attachment_picker = std::move(actions.show_attachment_picker);
  auto show_image_picker = std::move(actions.show_image_picker);
  auto handle_slash_command = std::move(actions.handle_slash_command);
  // Composition-scoped so the token count survives the runner being rebuilt on
  // every recomposition.
  const auto token_usage =
      UseState(std::make_shared<application::TokenUsageTracker>()).Get();
  auto runner = MakeChatGenerationRunner(
      ChatGenerationDependencies{
          .session = session,
          .generation = generation,
          .model_store = model_store,
          .completion_loop = completion_loop,
          .memory_context = memory_context,
          .behavior_settings = behavior_settings,
          .todo_state = todo_state,
          .token_usage = token_usage,
          .skills = skills,
          .compaction = compaction_service,
          .auto_compaction = auto_compaction,
      },
      tool_reviews, pending_messages, tasks, active_generation, revision,
      std::move(current_project_id), std::move(prompt_context), permission_mode,
      toast, std::move(retry_labels));
  const auto slash_popup = UsePopup();
  auto slash_layer = UseState(std::optional<LayerId>{});

  const bool generating =
      generation->State().phase == application::GenerationPhase::running;
  const bool has_content = HasVisibleText(draft->text) ||
                           !selected_attachments->empty() ||
                           selected_image->has_value();

  const auto dismiss_slash = [slash_popup, slash_layer] {
    if (slash_layer.Get())
      slash_popup.Dismiss(*slash_layer.Get());
    slash_layer = std::nullopt;
  };
  using SlashPresenter = std::function<void(std::string_view)>;
  const auto slash_presenter = std::make_shared<SlashPresenter>();
  const std::weak_ptr<SlashPresenter> weak_slash_presenter = slash_presenter;
  *slash_presenter = [slash_popup, slash_layer, draft, chat_mode, slash_models,
                      slash_selected_model_id, generating,
                      weak_slash_presenter](std::string_view text) {
    auto state = generating ? std::optional<application::SlashSuggestionState>{}
                            : application::ResolveSlashSuggestionState(
                                  text, slash_models.Get());
    if (!state) {
      if (slash_layer.Get())
        slash_popup.Dismiss(*slash_layer.Get());
      slash_layer = std::nullopt;
      return;
    }
    auto factory = [state = std::move(*state), models = slash_models.Get(),
                    selected_model_id = slash_selected_model_id.Get(),
                    chat_mode, draft,
                    weak_slash_presenter](PopupContext context) mutable {
      return SlashSuggestionsPopup(
          context, std::move(state), std::move(models),
          std::move(selected_model_id), chat_mode,
          [draft, weak_slash_presenter](std::string replacement) {
            draft = TextEditingValue::FromText(replacement);
            if (const auto presenter = weak_slash_presenter.lock())
              (*presenter)(replacement);
          });
    };
    if (slash_layer.Get() && slash_popup.Update(*slash_layer.Get(), factory)) {
      return;
    }
    slash_layer = slash_popup.Show(
        std::move(factory),
        PopupOptions{
            .placement = {AnchorSide::Above, AnchorAlignment::Start},
            .gap = 8.0F,
            .viewport_margin = 16.0F,
            .offset = {},
            .dismiss_on_outside_press = true,
            .dismiss_on_cancel = true,
            .trap_focus = false,
            .retain_anchor_focus = true,
            .on_dismiss_request = {},
        });
  };

  auto send = [draft, selected_attachments, selected_image, pending_messages,
               runner, handle_slash_command = std::move(handle_slash_command),
               quote_text, generating, has_content, revision, dismiss_slash] {
    dismiss_slash();
    const auto submitted_text = WithQuote(quote_text.Get(), draft->text);
    if (generating) {
      if (!has_content) {
        runner->CancelAndContinue();
        return;
      }
      std::optional<domain::ChatImage> image;
      if (selected_image.Get())
        image = selected_image.Get()->message;
      auto queued = pending_messages->Enqueue(
          submitted_text, selected_attachments.Get(), std::move(image));
      if (!queued)
        return;
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      selected_image = std::nullopt;
      quote_text = std::nullopt;
      revision += 1;
      return;
    }
    if (!has_content)
      return;
    if (handle_slash_command && handle_slash_command(submitted_text)) {
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      selected_image = std::nullopt;
      quote_text = std::nullopt;
      revision += 1;
      return;
    }
    std::optional<domain::ChatImage> image;
    if (selected_image.Get())
      image = selected_image.Get()->message;
    if (runner->Start(application::PendingMessage{
            .text = submitted_text,
            .attachments = selected_attachments.Get(),
            .image = std::move(image),
        })) {
      draft = TextEditingValue::FromText("");
      selected_attachments = std::vector<domain::InputAttachment>{};
      selected_image = std::nullopt;
      quote_text = std::nullopt;
    }
  };

  const bool can_send = generating || has_content;
  const auto primary_action_state = generating && !has_content
                                        ? ComposerPrimaryActionState::stop
                                        : ComposerPrimaryActionState::send;
  const auto &primary_action =
      PrimaryActionPresentationFor(primary_action_state);

  std::vector<View> attachment_chips;
  attachment_chips.reserve(selected_attachments->size());
  for (const auto &attachment : selected_attachments.Get()) {
    attachment_chips.emplace_back(
        Row{
            Text(attachment.Name())
                .Style(
                    ChatTextStyle(13.0F, FontWeight::Medium, colors::secondary))
                .With(Frame{.max_width = 170.0F, .max_height = 18.0F},
                      ClipChildren()),
            Stack{Image(app::images::x)
                      .Tint(colors::tertiary)
                      .With(Frame{.width = 12.0F, .height = 12.0F})}
                .OnClick([selected_attachments, path = attachment.Path(),
                          source = attachment.Source()] {
                  selected_attachments.Update(
                      [&](std::vector<domain::InputAttachment> &current) {
                        std::erase_if(current, [&](const auto &candidate) {
                          return candidate.Matches(path, source);
                        });
                      });
                })
                .With(Frame{.width = 18.0F, .height = 18.0F},
                      Align(HorizontalAlignment::Center,
                            VerticalAlignment::Center),
                      Focusable(), PointerCursor(PointerCursorKind::Hand)),
        }
            .With(Frame{.height = 34.0F},
                  Padding(EdgeInsets{.right = 8.0F, .left = 12.0F}),
                  Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center),
                  Background(colors::input), Border(colors::border, 1.0F),
                  CornerRadius(17.0F))
            .Key(attachment.Source() + ":" + attachment.Path()));
  }

  View attachment_row = Stack{}.With(Frame{.height = 0.0F});
  if (!attachment_chips.empty()) {
    attachment_row =
        ScrollView(Row(std::move(attachment_chips)).With(Spacing(8.0F)))
            .ScrollAxis(Axis::Horizontal)
            .With(Frame{.height = 42.0F}, Padding(EdgeInsets{.bottom = 8.0F}));
  }

  return Column{
      QuotePreview(quote_text),
      std::move(attachment_row),
      ComposerImagePreview(selected_image),
      Column{
          PendingMessages(pending_messages, revision),
          Row{
              ComposerAction(
                  ComposerActionVisual{
                      .icon = app::images::plus,
                      .tint = colors::secondary,
                      .background = Color::Transparent(),
                      .icon_size = 20.0F,
                      .opacity = generating ? 0.62F : 1.0F,
                  },
                  !generating, std::move(show_attachment_picker))
                  .On<LongPressEvents::Started>(
                      [show_image_picker = std::move(show_image_picker)](
                          const LongPressEvent &) { show_image_picker(); })
                  .With(LongPressGesture{}),
              TextField(draft)
                  .Placeholder(has_selected_model.value_or(true)
                                   ? app::strings::composer_hint_default
                                   : app::strings::composer_hint_no_model)
                  .Variant(TextFieldVariant::Standard)
                  .LineLimits(TextFieldLineLimits::MultiLine(1, 3))
                  .InputConfiguration(TextInputConfiguration{
                      .action = input_settings.enter_key ==
                                        domain::EnterKeyBehavior::send
                                    ? TextInputAction::Send
                                    : TextInputAction::Newline,
                      .multiline = true,
                  })
                  .VerticalAlign(TextVerticalAlign::Center)
                  .OnChanged(
                      [draft, slash_presenter](const TextEditingValue &value) {
                        draft = value;
                        (*slash_presenter)(value.text);
                      })
                  .OnSubmitted(send)
                  .With(Frame{.min_height = 44.0F}, Grow()),
              ComposerAction(
                  ComposerActionVisual{
                      .icon = primary_action.icon,
                      .tint =
                          can_send ? colors::text_on_color : colors::secondary,
                      .background =
                          can_send ? colors::accent : Color::Transparent(),
                      .icon_size = primary_action.icon_size,
                  },
                  can_send, send),
          } // Match the legacy 148px composer body at the 420dpi reference
            // density. A 56dp minimum rasterizes two pixels short here and
            // makes both the editor text and circular actions look low.
              .With(Frame{.min_height = 56.76F},
                    Padding(EdgeInsets::Symmetric(8.0F, 6.0F)),
                    CrossAlign(CrossAxisAlignment::End)),
      }
          .With(Frame{.min_height = 56.76F},
                CrossAlign(CrossAxisAlignment::Stretch),
                Background(colors::input), CornerRadius(20.0F)),
  }
      .With(Padding(EdgeInsets{
                .top = 14.0F, .right = 20.0F, .bottom = 20.0F, .left = 20.0F}),
            Background(colors::background), slash_popup.Anchor());
}

} // namespace linecode::presentation::chat_composer
