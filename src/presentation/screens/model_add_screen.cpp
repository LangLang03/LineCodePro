#include "presentation/screens/model_add_screen.h"

#include <array>
#include <functional>
#include <numbers>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class CatalogTarget : std::uint8_t { primary, compression };

struct ModelFormState final {
  std::string id;
  TextEditingValue name;
  domain::ModelProtocol protocol{domain::ModelProtocol::openai_compatible};
  std::string provider_label;
  TextEditingValue base_url;
  TextEditingValue api_key;
  TextEditingValue model_id;
  TextEditingValue tool_limit;
  TextEditingValue context_size;
  TextEditingValue compression_id;
  bool compression_enabled{};
  bool compression_auto{true};
  bool compression_custom{};
  bool custom_model{true};
  bool local{};
  bool protocol_locked{};
  bool preset_mode{};
  bool busy{};
  bool attempted_save{};
  int acceleration{};
  std::vector<std::string> primary_catalog;
  std::vector<std::string> compression_catalog;
  std::string error;
};

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

View Glyph(ImageResource icon, float size, Color tint) {
  return Image(std::move(icon))
      .Tint(tint)
      .With(Frame{.width = size, .height = size});
}

ModelFormState MakeState(const application::ModelDraft &draft,
                         bool protocol_locked, bool preset_mode) {
  return ModelFormState{
      .id = draft.id,
      .name = TextEditingValue::FromText(draft.name),
      .protocol = draft.protocol,
      .provider_label = draft.provider_label,
      .base_url = TextEditingValue::FromText(draft.base_url),
      .api_key = TextEditingValue::FromText(draft.api_key),
      .model_id = TextEditingValue::FromText(draft.model_id),
      .tool_limit = TextEditingValue::FromText(draft.tool_call_limit),
      .context_size = TextEditingValue::FromText(draft.context_size),
      .compression_id = TextEditingValue::FromText(draft.compression_model_id),
      .compression_enabled = draft.compression_enabled,
      .compression_auto = draft.compression_auto,
      .compression_custom = !draft.compression_model_id.empty(),
      .custom_model = !draft.model_id.empty(),
      .local = draft.local,
      .protocol_locked = protocol_locked,
      .preset_mode = preset_mode,
  };
}

application::ModelDraft MakeDraft(const ModelFormState &state) {
  return application::ModelDraft{
      .id = state.id,
      .name = state.name.text,
      .protocol = state.protocol,
      .provider_label = state.provider_label,
      .base_url = state.base_url.text,
      .api_key = state.api_key.text,
      .model_id = state.model_id.text,
      .tool_call_limit = state.tool_limit.text,
      .compression_enabled = state.compression_enabled,
      .compression_auto = state.compression_auto,
      .compression_model_id = state.compression_id.text,
      .context_size = state.context_size.text,
      .local = state.local,
  };
}

template <typename Member>
auto ChangeText(State<ModelFormState> state, Member member) {
  return [state, member](const TextEditingValue &value) {
    auto next = state.Get();
    next.*member = value;
    next.error.clear();
    state = std::move(next);
  };
}

StringVariant ProtocolName(domain::ModelProtocol protocol) {
  switch (protocol) {
  case domain::ModelProtocol::openai_compatible:
    return app::strings::model_protocol_openai;
  case domain::ModelProtocol::codex_responses:
    return app::strings::model_protocol_codex;
  case domain::ModelProtocol::anthropic_messages:
    return app::strings::model_protocol_anthropic;
  case domain::ModelProtocol::local_gguf:
    return app::strings::model_protocol_local;
  }
  return app::strings::model_protocol_openai;
}

StringVariant ValidationMessage(application::ModelValidationCode code) {
  switch (code) {
  case application::ModelValidationCode::local_backend_unavailable:
    return app::strings::model_form_local_pending;
  case application::ModelValidationCode::missing_name_or_model_id:
    return app::strings::model_form_missing_id;
  case application::ModelValidationCode::missing_api_key:
    return app::strings::model_form_missing_key;
  case application::ModelValidationCode::invalid_tool_call_limit:
    return app::strings::model_form_invalid_tool_limit;
  case application::ModelValidationCode::missing_compression_model_id:
    return app::strings::model_form_missing_compression;
  }
  return app::strings::model_form_missing_id;
}

View HeaderAction(StringVariant label, bool enabled,
                  std::function<void()> action) {
  return Text(std::move(label))
      .Style(Label(16.0F, FontWeight::Medium,
                   enabled ? colors::accent : colors::tertiary))
      .OnClick([enabled, action = std::move(action)] {
        if (enabled && action) {
          std::invoke(action);
        }
      })
      .With(Frame{.min_width = 42.0F, .min_height = 36.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Enabled{enabled}, Opacity(enabled ? 1.0F : 0.45F), Focusable(),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

View SectionLabel(StringVariant text) {
  return Text(std::move(text))
      .Style(Label(14.0F, FontWeight::Medium, colors::secondary))
      .With(Padding(EdgeInsets{
          .top = 16.0F, .right = 0.0F, .bottom = 8.0F, .left = 0.0F}));
}

View SupportingText(StringVariant text, float top = 8.0F) {
  return Text(std::move(text))
      .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
      .With(Padding(EdgeInsets{.top = top}));
}

StringVariant BaseUrlPlaceholder(domain::ModelProtocol protocol) {
  switch (protocol) {
  case domain::ModelProtocol::codex_responses:
    return "https://api.example.com/codex";
  case domain::ModelProtocol::anthropic_messages:
    return "https://api.example.com/anthropic";
  case domain::ModelProtocol::openai_compatible:
  case domain::ModelProtocol::local_gguf:
    return "https://api.example.com/v1";
  }
  return "https://api.example.com/v1";
}

StringVariant BaseUrlHint(domain::ModelProtocol protocol) {
  switch (protocol) {
  case domain::ModelProtocol::codex_responses:
    return app::strings::model_form_base_url_hint_codex;
  case domain::ModelProtocol::anthropic_messages:
    return app::strings::model_form_base_url_hint_anthropic;
  case domain::ModelProtocol::openai_compatible:
  case domain::ModelProtocol::local_gguf:
    return app::strings::model_form_base_url_hint;
  }
  return app::strings::model_form_base_url_hint;
}

View FormField(TextEditingValue value, StringVariant label,
               StringVariant placeholder,
               std::function<void(const TextEditingValue &)> changed,
               ValidationResult validation = ValidationResult::None(),
               TextInputType input_type = TextInputType::Text,
               bool secure = false) {
  auto field = TextField(std::move(value))
                   .Label(std::move(label))
                   .Placeholder(std::move(placeholder))
                   .Variant(TextFieldVariant::Outlined)
                   .LineLimits(TextFieldLineLimits::SingleLine())
                   .InputConfiguration(TextInputConfiguration{
                       .type = input_type,
                       .capitalization = TextCapitalization::None,
                       .action = TextInputAction::Next,
                       .multiline = false,
                       .secure = secure,
                       .autocorrect = false,
                   })
                   .Validation(std::move(validation))
                   .OnChanged(std::move(changed))
                   .With(Frame{.min_height = 48.0F});
  return secure ? std::move(field).Secure() : std::move(field);
}

View ProtocolSelector(State<ModelFormState> state) {
  constexpr std::array protocols{
      domain::ModelProtocol::openai_compatible,
      domain::ModelProtocol::codex_responses,
      domain::ModelProtocol::anthropic_messages,
      domain::ModelProtocol::local_gguf,
  };
  std::vector<View> items;
  items.reserve(protocols.size());
  for (const auto protocol : protocols) {
    const bool selected = state->protocol == protocol;
    const bool enabled = !state->protocol_locked && !state->local &&
                         protocol != domain::ModelProtocol::local_gguf;
    items.push_back(
        Stack{
            Text(ProtocolName(protocol))
                .Style(Label(16.0F, FontWeight::Bold,
                             selected ? colors::text_on_color
                                      : colors::secondary))
                .Align(TextAlign::Center),
        }
            .OnClick([state, protocol, enabled] {
              if (!enabled)
                return;
              auto next = state.Get();
              next.protocol = protocol;
              next.provider_label =
                  std::string{domain::ModelProtocolLabel(protocol)};
              next.model_id = TextEditingValue::FromText("");
              next.compression_id = TextEditingValue::FromText("");
              next.compression_enabled = false;
              next.primary_catalog.clear();
              next.compression_catalog.clear();
              next.error.clear();
              state = std::move(next);
            })
            .With(Frame{.min_height = 46.0F}, Grow(),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(selected ? colors::accent : colors::surface_light),
                  CornerRadius(12.0F), Enabled{enabled || selected},
                  Opacity(enabled || selected ? 1.0F : 0.45F),
                  PointerCursor(enabled ? PointerCursorKind::Hand
                                        : PointerCursorKind::Default)));
  }
  return Row(std::move(items)).With(Spacing(8.0F));
}

View SwitchHeader(StringVariant label,
                  std::optional<StringVariant> switch_label, bool checked,
                  bool enabled, float top,
                  std::function<void(bool)> changed) {
  std::vector<View> trailing;
  if (switch_label.has_value()) {
    trailing.push_back(Text(std::move(*switch_label))
                           .Style(Label(13.0F, FontWeight::Medium,
                                        colors::secondary)));
  }
  trailing.push_back(Switch(checked)
                         .OnChanged(std::move(changed))
                         .With(Enabled{enabled},
                               Opacity(enabled ? 1.0F : 0.45F)));
  return Row{
      Text(std::move(label))
          .Style(Label(13.0F, FontWeight::Medium, colors::secondary))
          .With(Grow()),
      Row(std::move(trailing))
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
  }
      .With(Padding(EdgeInsets{.top = top, .bottom = 8.0F}),
            CrossAlign(CrossAxisAlignment::Center));
}

View SearchGlyph(Color tint) {
  return Canvas([tint](PaintContext &paint, Size) {
           constexpr float kPi = std::numbers::pi_v<float>;
           const StrokeStyle stroke{.width = 1.8F, .cap = StrokeCap::Round};
           paint.DrawArc(Point{6.5F, 6.5F}, 4.5F, 0.0F, 2.0F * kPi, tint,
                         stroke);
           paint.DrawLine(Point{9.75F, 9.75F}, Point{14.0F, 14.0F}, tint,
                          stroke);
         })
      .With(Frame{.width = 16.0F, .height = 16.0F});
}

View QueryButton(bool enabled, bool busy, std::function<void()> action) {
  const bool highlighted = enabled || busy;
  const Color tint = highlighted ? colors::text_on_color : colors::tertiary;
  return Row{
      SearchGlyph(tint),
      Text(busy ? StringVariant{app::strings::model_form_query_loading}
                : StringVariant{app::strings::model_form_query})
          .Style(Label(16.0F, FontWeight::Bold, tint)),
  }
      .OnClick([enabled, action = std::move(action)] {
        if (enabled && action)
          std::invoke(action);
      })
      .With(Frame{.height = 48.0F, .min_width = 76.0F}, Spacing(4.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(highlighted ? colors::accent : colors::surface_light),
            CornerRadius(12.0F), Enabled{enabled},
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

void SelectCatalogItem(State<ModelFormState> state, CatalogTarget target,
                       std::string model, bool custom) {
  auto next = state.Get();
  if (target == CatalogTarget::primary) {
    next.custom_model = custom;
    next.model_id = TextEditingValue::FromText(
        custom ? std::string{} : std::move(model));
    if (!custom && next.preset_mode && next.name.text.empty()) {
      next.name = TextEditingValue::FromText(next.model_id.text);
    }
  } else {
    next.compression_custom = custom;
    next.compression_id = TextEditingValue::FromText(
        custom ? std::string{} : std::move(model));
  }
  next.error.clear();
  state = std::move(next);
}

View PickerRow(BottomSheetContext sheet, StringVariant label, bool selected,
               bool custom, std::function<void()> choose) {
  return Row{
      Text(std::move(label))
          .Style(Label(16.0F, FontWeight::Regular,
                       custom ? colors::accent : colors::text))
          .With(Grow()),
      selected ? Glyph(app::images::check, 16.0F, colors::accent)
                     .With(Frame{.width = 18.0F, .height = 18.0F})
               : Spacer().With(Frame{.width = 0.0F, .height = 0.0F}),
  }
      .OnClick([sheet, choose = std::move(choose)] {
        sheet.Dismiss();
        if (choose)
          std::invoke(choose);
      })
      .With(Padding(EdgeInsets::Symmetric(16.0F, 14.0F)),
            CrossAlign(CrossAxisAlignment::Center), Focusable(),
            PointerCursor(PointerCursorKind::Hand));
}

void ShowModelPicker(const BottomSheetHandle &sheets,
                     State<ModelFormState> state, CatalogTarget target) {
  const auto models = target == CatalogTarget::primary
                          ? state->primary_catalog
                          : state->compression_catalog;
  const std::string selected = target == CatalogTarget::primary
                                   ? state->model_id.text
                                   : state->compression_id.text;
  sheets.Show([models, selected, state, target](BottomSheetContext sheet) {
    std::vector<View> rows;
    rows.reserve(models.size() + 1);
    for (const auto &model : models) {
      rows.push_back(PickerRow(
          sheet, model, model == selected, false,
          [state, target, model] {
            SelectCatalogItem(state, target, model, false);
          }));
    }
    rows.push_back(PickerRow(
        sheet, app::strings::model_form_custom_model_picker, false, true,
        [state, target] { SelectCatalogItem(state, target, {}, true); }));

    return Column{
        Row{Spacer(),
            Stack{}.With(Frame{.width = 36.0F, .height = 4.0F},
                         Background(colors::tertiary), CornerRadius(2.0F)),
            Spacer()}
            .With(Padding(EdgeInsets{.top = 8.0F, .bottom = 4.0F})),
        Text(app::strings::model_form_picker_title)
            .Style(Label(17.0F, FontWeight::Bold))
            .With(Padding(EdgeInsets{
                .right = 16.0F, .bottom = 12.0F, .left = 16.0F})),
        Stack{}.With(Frame{.height = 1.0F},
                     Background(colors::border_light)),
        ScrollView(Column(std::move(rows))
                       .With(CrossAlign(CrossAxisAlignment::Stretch)))
            .ScrollAxis(Axis::Vertical)
            .With(Frame{.max_height = 420.0F}, ScrollBar()),
        Spacer().With(Frame{.height = 12.0F}),
    }
        .With(Frame{.max_width = 560.0F}, Background(colors::elevated),
              CornerRadius(CornerRadii::Top(16.0F)), ClipChildren(),
              CrossAlign(CrossAxisAlignment::Stretch));
  });
}

Task<void> QueryModels(
    std::shared_ptr<application::ModelCatalogGateway> catalog,
    State<ModelFormState> state, CatalogTarget target,
    BottomSheetHandle sheets, ToastHandle toast) {
  auto next = state.Get();
  next.busy = true;
  next.error.clear();
  state = std::move(next);

  const auto draft = MakeDraft(state.Get());
  auto result = co_await catalog->Fetch(
      draft.protocol, application::ModelFormService::EffectiveBaseUrl(draft),
      draft.api_key);
  const bool has_models = result && !result->empty();
  next = state.Get();
  next.busy = false;
  if (!result) {
    next.error = result.error().message;
    toast.Show(result.error().message);
  } else if (result->empty()) {
    next.error.clear();
    toast.Show(app::strings::model_form_query_empty);
  } else if (target == CatalogTarget::primary) {
    next.primary_catalog = std::move(*result);
    next.custom_model = false;
  } else {
    next.compression_catalog = std::move(*result);
  }
  state = std::move(next);
  if (has_models)
    ShowModelPicker(sheets, state, target);
}

Task<void> ProbeModel(std::shared_ptr<application::ModelCatalogGateway> catalog,
                      State<ModelFormState> state, ToastHandle toast) {
  const auto built =
      application::ModelFormService::BuildForProbe(MakeDraft(state.Get()));
  if (!built) {
    toast.Show(ValidationMessage(built.error().code));
    co_return;
  }
  auto next = state.Get();
  next.busy = true;
  state = std::move(next);
  auto result = co_await catalog->Probe(*built);
  next = state.Get();
  next.busy = false;
  if (result) {
    next.error.clear();
    state = std::move(next);
    toast.Show(app::strings::model_form_test_ok);
  } else {
    next.error = result.error().message;
    state = std::move(next);
    toast.Show(result.error().message);
  }
}

Task<void> SaveModel(std::shared_ptr<application::ModelStore> store,
                     State<ModelFormState> state, ToastHandle toast,
                     ModelAddScreenActions actions) {
  const auto built =
      application::ModelFormService::Build(MakeDraft(state.Get()));
  if (!built) {
    toast.Show(ValidationMessage(built.error().code));
    co_return;
  }
  auto next = state.Get();
  next.busy = true;
  state = std::move(next);
  auto saved = co_await store->Save(*built);
  if (!saved) {
    next = state.Get();
    next.busy = false;
    next.error = saved.error().message;
    state = std::move(next);
    toast.Show(saved.error().message);
    co_return;
  }
  auto selected = co_await store->Select(saved->id);
  if (!selected) {
    next = state.Get();
    next.busy = false;
    next.error = selected.error().message;
    state = std::move(next);
    toast.Show(selected.error().message);
    co_return;
  }
  toast.Show(app::strings::model_form_saved);
  if (actions.on_saved) {
    std::invoke(actions.on_saved, std::move(*saved));
  }
}

View ModelSelector(TextEditingValue selection, bool enabled, bool busy,
                   std::function<void()> action) {
  const bool empty = selection.text.empty();
  auto selector_action = action;
  return Row{
      Row{
          Text(empty ? StringVariant{app::strings::model_form_select_model_first}
                     : StringVariant{selection.text})
              .Style(Label(16.0F, FontWeight::Regular,
                           empty ? colors::tertiary : colors::text))
              .With(Grow()),
          Glyph(app::images::chevron_down, 14.0F, colors::tertiary)
              .With(Frame{.width = 16.0F, .height = 16.0F}),
      }
          .OnClick([action = std::move(selector_action)] {
            if (action)
              std::invoke(action);
          })
          .With(Frame{.height = 48.0F}, Grow(),
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::surface_light),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
      QueryButton(enabled, busy, std::move(action)),
  }
      .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center));
}

View LocalForm(State<ModelFormState> state, ToastHandle toast) {
  const std::array<StringResource, 3> labels{
      app::strings::model_form_acceleration_auto,
      app::strings::model_form_acceleration_cpu,
      app::strings::model_form_acceleration_npu,
  };
  std::vector<View> acceleration;
  acceleration.reserve(labels.size());
  for (std::size_t index = 0; index < labels.size(); ++index) {
    const bool selected = state->acceleration == static_cast<int>(index);
    acceleration.push_back(
        Text(labels[index])
            .Style(Label(16.0F, FontWeight::Bold,
                         selected ? colors::text_on_color : colors::secondary))
            .Align(TextAlign::Center)
            .OnClick([state, index] {
              auto next = state.Get();
              next.acceleration = static_cast<int>(index);
              state = std::move(next);
            })
            .With(Frame{.height = 46.0F}, Grow(),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(selected ? colors::accent : colors::surface_light),
                  CornerRadius(12.0F), PointerCursor(PointerCursorKind::Hand)));
  }

  return Column{
      SectionLabel(app::strings::model_form_local_file),
      Row{
          Stack{Glyph(app::images::file_up, 20.0F, colors::accent)}.With(
              Frame{.width = 38.0F, .height = 38.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Background(colors::accent_muted), CornerRadius(8.0F)),
          Column{
              Text(app::strings::model_form_local_file_title)
                  .Style(Label(16.0F, FontWeight::Bold, colors::tertiary)),
              Text(app::strings::model_form_local_file_desc)
                  .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
          }
              .With(Spacing(3.0F), Grow()),
          Glyph(app::images::chevron_down, 14.0F, colors::tertiary)
              .With(Frame{.width = 16.0F, .height = 16.0F}),
      }
          .OnClick([toast] {
            toast.Show(app::strings::model_form_local_picker_pending);
          })
          .With(Frame{.min_height = 74.0F}, Spacing(12.0F),
                Padding(EdgeInsets::All(12.0F)),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::surface_light),
                Border{.color = colors::border_light, .width = 1.0F},
                CornerRadius(12.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
      SectionLabel(app::strings::model_form_local_context_size),
      FormField(state->context_size,
                app::strings::model_form_local_context_size,
                app::strings::model_form_local_context_placeholder,
                ChangeText(state, &ModelFormState::context_size),
                ValidationResult::None(), TextInputType::Text),
      SupportingText(app::strings::model_form_local_context_hint),
      SectionLabel(app::strings::model_form_acceleration),
      Row(std::move(acceleration)).With(Spacing(8.0F)),
      SupportingText(app::strings::model_form_acceleration_hint),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

} // namespace

[[huxerui::composable]] View
ModelAddScreen(ModelAddScreenOptions options,
               std::shared_ptr<application::ModelStore> store,
               std::shared_ptr<application::ModelCatalogGateway> catalog,
               ModelAddScreenActions actions) {
  const bool editing = options.editing.has_value();
  const auto initial_draft =
      editing
          ? application::ModelFormService::Edit(*options.editing)
          : application::ModelFormService::New(options.preset, options.local);
  auto state = UseState(MakeState(
      initial_draft, editing || options.preset.has_value() || options.local,
      options.preset.has_value()));
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const auto sheets = UseBottomSheet();

  const auto current_draft = MakeDraft(state.Get());
  const auto validation = application::ModelFormService::Build(current_draft);
  const bool can_save = !state->busy && validation.has_value();
  const bool can_query =
      !state->busy && application::ModelFormService::CanQuery(current_draft);

  auto save = [store, state, tasks, toast, actions] {
    auto next = state.Get();
    next.attempted_save = true;
    state = std::move(next);
    tasks.Launch([store, state, toast, actions]() -> Task<void> {
      co_await SaveModel(store, state, toast, actions);
    });
  };
  auto test = [catalog, state, tasks, toast] {
    tasks.Launch([catalog, state, toast]() -> Task<void> {
      co_await ProbeModel(catalog, state, toast);
    });
  };
  auto query_primary = [catalog, state, tasks, toast, sheets, can_query] {
    if (!state->primary_catalog.empty()) {
      ShowModelPicker(sheets, state, CatalogTarget::primary);
      return;
    }
    if (!can_query) {
      toast.Show(app::strings::model_form_query_requirements);
      return;
    }
    tasks.Launch([catalog, state, sheets, toast]() -> Task<void> {
      co_await QueryModels(catalog, state, CatalogTarget::primary, sheets, toast);
    });
  };
  auto query_compression = [catalog, state, tasks, toast, sheets, can_query] {
    if (!state->compression_catalog.empty()) {
      ShowModelPicker(sheets, state, CatalogTarget::compression);
      return;
    }
    if (!can_query) {
      toast.Show(app::strings::model_form_query_requirements);
      return;
    }
    tasks.Launch([catalog, state, sheets, toast]() -> Task<void> {
      co_await QueryModels(catalog, state, CatalogTarget::compression, sheets,
                           toast);
    });
  };

  std::vector<View> form;
  form.reserve(30);
  form.push_back(SectionLabel(
      state->protocol_locked
          ? StringVariant::Format(app::strings::model_form_provider_named,
                                  state->provider_label)
          : StringVariant{app::strings::model_form_provider}));
  form.push_back(ProtocolSelector(state));

  if (state->local) {
    form.push_back(SectionLabel(app::strings::model_form_name));
    form.push_back(FormField(state->name, app::strings::model_form_name,
                             app::strings::model_form_name_local_hint,
                             ChangeText(state, &ModelFormState::name)));
    form.push_back(LocalForm(state, toast));
  } else {
    form.push_back(SectionLabel(app::strings::model_form_name));
    form.push_back(FormField(
        state->name, app::strings::model_form_name,
        state->preset_mode
            ? StringVariant{app::strings::model_form_name_optional_hint}
            : StringVariant{app::strings::model_form_name_remote_hint},
        ChangeText(state, &ModelFormState::name)));

    form.push_back(SectionLabel(app::strings::model_form_base_url));
    form.push_back(FormField(
        state->base_url, app::strings::model_form_base_url,
        options.preset ? StringVariant{options.preset->placeholder}
                       : BaseUrlPlaceholder(state->protocol),
        ChangeText(state, &ModelFormState::base_url), ValidationResult::None(),
        TextInputType::Url));
    form.push_back(SupportingText(BaseUrlHint(state->protocol)));

    form.push_back(SectionLabel(app::strings::model_form_api_key));
    form.push_back(FormField(
        state->api_key, app::strings::model_form_api_key,
        app::strings::model_form_api_key_hint,
        ChangeText(state, &ModelFormState::api_key), ValidationResult::None(),
        TextInputType::Text, true));

    form.push_back(SwitchHeader(
        app::strings::model_form_model_id,
        StringVariant{app::strings::model_form_custom_model},
        state->custom_model, true, 16.0F, [state](bool value) {
          auto next = state.Get();
          next.custom_model = value;
          next.model_id = TextEditingValue::FromText("");
          state = std::move(next);
        }));
    if (state->custom_model) {
      form.push_back(FormField(
          state->model_id, app::strings::model_form_model_id,
          app::strings::model_form_model_id_hint,
          ChangeText(state, &ModelFormState::model_id)));
    } else {
      form.push_back(ModelSelector(state->model_id, can_query, state->busy,
                                   query_primary));
    }

    form.push_back(SectionLabel(app::strings::model_form_tool_limit));
    form.push_back(
        FormField(state->tool_limit, app::strings::model_form_tool_limit,
                  app::strings::model_form_tool_limit_placeholder,
                  ChangeText(state, &ModelFormState::tool_limit),
                  ValidationResult::None(),
                  TextInputType::Number));
    form.push_back(SupportingText(app::strings::model_form_tool_limit_hint));

    form.push_back(SectionLabel(app::strings::model_form_context_size));
    form.push_back(FormField(state->context_size,
                             app::strings::model_form_context_size,
                             app::strings::model_form_context_placeholder,
                             ChangeText(state, &ModelFormState::context_size)));
    form.push_back(SupportingText(app::strings::model_form_context_hint));

    if (domain::SupportsDedicatedCompression(state->protocol)) {
      form.push_back(SwitchHeader(
          app::strings::model_form_compression, std::nullopt,
          state->compression_enabled, true, 16.0F, [state](bool value) {
            auto next = state.Get();
            next.compression_enabled = value;
            state = std::move(next);
          }));
      if (state->compression_enabled) {
        form.push_back(SupportingText(
            app::strings::model_form_compression_hint, 0.0F));
        form.push_back(SwitchHeader(
            app::strings::model_form_compression_auto, std::nullopt,
            state->compression_auto, true, 12.0F, [state](bool value) {
              auto next = state.Get();
              next.compression_auto = value;
              state = std::move(next);
            }));
        form.push_back(SwitchHeader(
            app::strings::model_form_compression_id,
            StringVariant{app::strings::model_form_compression_custom},
            state->compression_custom, !state->compression_auto, 12.0F,
            [state](bool value) {
              auto next = state.Get();
              next.compression_custom = value;
              next.compression_id = TextEditingValue::FromText("");
              state = std::move(next);
            }));
        if (!state->compression_auto) {
          if (state->compression_custom) {
            form.push_back(FormField(
                state->compression_id,
                app::strings::model_form_compression_id,
                app::strings::model_form_compression_id_hint,
                ChangeText(state, &ModelFormState::compression_id)));
          } else {
            form.push_back(ModelSelector(state->compression_id, can_query,
                                         state->busy, query_compression));
          }
        }
      }
    }
  }

  if (!state->error.empty()) {
    form.push_back(Text(state->error)
                       .Style(Label(12.0F, FontWeight::Regular, colors::danger))
                       .With(Padding(EdgeInsets{.top = 12.0F})));
  }

  View screen = Column{
      LegacyScreenHeaderLayout{
          Stack{Glyph(app::images::chevron_left, 20.0F, colors::text)}
              .OnClick([callback = actions.on_back] {
                if (callback)
                  std::invoke(callback);
              })
              .With(
                  Frame{.width = 36.0F, .height = 36.0F},
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Focusable(), PointerCursor(PointerCursorKind::Hand)),
          Stack{Text(editing ? app::strings::model_form_edit_title
                             : app::strings::model_form_add_title)
                    .Style(Label(17.0F, FontWeight::Bold))}
              .With(Grow(), Align(HorizontalAlignment::Center,
                                  VerticalAlignment::Center)),
          Row{
              state->local
                  ? Spacer().With(Frame{.width = 0.0F, .height = 36.0F})
                  : HeaderAction(app::strings::model_form_test, !state->busy,
                                 std::move(test)),
              HeaderAction(app::strings::model_form_save, can_save,
                           std::move(save)),
          }
              .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
      }
          .With(Frame{.min_height = 60.0F},
                Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
                Background(colors::background)),
      Divider(),
      ScrollView(Column(std::move(form))
                     .With(Padding(EdgeInsets{.top = 16.0F,
                                              .right = 16.0F,
                                              .bottom = 16.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});

  auto text_field = UseEnvironment<TextFieldStyle>();
  text_field.variant = TextFieldVariant::Outlined;
  text_field.show_label = false;
  text_field.outlined.background = colors::surface_light;
  text_field.outlined.border = colors::border_light;
  text_field.outlined.hovered_border = colors::border_light;
  text_field.outlined.focused_border = colors::border_light;
  text_field.outlined.disabled_border = colors::border_light;
  text_field.outlined.minimum_height = 48.0F;
  text_field.text_style = Label(16.0F);
  text_field.placeholder_style = Label(16.0F, FontWeight::Regular,
                                       colors::tertiary);
  text_field.caret = colors::accent;
  text_field.border_width = 1.0F;
  text_field.focused_border_width = 1.0F;
  text_field.corner_radius = 12.0F;
  text_field.padding = EdgeInsets::Symmetric(16.0F, 12.0F);

  auto switch_style = UseEnvironment<SwitchStyle>();
  switch_style.width = 46.0F;
  switch_style.height = 27.0F;
  switch_style.minimum_interactive_height = 27.0F;
  switch_style.state_layer_size = 27.0F;
  switch_style.unchecked_track = colors::surface_light;
  switch_style.checked_track = colors::accent_dim;
  switch_style.unchecked_track_border = colors::border_light;
  switch_style.checked_track_border = colors::accent;
  switch_style.unchecked_thumb = colors::tertiary;
  switch_style.checked_thumb = colors::accent;
  switch_style.unchecked_thumb_radius = 10.5F;
  switch_style.checked_thumb_radius = 10.5F;
  switch_style.track_border_width = 1.0F;
  switch_style.corner_radius = 13.5F;

  ThemeDefinition overrides;
  overrides.Set(std::move(text_field));
  overrides.Set(std::move(switch_style));
  return Theme(std::move(overrides), std::move(screen));
}

} // namespace linecode::presentation
