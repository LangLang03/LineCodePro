#include "presentation/screens/model_add_screen.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <functional>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <app_resources.h>
#include <huxerui/huxerui.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/components/legacy_switch.h"
#include "presentation/line_theme.h"
#include "presentation/model_form_presentation.h"
#include "presentation/model_protocol_presentation.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

enum class CatalogTarget : std::uint8_t { primary, compression };
enum class LocalAcceleration : std::uint8_t { automatic, cpu, npu };

struct ModelFormState final {
  std::string id;
  TextEditingValue name;
  domain::ModelProtocol protocol{domain::ModelProtocol::openai_compatible};
  std::string provider_label;
  TextEditingValue base_url;
  TextEditingValue api_key;
  ModelSelectionSlots<TextEditingValue> primary_id;
  TextEditingValue tool_limit;
  TextEditingValue context_size;
  ModelSelectionSlots<TextEditingValue> compression_id;
  bool compression_enabled{};
  bool compression_auto{true};
  bool local{};
  bool protocol_locked{};
  bool preset_mode{};
  bool primary_querying{};
  bool compression_querying{};
  bool probing{};
  bool saving{};
  bool attempted_save{};
  LocalAcceleration acceleration{LocalAcceleration::automatic};
  std::vector<std::string> primary_catalog;
  std::vector<std::string> compression_catalog;
  std::string error;
};

using CatalogMember = std::vector<std::string> ModelFormState::*;
using SelectionMember = ModelSelectionSlots<TextEditingValue> ModelFormState::*;
using QueryingMember = bool ModelFormState::*;

void CompletePrimarySelection(ModelFormState &state, bool custom) {
  if (!custom && state.preset_mode && state.name.text.empty()) {
    state.name = TextEditingValue::FromText(state.primary_id.catalog.text);
  }
}

void CompleteCompressionSelection(ModelFormState &, bool) {}

void CompletePrimaryCatalogLoad(ModelFormState &state) {
  state.primary_id.SetCustom(false);
}

void CompleteCompressionCatalogLoad(ModelFormState &) {}

struct CatalogTargetPolicy final {
  CatalogTarget target;
  CatalogMember catalog;
  SelectionMember selection;
  QueryingMember querying;
  void (*after_selection)(ModelFormState &, bool);
  void (*after_load)(ModelFormState &);
};

const std::array catalog_target_policies{
    CatalogTargetPolicy{
        CatalogTarget::primary,
        &ModelFormState::primary_catalog,
        &ModelFormState::primary_id,
        &ModelFormState::primary_querying,
        CompletePrimarySelection,
        CompletePrimaryCatalogLoad,
    },
    CatalogTargetPolicy{
        CatalogTarget::compression,
        &ModelFormState::compression_catalog,
        &ModelFormState::compression_id,
        &ModelFormState::compression_querying,
        CompleteCompressionSelection,
        CompleteCompressionCatalogLoad,
    },
};

[[nodiscard]] const CatalogTargetPolicy &
CatalogPolicyFor(CatalogTarget target) noexcept {
  const auto found = std::ranges::find(catalog_target_policies, target,
                                       &CatalogTargetPolicy::target);
  if (found != catalog_target_policies.end())
    return *found;
  std::unreachable();
}

struct ValidationPresentation final {
  application::ModelValidationCode code;
  StringResource message;
};

const std::array validation_presentations{
    ValidationPresentation{
        application::ModelValidationCode::local_backend_unavailable,
        app::strings::model_form_local_pending},
    ValidationPresentation{
        application::ModelValidationCode::missing_name_or_model_id,
        app::strings::model_form_missing_id},
    ValidationPresentation{application::ModelValidationCode::missing_api_key,
                           app::strings::model_form_missing_key},
    ValidationPresentation{
        application::ModelValidationCode::invalid_tool_call_limit,
        app::strings::model_form_invalid_tool_limit},
    ValidationPresentation{
        application::ModelValidationCode::missing_compression_model_id,
        app::strings::model_form_missing_compression},
};

struct LocalAccelerationPresentation final {
  LocalAcceleration mode;
  StringResource label;
};

const std::array local_acceleration_presentations{
    LocalAccelerationPresentation{LocalAcceleration::automatic,
                                  app::strings::model_form_acceleration_auto},
    LocalAccelerationPresentation{LocalAcceleration::cpu,
                                  app::strings::model_form_acceleration_cpu},
    LocalAccelerationPresentation{LocalAcceleration::npu,
                                  app::strings::model_form_acceleration_npu},
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
      .primary_id =
          ModelSelectionSlots<TextEditingValue>{
              .manual = TextEditingValue::FromText(draft.model_id),
              .catalog = TextEditingValue::FromText(draft.model_id),
              .custom = !draft.model_id.empty(),
          },
      .tool_limit = TextEditingValue::FromText(draft.tool_call_limit),
      .context_size = TextEditingValue::FromText(draft.context_size),
      .compression_id =
          ModelSelectionSlots<TextEditingValue>{
              .manual = TextEditingValue::FromText(draft.compression_model_id),
              .catalog = TextEditingValue::FromText(draft.compression_model_id),
              .custom = !draft.compression_model_id.empty(),
          },
      .compression_enabled = draft.compression_enabled,
      .compression_auto = draft.compression_auto,
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
      .model_id = state.primary_id.Effective().text,
      .tool_call_limit = state.tool_limit.text,
      .compression_enabled = state.compression_enabled,
      .compression_auto = state.compression_auto,
      .compression_model_id = state.compression_id.Effective().text,
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

auto ChangeConnectionText(State<ModelFormState> state,
                          TextEditingValue ModelFormState::*member) {
  return [state, member](const TextEditingValue &value) {
    auto next = state.Get();
    next.*member = value;
    next.primary_catalog.clear();
    next.primary_id.ClearCatalog();
    next.compression_catalog.clear();
    next.compression_id.ClearCatalog();
    next.error.clear();
    state = std::move(next);
  };
}

auto ChangeManualSelection(State<ModelFormState> state,
                           SelectionMember member) {
  return [state, member](const TextEditingValue &value) {
    auto next = state.Get();
    (next.*member).manual = value;
    next.error.clear();
    state = std::move(next);
  };
}

StringVariant ValidationMessage(application::ModelValidationCode code) {
  const auto found = std::ranges::find(validation_presentations, code,
                                       &ValidationPresentation::code);
  if (found != validation_presentations.end())
    return found->message;
  std::unreachable();
}

View HeaderAction(StringVariant label, bool enabled,
                  std::function<void()> action) {
  return Stack{
      Text(std::move(label))
          .Style(Label(16.0F, FontWeight::Medium,
                       enabled ? colors::accent : colors::tertiary))
          .With(Offset(
              Point{0.0F, ModelFormLayoutMetrics::header_action_offset_y})),
  }
      .OnClick([enabled, action = std::move(action)] {
        if (enabled && action) {
          std::invoke(action);
        }
      })
      .With(Padding(EdgeInsets::Symmetric(12.0F, 8.0F)),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Enabled{enabled}, Focusable(),
            PointerCursor(enabled ? PointerCursorKind::Hand
                                  : PointerCursorKind::Default));
}

View SectionLabel(StringVariant text, ModelFormSectionLabelKind kind,
                  float bottom = 8.0F) {
  // The legacy label uses LayoutParams margins. Keep those gaps as siblings
  // so the Text's own bounds contain only the painted label.
  const auto &metrics = ModelFormSectionLabelMetricsFor(kind);
  return Column{
      Stack{}.With(Frame{.height = 16.0F}),
      Stack{Text(std::move(text))
                .Style(Label(13.0F, FontWeight::Medium, colors::secondary))}
          .With(Frame{.height = metrics.line_box_height},
                Align(HorizontalAlignment::Start, VerticalAlignment::Center)),
      Stack{}.With(Frame{.height = bottom}),
  };
}

View SupportingText(StringVariant text, float top = 8.0F,
                    float bottom = 0.0F) {
  return Text(std::move(text))
      .Style(Label(11.0F, FontWeight::Regular, colors::tertiary))
      .With(Padding(EdgeInsets{.top = top, .bottom = bottom}));
}

StringVariant BaseUrlPlaceholder(domain::ModelProtocol protocol) {
  return std::string{
      ModelProtocolPresentationFor(protocol).base_url_placeholder};
}

StringVariant BaseUrlHint(domain::ModelProtocol protocol) {
  return ModelProtocolPresentationFor(protocol).base_url_hint;
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

View ProtocolSelector(State<ModelFormState> state, ToastHandle toast) {
  std::vector<View> items;
  items.reserve(model_protocol_presentations.size());
  for (const auto &presentation : model_protocol_presentations) {
    const auto protocol = presentation.protocol;
    const bool selected = state->protocol == protocol;
    const auto decision = ResolveProtocolTabDecision(state->protocol, protocol,
                                                     state->protocol_locked);
    items.push_back(
        Stack{
            Text(presentation.name)
                .Style(
                    Label(16.0F, FontWeight::Bold,
                          selected ? colors::text_on_color : colors::secondary))
                .Align(TextAlign::Center),
        }
            .OnClick([state, toast, protocol, decision] {
              if (decision.action == ProtocolTabAction::prompt_local_entry) {
                toast.Show(app::strings::model_form_open_local_form);
                return;
              }
              if (decision.action == ProtocolTabAction::prompt_custom_entry) {
                toast.Show(app::strings::model_form_open_custom_form);
                return;
              }
              if (decision.action != ProtocolTabAction::select_protocol)
                return;
              auto next = state.Get();
              next.protocol = protocol;
              next.provider_label =
                  std::string{domain::ModelProtocolLabel(protocol)};
              next.primary_id.ClearAll();
              next.compression_id.ClearCatalog();
              if (!domain::SupportsDedicatedCompression(protocol))
                next.compression_enabled = false;
              next.primary_catalog.clear();
              next.compression_catalog.clear();
              next.error.clear();
              state = std::move(next);
            })
            .With(Frame{.height = 46.0F}, Grow(),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(selected ? colors::accent : colors::surface_light),
                  CornerRadius(12.0F), Enabled{decision.enabled},
                  PointerCursor(decision.enabled
                                    ? PointerCursorKind::Hand
                                    : PointerCursorKind::Default)));
  }
  return Row(std::move(items)).With(Spacing(8.0F));
}

View SwitchHeader(StringVariant label,
                  std::optional<StringVariant> switch_label, bool checked,
                  bool enabled, float top, std::function<void(bool)> changed) {
  std::vector<View> trailing;
  if (switch_label.has_value()) {
    trailing.push_back(
        Text(std::move(*switch_label))
            .Style(Label(13.0F, FontWeight::Medium, colors::secondary)));
  }
  trailing.push_back(
      LegacySwitch(checked, std::move(changed)).With(Enabled{enabled}));
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

View QueryButton(bool enabled, bool busy, std::function<void()> action) {
  const bool highlighted = enabled || busy;
  const bool interactive = enabled && !busy;
  const Color tint = highlighted ? colors::text_on_color : colors::tertiary;
  return Row{
      Glyph(app::images::search, 16.0F, tint),
      Text(busy ? StringVariant{app::strings::model_form_query_loading}
                : StringVariant{app::strings::model_form_query})
          .Style(Label(16.0F, FontWeight::Bold, tint)),
  }
      .OnClick([interactive, action = std::move(action)] {
        if (interactive && action)
          std::invoke(action);
      })
      .With(Frame{.height = 48.0F, .min_width = 76.0F}, Spacing(4.0F),
            Padding(EdgeInsets::Symmetric(16.0F, 0.0F)),
            CrossAlign(CrossAxisAlignment::Center),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(highlighted ? colors::accent : colors::surface_light),
            CornerRadius(12.0F),
            PointerCursor(interactive ? PointerCursorKind::Hand
                                      : PointerCursorKind::Default));
}

void SelectCatalogItem(State<ModelFormState> state, CatalogTarget target,
                       std::string model, bool custom) {
  const auto &policy = CatalogPolicyFor(target);
  auto next = state.Get();
  auto &selection = next.*policy.selection;
  if (custom)
    selection.ChooseCustom();
  else
    selection.ChooseCatalog(TextEditingValue::FromText(std::move(model)));
  std::invoke(policy.after_selection, next, custom);
  next.error.clear();
  state = std::move(next);
}

enum class PickerRowKind : std::uint8_t { catalog_entry, custom_entry };

struct PickerRowPresentation final {
  PickerRowKind kind;
  colors::Token text_color;
  bool displays_selection;
};

constexpr std::array picker_row_presentations{
    PickerRowPresentation{PickerRowKind::catalog_entry, colors::text, true},
    PickerRowPresentation{PickerRowKind::custom_entry, colors::accent, false},
};

[[nodiscard]] const PickerRowPresentation &
PickerRowVisual(PickerRowKind kind) noexcept {
  const auto found = std::ranges::find(picker_row_presentations, kind,
                                       &PickerRowPresentation::kind);
  if (found != picker_row_presentations.end())
    return *found;
  std::unreachable();
}

View PickerRow(BottomSheetContext sheet, StringVariant label, bool selected,
               PickerRowKind kind, std::function<void()> choose) {
  const auto &presentation = PickerRowVisual(kind);
  return Row{
      Text(std::move(label))
          .Style(Label(16.0F, FontWeight::Regular, presentation.text_color))
          .With(Grow()),
      presentation.displays_selection && selected
          ? Glyph(app::images::check, 16.0F, colors::accent)
                .With(Frame{.width = 18.0F, .height = 18.0F})
          : Stack{}.With(Frame{.width = 0.0F, .height = 0.0F}),
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
  const auto &policy = CatalogPolicyFor(target);
  const auto models = state.Get().*policy.catalog;
  const std::string selected = (state.Get().*policy.selection).catalog.text;
  sheets.Show([models, selected, state, target](BottomSheetContext sheet) {
    std::vector<View> rows;
    rows.reserve(models.size() + 1);
    for (const auto &model : models) {
      rows.push_back(PickerRow(sheet, model, model == selected,
                               PickerRowKind::catalog_entry,
                               [state, target, model] {
                                 SelectCatalogItem(state, target, model, false);
                               }));
    }
    rows.push_back(PickerRow(
        sheet, app::strings::model_form_custom_model_picker, false,
        PickerRowKind::custom_entry,
        [state, target] { SelectCatalogItem(state, target, {}, true); }));

    View panel =
        Column{
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
            Stack{}.With(Frame{.width = 1.0F, .height = 12.0F}),
        }
            .With(Frame{.max_width = 560.0F}, Background(colors::elevated),
                  CornerRadius(CornerRadii::Top(16.0F)), ClipChildren(),
                  CrossAlign(CrossAxisAlignment::Stretch));

    // ModelPickerDialog leaves the legacy 16dp horizontal dialog inset. The
    // presentation host itself is intentionally transparent, so apply that
    // inset outside the painted panel and retain the 560dp expanded-width cap.
    return Column{std::move(panel)}.With(
        Padding(EdgeInsets::Symmetric(16.0F, 0.0F)),
        CrossAlign(CrossAxisAlignment::Stretch));
  });
}

Task<void>
QueryModels(std::shared_ptr<application::ModelCatalogGateway> catalog,
            State<ModelFormState> state, CatalogTarget target,
            BottomSheetHandle sheets, ToastHandle toast) {
  const auto &policy = CatalogPolicyFor(target);
  auto next = state.Get();
  next.*policy.querying = true;
  next.error.clear();
  state = std::move(next);

  const auto draft = MakeDraft(state.Get());
  auto result = co_await catalog->Fetch(
      draft.protocol, application::ModelFormService::EffectiveBaseUrl(draft),
      draft.api_key);
  const bool has_models = result && !result->empty();
  next = state.Get();
  next.*policy.querying = false;
  if (!result) {
    next.error = result.error().message;
    toast.Show(result.error().message);
  } else if (result->empty()) {
    next.error.clear();
    toast.Show(app::strings::model_form_query_empty);
  } else {
    next.*policy.catalog = std::move(*result);
    std::invoke(policy.after_load, next);
  }
  state = std::move(next);
  if (has_models)
    ShowModelPicker(sheets, state, target);
}

Task<void> ProbeModel(std::shared_ptr<application::ModelCatalogGateway> catalog,
                      State<ModelFormState> state, ToastHandle toast,
                      DialogHandle dialogs) {
  const auto built =
      application::ModelFormService::BuildForProbe(MakeDraft(state.Get()));
  if (!built) {
    toast.Show(ValidationMessage(built.error().code));
    co_return;
  }
  auto next = state.Get();
  next.probing = true;
  state = std::move(next);
  const auto started = std::chrono::steady_clock::now();
  auto result = co_await catalog->Probe(*built);
  const auto elapsed = std::chrono::duration_cast<std::chrono::milliseconds>(
                           std::chrono::steady_clock::now() - started)
                           .count();
  next = state.Get();
  next.probing = false;
  if (result) {
    next.error.clear();
    state = std::move(next);
    const auto first = result->response.find_first_not_of(" \t\n\r");
    const auto last = result->response.find_last_not_of(" \t\n\r");
    const std::string response =
        first == std::string::npos
            ? std::string{}
            : result->response.substr(first, last - first + 1U);
    const bool has_data = !response.empty();
    dialogs.Show(app::strings::model_form_test_result_title,
                 StringVariant::Format(
                     has_data
                         ? app::strings::model_form_test_result_success
                         : app::strings::model_form_test_result_success_no_data,
                     elapsed, response),
                 app::strings::model_form_test_result_confirm,
                 app::strings::common_cancel);
  } else {
    next.error = result.error().message;
    state = std::move(next);
    toast.Show(StringVariant::Format(app::strings::model_form_test_result_error,
                                     result.error().message, elapsed));
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
  next.saving = true;
  state = std::move(next);
  auto saved = co_await store->Save(*built);
  if (!saved) {
    next = state.Get();
    next.saving = false;
    next.error = saved.error().message;
    state = std::move(next);
    toast.Show(saved.error().message);
    co_return;
  }
  auto selected = co_await store->Select(saved->id);
  if (!selected) {
    next = state.Get();
    next.saving = false;
    next.error = selected.error().message;
    state = std::move(next);
    toast.Show(selected.error().message);
    co_return;
  }
  next = state.Get();
  next.saving = false;
  state = std::move(next);
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
          Text(empty
                   ? StringVariant{app::strings::model_form_select_model_first}
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
                Border{.color = colors::border_light,
                       .width = ModelFormLayoutMetrics::form_border_width},
                CornerRadius(12.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
      QueryButton(enabled, busy, std::move(action)),
  }
      .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center));
}

View LocalForm(State<ModelFormState> state, ToastHandle toast) {
  std::vector<View> acceleration;
  acceleration.reserve(local_acceleration_presentations.size());
  for (const auto &presentation : local_acceleration_presentations) {
    const bool selected = state->acceleration == presentation.mode;
    acceleration.push_back(
        Stack{
            Text(presentation.label)
                .Style(
                    Label(16.0F, FontWeight::Bold,
                          selected ? colors::text_on_color : colors::secondary))
                .With(Offset(Point{
                    0.0F, ModelFormLayoutMetrics::acceleration_text_offset_y})),
        }
            .With(Frame{.height = ModelFormLayoutMetrics::toggle_height},
                  Grow(),
                  Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                  Background(selected ? colors::accent : colors::surface_light),
                  CornerRadius(12.0F)));
  }

  return Column{
      SectionLabel(app::strings::model_form_local_file,
                   ModelFormSectionLabelKind::cjk_or_mixed),
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
                Border{.color = colors::border_light,
                       .width = ModelFormLayoutMetrics::form_border_width},
                CornerRadius(12.0F), Focusable(),
                PointerCursor(PointerCursorKind::Hand)),
      SectionLabel(app::strings::model_form_local_context_size,
                   ModelFormSectionLabelKind::cjk_or_mixed),
      FormField(state->context_size,
                app::strings::model_form_local_context_size,
                app::strings::model_form_local_context_placeholder,
                ChangeText(state, &ModelFormState::context_size),
                ValidationResult::None(), TextInputType::Number),
      SupportingText(app::strings::model_form_local_context_hint),
      // The legacy acceleration section uses a 1dp tighter post-label gap than
      // the other CJK/mixed section labels.
      SectionLabel(app::strings::model_form_acceleration,
                   ModelFormSectionLabelKind::cjk_or_mixed,
                   ModelFormLayoutMetrics::acceleration_label_bottom_padding),
      Row(std::move(acceleration))
          .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
      SupportingText(app::strings::model_form_acceleration_hint),
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch));
}

} // namespace

[[huxerui::composable]] View
ModelAddContent(ModelAddScreenOptions options,
                std::shared_ptr<application::ModelStore> store,
                std::shared_ptr<application::ModelCatalogGateway> catalog,
                ModelAddScreenActions actions) {
  const bool editing = options.editing.has_value();
  const auto initial_draft =
      editing
          ? application::ModelFormService::Edit(*options.editing)
          : application::ModelFormService::New(options.preset, options.local);
  auto state =
      UseState(MakeState(initial_draft, editing || options.preset.has_value(),
                         options.preset.has_value()));
  const auto tasks = UseTaskScope();
  const auto toast = UseToast();
  const auto dialogs = UseDialog();
  const auto sheets = UseBottomSheet();

  const auto current_draft = MakeDraft(state.Get());
  const auto validation = application::ModelFormService::Build(current_draft);
  const bool can_save = validation.has_value();
  const bool can_query = application::ModelFormService::CanQuery(current_draft);

  auto save = [store, state, tasks, toast, actions] {
    if (state->saving)
      return;
    auto next = state.Get();
    next.attempted_save = true;
    state = std::move(next);
    tasks.Launch([store, state, toast, actions]() -> Task<void> {
      co_await SaveModel(store, state, toast, actions);
    });
  };
  auto test = [catalog, state, tasks, toast, dialogs] {
    if (state->probing)
      return;
    tasks.Launch([catalog, state, toast, dialogs]() -> Task<void> {
      co_await ProbeModel(catalog, state, toast, dialogs);
    });
  };
  auto query_primary = [catalog, state, tasks, toast, sheets, can_query] {
    if (!state->primary_catalog.empty()) {
      ShowModelPicker(sheets, state, CatalogTarget::primary);
      return;
    }
    if (!can_query || state->primary_querying) {
      return;
    }
    tasks.Launch([catalog, state, sheets, toast]() -> Task<void> {
      co_await QueryModels(catalog, state, CatalogTarget::primary, sheets,
                           toast);
    });
  };
  auto query_compression = [catalog, state, tasks, toast, sheets, can_query] {
    if (!state->compression_catalog.empty()) {
      ShowModelPicker(sheets, state, CatalogTarget::compression);
      return;
    }
    if (!can_query || state->compression_querying) {
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
      (state->protocol_locked || state->local)
          ? StringVariant::Format(
                app::strings::model_form_provider_named,
                state->local ? UseString(app::strings::model_protocol_local)
                             : state->provider_label)
          : StringVariant{app::strings::model_form_provider},
      ModelFormSectionLabelKind::cjk_or_mixed));
  form.push_back(ProtocolSelector(state, toast));

  if (state->local) {
    form.push_back(SectionLabel(app::strings::model_form_name,
                                ModelFormSectionLabelKind::cjk_or_mixed));
    form.push_back(FormField(state->name, app::strings::model_form_name,
                             app::strings::model_form_name_local_hint,
                             ChangeText(state, &ModelFormState::name)));
    form.push_back(LocalForm(state, toast));
  } else {
    form.push_back(SectionLabel(app::strings::model_form_name,
                                ModelFormSectionLabelKind::cjk_or_mixed));
    form.push_back(FormField(
        state->name, app::strings::model_form_name,
        state->preset_mode
            ? StringVariant{app::strings::model_form_name_optional_hint}
            : StringVariant{app::strings::model_form_name_remote_hint},
        ChangeText(state, &ModelFormState::name)));

    form.push_back(SectionLabel(app::strings::model_form_base_url,
                                ModelFormSectionLabelKind::latin));
    form.push_back(
        FormField(state->base_url, app::strings::model_form_base_url,
                  options.preset ? StringVariant{options.preset->placeholder}
                                 : BaseUrlPlaceholder(state->protocol),
                  ChangeConnectionText(state, &ModelFormState::base_url),
                  ValidationResult::None(), TextInputType::Url));
    // Android TextView adds 3dp between the two wrapped hint lines. HuxerUI's
    // public Text API intentionally uses the platform paragraph leading and
    // has no line-spacing override, so retain the legacy block height here.
    form.push_back(SupportingText(
        BaseUrlHint(state->protocol), 8.0F,
        ModelFormLayoutMetrics::base_url_hint_trailing_padding));

    form.push_back(SectionLabel(app::strings::model_form_api_key,
                                ModelFormSectionLabelKind::latin));
    form.push_back(
        FormField(state->api_key, app::strings::model_form_api_key,
                  app::strings::model_form_api_key_hint,
                  ChangeConnectionText(state, &ModelFormState::api_key),
                  ValidationResult::None(), TextInputType::Text, true));

    form.push_back(SwitchHeader(
        app::strings::model_form_model_id,
        StringVariant{app::strings::model_form_custom_model},
        state->primary_id.custom, true, 16.0F, [state](bool value) {
          auto next = state.Get();
          next.primary_id.SetCustom(value);
          state = std::move(next);
        }));
    if (state->primary_id.custom) {
      form.push_back(
          FormField(state->primary_id.manual, app::strings::model_form_model_id,
                    app::strings::model_form_model_id_hint,
                    ChangeManualSelection(state, &ModelFormState::primary_id)));
    } else {
      form.push_back(ModelSelector(state->primary_id.catalog, can_query,
                                   state->primary_querying, query_primary));
    }

    form.push_back(SectionLabel(app::strings::model_form_tool_limit,
                                ModelFormSectionLabelKind::cjk_or_mixed));
    form.push_back(FormField(state->tool_limit,
                             app::strings::model_form_tool_limit,
                             app::strings::model_form_tool_limit_placeholder,
                             ChangeText(state, &ModelFormState::tool_limit),
                             ValidationResult::None(), TextInputType::Number));
    form.push_back(SupportingText(app::strings::model_form_tool_limit_hint));

    form.push_back(SectionLabel(app::strings::model_form_context_size,
                                ModelFormSectionLabelKind::cjk_or_mixed));
    form.push_back(FormField(state->context_size,
                             app::strings::model_form_context_size,
                             app::strings::model_form_context_placeholder,
                             ChangeText(state, &ModelFormState::context_size)));
    form.push_back(SupportingText(app::strings::model_form_context_hint));

    if (domain::SupportsDedicatedCompression(state->protocol)) {
      form.push_back(SwitchHeader(app::strings::model_form_compression,
                                  std::nullopt, state->compression_enabled,
                                  true, 16.0F, [state](bool value) {
                                    auto next = state.Get();
                                    next.compression_enabled = value;
                                    state = std::move(next);
                                  }));
      if (state->compression_enabled) {
        form.push_back(
            SupportingText(app::strings::model_form_compression_hint, 0.0F));
        form.push_back(SwitchHeader(app::strings::model_form_compression_auto,
                                    std::nullopt, state->compression_auto, true,
                                    12.0F, [state](bool value) {
                                      auto next = state.Get();
                                      next.compression_auto = value;
                                      state = std::move(next);
                                    }));
        form.push_back(SwitchHeader(
            app::strings::model_form_compression_id,
            StringVariant{app::strings::model_form_compression_custom},
            state->compression_id.custom, !state->compression_auto, 12.0F,
            [state](bool value) {
              auto next = state.Get();
              next.compression_id.SetCustom(value);
              state = std::move(next);
            }));
        if (!state->compression_auto) {
          if (state->compression_id.custom) {
            form.push_back(FormField(
                state->compression_id.manual,
                app::strings::model_form_compression_id,
                app::strings::model_form_compression_id_hint,
                ChangeManualSelection(state, &ModelFormState::compression_id)));
          } else {
            form.push_back(ModelSelector(state->compression_id.catalog,
                                         can_query, state->compression_querying,
                                         query_compression));
          }
        }
      }
    }
  }

  std::vector<View> header_actions;
  header_actions.reserve(state->local ? 1U : 2U);
  if (!state->local) {
    header_actions.push_back(
        HeaderAction(app::strings::model_form_test, true, std::move(test)));
  }
  header_actions.push_back(
      HeaderAction(app::strings::model_form_save, can_save, std::move(save)));

  View screen =
      Column{
          LegacyScreenHeaderLayout{
              Stack{Glyph(app::images::chevron_left, 22.0F, colors::text)}
                  .OnClick([callback = actions.on_back] {
                    if (callback)
                      std::invoke(callback);
                  })
                  .With(Frame{.width = 36.0F, .height = 36.0F},
                        Align(HorizontalAlignment::Center,
                              VerticalAlignment::Center),
                        Focusable(), PointerCursor(PointerCursorKind::Hand)),
              Stack{Text(editing ? app::strings::model_form_edit_title
                                 : app::strings::model_form_add_title)
                        .Style(Label(17.0F, FontWeight::Bold))}
                  .With(Grow(),
                        Align(HorizontalAlignment::Center,
                              VerticalAlignment::Center),
                        Offset(Point{
                            0.0F,
                            ModelFormLayoutMetrics::header_title_offset_y})),
              Row(std::move(header_actions))
                  .With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center)),
          }
              .With(Frame{.min_height =
                              ModelFormLayoutMetrics::header_minimum_height},
                    Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
                    Background(colors::background)),
          // The legacy model form reserves three more physical pixels below
          // its header content than the shared header contract.  Keep that
          // compensation outside the centered header so the already-matched
          // title and action baselines do not move with the scroll viewport.
          Stack{}.With(Frame{.height = 1.14F},
                       Background(colors::background)),
          LegacyScreenHeaderDivider(),
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
  text_field.placeholder_style =
      Label(16.0F, FontWeight::Regular, colors::tertiary);
  text_field.caret = colors::accent;
  text_field.border_width = ModelFormLayoutMetrics::form_border_width;
  text_field.focused_border_width = ModelFormLayoutMetrics::form_border_width;
  text_field.outlined.corner_radii = CornerRadii{12.0F};
  text_field.padding = EdgeInsets::Symmetric(16.0F, 12.0F);

  ThemeDefinition overrides;
  overrides.Set(std::move(text_field));
  return Theme(std::move(overrides), std::move(screen));
}

[[huxerui::composable]] View
ModelAddScreen(ModelAddScreenOptions options,
               std::shared_ptr<application::ModelStore> store,
               std::shared_ptr<application::ModelCatalogGateway> catalog,
               ModelAddScreenActions actions) {
  ThemeDefinition overrides;
  overrides.Set(LineDialogBottomSheetStyle(UseLineColors()));
  return Theme(
      std::move(overrides),
      Scope([options = std::move(options), store = std::move(store),
             catalog = std::move(catalog), actions = std::move(actions)] {
        return ModelAddContent(options, store, catalog, actions);
      }));
}

} // namespace linecode::presentation
