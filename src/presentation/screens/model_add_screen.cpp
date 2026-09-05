#include "presentation/screens/model_add_screen.h"

#include <array>
#include <functional>
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

ValidationResult FieldValidation(bool invalid, StringVariant message) {
  return invalid ? ValidationResult::Invalid(std::move(message))
                 : ValidationResult::None();
}

View HeaderAction(StringVariant label, bool enabled,
                  std::function<void()> action) {
  return Text(std::move(label))
      .Style(Label(16.0F, FontWeight::Medium,
                   enabled ? colors::text : colors::tertiary))
      .OnClick([enabled, action = std::move(action)] {
        if (enabled && action) {
          std::invoke(action);
        }
      })
      .With(Frame{.min_width = 42.0F, .min_height = 36.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Enabled{enabled}, Focusable(),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

View SectionLabel(StringVariant text) {
  return Text(std::move(text))
      .Style(Label(13.0F, FontWeight::Bold, colors::tertiary))
      .With(Padding(EdgeInsets{
          .top = 20.0F, .right = 4.0F, .bottom = 8.0F, .left = 4.0F}));
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
                .Style(Label(12.0F,
                             selected ? FontWeight::Bold : FontWeight::Regular,
                             selected ? colors::text_on_color : colors::text))
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
                  Background(selected ? colors::accent : colors::elevated),
                  Border{.color = selected ? colors::accent : colors::border,
                         .width = 1.0F},
                  CornerRadius(12.0F), Enabled{enabled || selected},
                  PointerCursor(enabled ? PointerCursorKind::Hand
                                        : PointerCursorKind::Default)));
  }
  return Row(std::move(items)).With(Spacing(8.0F));
}

View SwitchSetting(StringVariant label, bool checked,
                   std::function<void(bool)> changed) {
  auto row_changed = changed;
  return Row{
      Text(std::move(label)).Style(Label(14.0F)).With(Grow()),
      Switch(checked).OnChanged(std::move(changed)),
  }
      .OnClick([checked, changed = std::move(row_changed)] {
        if (changed)
          std::invoke(changed, !checked);
      })
      .With(Frame{.min_height = 52.0F}, CrossAlign(CrossAxisAlignment::Center),
            Focusable(), PointerCursor(PointerCursorKind::Hand));
}

View QueryButton(bool enabled, bool busy, std::function<void()> action) {
  return Text(busy ? StringVariant{"…"}
                   : StringVariant{app::strings::model_form_query})
      .Style(Label(13.0F, FontWeight::Bold,
                   enabled ? colors::text_on_color : colors::tertiary))
      .Align(TextAlign::Center)
      .OnClick([enabled, action = std::move(action)] {
        if (enabled && action)
          std::invoke(action);
      })
      .With(Frame{.width = 72.0F, .min_height = 46.0F},
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(enabled ? colors::accent : colors::surface_light),
            CornerRadius(12.0F), Enabled{enabled},
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

Task<void>
QueryModels(std::shared_ptr<application::ModelCatalogGateway> catalog,
            State<ModelFormState> state, CatalogTarget target) {
  auto next = state.Get();
  next.busy = true;
  next.error.clear();
  state = std::move(next);

  const auto draft = MakeDraft(state.Get());
  auto result = co_await catalog->Fetch(
      draft.protocol, application::ModelFormService::EffectiveBaseUrl(draft),
      draft.api_key);
  next = state.Get();
  next.busy = false;
  if (!result) {
    next.error = result.error().message;
  } else if (result->empty()) {
    next.error = "No models returned";
  } else if (target == CatalogTarget::primary) {
    next.primary_catalog = std::move(*result);
    next.custom_model = false;
  } else {
    next.compression_catalog = std::move(*result);
  }
  state = std::move(next);
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

View CatalogRows(const std::vector<std::string> &models,
                 std::string_view selected,
                 std::function<void(std::string)> select) {
  std::vector<View> rows;
  rows.reserve(models.size());
  for (const auto &model : models) {
    const bool active = model == selected;
    rows.push_back(Row{
        Text(model)
            .Style(
                Label(13.0F, active ? FontWeight::Bold : FontWeight::Regular))
            .With(Grow()),
        active ? Glyph(app::images::check, 16.0F, colors::accent)
               : Spacer().With(Frame{.width = 16.0F, .height = 16.0F}),
    }
                       .OnClick([select, model] {
                         if (select)
                           std::invoke(select, model);
                       })
                       .With(Frame{.min_height = 44.0F},
                             Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
                             CrossAlign(CrossAxisAlignment::Center),
                             Background(active ? colors::accent_muted
                                               : Color::Transparent()),
                             PointerCursor(PointerCursorKind::Hand)));
  }
  return Column(std::move(rows))
      .With(Background(colors::elevated),
            Border{.color = colors::border, .width = 1.0F}, CornerRadius(12.0F),
            ClipChildren(), CrossAlign(CrossAxisAlignment::Stretch));
}

View LocalForm(State<ModelFormState> state) {
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
            .Style(Label(13.0F,
                         selected ? FontWeight::Bold : FontWeight::Regular,
                         selected ? colors::text_on_color : colors::text))
            .Align(TextAlign::Center)
            .OnClick([state, index] {
              auto next = state.Get();
              next.acceleration = static_cast<int>(index);
              state = std::move(next);
            })
            .With(Frame{.min_height = 44.0F}, Grow(),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(selected ? colors::accent : colors::elevated),
                  Border{.color = selected ? colors::accent : colors::border,
                         .width = 1.0F},
                  CornerRadius(12.0F), PointerCursor(PointerCursorKind::Hand)));
  }

  return Column{
      SectionLabel(app::strings::model_form_local_file),
      Row{
          Stack{Glyph(app::images::file, 22.0F, colors::accent)}.With(
              Frame{.width = 44.0F, .height = 44.0F},
              Align(HorizontalAlignment::Center, VerticalAlignment::Center),
              Background(colors::accent_muted), CornerRadius(8.0F)),
          Column{
              Text(app::strings::model_form_local_file)
                  .Style(Label(15.0F, FontWeight::Medium)),
              Text(app::strings::model_form_choose_file)
                  .Style(Label(11.0F, FontWeight::Regular, colors::tertiary)),
          }
              .With(Grow()),
          Glyph(app::images::chevron_right, 17.0F, colors::tertiary),
      }
          .With(Frame{.min_height = 74.0F}, Spacing(12.0F),
                Padding(EdgeInsets::All(12.0F), ),
                CrossAlign(CrossAxisAlignment::Center),
                Background(colors::elevated),
                Border{.color = colors::border, .width = 1.0F},
                CornerRadius(12.0F)),
      SectionLabel(app::strings::model_form_context_size),
      FormField(state->context_size, app::strings::model_form_context_size,
                app::strings::model_form_context_hint,
                ChangeText(state, &ModelFormState::context_size),
                ValidationResult::None(), TextInputType::Text),
      SectionLabel(app::strings::model_form_acceleration),
      Row(std::move(acceleration)).With(Spacing(8.0F)),
      Text(app::strings::model_form_local_pending)
          .Style(Label(12.0F, FontWeight::Regular, colors::tertiary))
          .With(Padding(EdgeInsets{.top = 16.0F})),
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
  auto query_primary = [catalog, state, tasks, toast, can_query] {
    if (!can_query) {
      toast.Show(app::strings::model_form_query_requirements);
      return;
    }
    tasks.Launch([catalog, state]() -> Task<void> {
      co_await QueryModels(catalog, state, CatalogTarget::primary);
    });
  };
  auto query_compression = [catalog, state, tasks, toast, can_query] {
    if (!can_query) {
      toast.Show(app::strings::model_form_query_requirements);
      return;
    }
    tasks.Launch([catalog, state]() -> Task<void> {
      co_await QueryModels(catalog, state, CatalogTarget::compression);
    });
  };

  const bool attempted = state->attempted_save;
  const bool missing_id = attempted && state->model_id.text.empty();
  const bool missing_key = attempted && state->api_key.text.empty();
  const bool invalid_tool =
      attempted && !validation &&
      validation.error().code ==
          application::ModelValidationCode::invalid_tool_call_limit;
  const bool missing_compression =
      attempted && !validation &&
      validation.error().code ==
          application::ModelValidationCode::missing_compression_model_id;

  std::vector<View> form;
  form.reserve(30);
  form.push_back(SectionLabel(app::strings::model_form_protocol));
  form.push_back(ProtocolSelector(state));

  if (state->local) {
    form.push_back(SectionLabel(app::strings::model_form_name));
    form.push_back(FormField(state->name, app::strings::model_form_name,
                             app::strings::model_form_name_hint,
                             ChangeText(state, &ModelFormState::name)));
    form.push_back(LocalForm(state));
  } else {
    form.push_back(SectionLabel(app::strings::model_form_name));
    form.push_back(FormField(state->name, app::strings::model_form_name,
                             app::strings::model_form_name_hint,
                             ChangeText(state, &ModelFormState::name)));

    form.push_back(SectionLabel(app::strings::model_form_base_url));
    form.push_back(FormField(state->base_url, app::strings::model_form_base_url,
                             app::strings::model_form_base_url_hint,
                             ChangeText(state, &ModelFormState::base_url),
                             ValidationResult::None(), TextInputType::Url));

    form.push_back(SectionLabel(app::strings::model_form_api_key));
    form.push_back(FormField(
        state->api_key, app::strings::model_form_api_key, "",
        ChangeText(state, &ModelFormState::api_key),
        FieldValidation(missing_key, app::strings::model_form_missing_key),
        TextInputType::Text, true));

    form.push_back(SectionLabel(app::strings::model_form_model_id));
    form.push_back(SwitchSetting(app::strings::model_form_custom_model,
                                 state->custom_model, [state](bool value) {
                                   auto next = state.Get();
                                   next.custom_model = value;
                                   next.model_id =
                                       TextEditingValue::FromText("");
                                   state = std::move(next);
                                 }));
    if (state->custom_model) {
      form.push_back(FormField(
          state->model_id, app::strings::model_form_model_id,
          app::strings::model_form_model_id,
          ChangeText(state, &ModelFormState::model_id),
          FieldValidation(missing_id, app::strings::model_form_missing_id)));
    } else {
      form.push_back(Row{
          Stack{
              Text(state->model_id.text.empty()
                       ? StringVariant{app::strings::model_form_select_model}
                       : StringVariant{state->model_id.text})
                  .Style(Label(14.0F, FontWeight::Regular,
                               state->model_id.text.empty() ? colors::tertiary
                                                            : colors::text)),
          }
              .With(
                  Frame{.min_height = 46.0F}, Grow(),
                  Padding(EdgeInsets::Symmetric(12.0F, 10.0F)),
                  Align(HorizontalAlignment::Start, VerticalAlignment::Center),
                  Background(colors::elevated),
                  Border{.color = missing_id ? colors::danger : colors::border,
                         .width = 1.0F},
                  CornerRadius(12.0F)),
          QueryButton(can_query, state->busy, query_primary),
      }
                         .With(Spacing(8.0F)));
      if (!state->primary_catalog.empty()) {
        form.push_back(CatalogRows(
            state->primary_catalog, state->model_id.text,
            [state](std::string model) {
              auto next = state.Get();
              next.model_id = TextEditingValue::FromText(std::move(model));
              if (next.preset_mode && next.name.text.empty()) {
                next.name = TextEditingValue::FromText(next.model_id.text);
              }
              next.primary_catalog.clear();
              state = std::move(next);
            }));
      }
    }

    form.push_back(SectionLabel(app::strings::model_form_tool_limit));
    form.push_back(
        FormField(state->tool_limit, app::strings::model_form_tool_limit,
                  app::strings::model_form_tool_limit_hint,
                  ChangeText(state, &ModelFormState::tool_limit),
                  FieldValidation(invalid_tool,
                                  app::strings::model_form_invalid_tool_limit),
                  TextInputType::Number));

    form.push_back(SectionLabel(app::strings::model_form_context_size));
    form.push_back(FormField(state->context_size,
                             app::strings::model_form_context_size,
                             app::strings::model_form_context_hint,
                             ChangeText(state, &ModelFormState::context_size)));

    if (domain::SupportsDedicatedCompression(state->protocol)) {
      form.push_back(SectionLabel(app::strings::model_form_compression));
      form.push_back(SwitchSetting(app::strings::model_form_compression_enabled,
                                   state->compression_enabled,
                                   [state](bool value) {
                                     auto next = state.Get();
                                     next.compression_enabled = value;
                                     state = std::move(next);
                                   }));
      if (state->compression_enabled) {
        form.push_back(SwitchSetting(app::strings::model_form_compression_auto,
                                     state->compression_auto,
                                     [state](bool value) {
                                       auto next = state.Get();
                                       next.compression_auto = value;
                                       state = std::move(next);
                                     }));
        if (!state->compression_auto) {
          form.push_back(FormField(
              state->compression_id, app::strings::model_form_compression_id,
              app::strings::model_form_compression_id,
              ChangeText(state, &ModelFormState::compression_id),
              FieldValidation(missing_compression,
                              app::strings::model_form_missing_compression)));
          form.push_back(Row{
              Spacer().With(Grow()),
              QueryButton(can_query, state->busy, query_compression),
          });
          if (!state->compression_catalog.empty()) {
            form.push_back(CatalogRows(
                state->compression_catalog, state->compression_id.text,
                [state](std::string model) {
                  auto next = state.Get();
                  next.compression_id =
                      TextEditingValue::FromText(std::move(model));
                  next.compression_catalog.clear();
                  state = std::move(next);
                }));
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

  return Column{
      LegacyScreenHeaderLayout{
          Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
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
                                              .bottom = 100.0F,
                                              .left = 16.0F}),
                           CrossAlign(CrossAxisAlignment::Stretch)))
          .ScrollAxis(Axis::Vertical)
          .With(Grow(), ScrollBar()),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
