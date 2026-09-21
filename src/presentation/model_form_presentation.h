#pragma once

#include <array>
#include <cstddef>
#include <cstdint>
#include <utility>

#include "domain/model_config.h"

namespace linecode::presentation {

struct ModelFormLayoutMetrics final {
  static constexpr float header_minimum_height = 61.14F;
  static constexpr float header_title_offset_y = 1.52F;
  static constexpr float header_action_offset_y = 1.90F;
  static constexpr float toggle_height = 46.0F;
  static constexpr float acceleration_label_bottom_padding = 9.3F;
  static constexpr float acceleration_text_offset_y = 1.14F;
  static constexpr float form_border_width = 1.14F;
  // The legacy 11sp TextView adds explicit leading to wrapped form hints.
  // HuxerUI exposes platform paragraph metrics but no line-spacing override.
  static constexpr float base_url_hint_trailing_padding = 6.0F;
};

enum class ModelFormSectionLabelKind : std::uint8_t {
  cjk_or_mixed,
  latin,
  count,
};

struct ModelFormSectionLabelMetrics final {
  float line_box_height;
};

inline constexpr std::array model_form_section_label_metrics{
    ModelFormSectionLabelMetrics{.line_box_height = 18.67F},
    ModelFormSectionLabelMetrics{.line_box_height = 15.24F},
};

static_assert(model_form_section_label_metrics.size() ==
              static_cast<std::size_t>(ModelFormSectionLabelKind::count));

[[nodiscard]] constexpr const ModelFormSectionLabelMetrics &
ModelFormSectionLabelMetricsFor(ModelFormSectionLabelKind kind) noexcept {
  return model_form_section_label_metrics[static_cast<std::size_t>(kind)];
}

// The legacy form keeps the free-form EditText and catalog selection alive at
// the same time. Switching the "custom" control only changes which slot is
// effective; choosing the explicit custom row is the operation that clears
// both values and starts a fresh manual entry.
template <typename Value> struct ModelSelectionSlots final {
  Value manual{};
  Value catalog{};
  bool custom{};

  [[nodiscard]] const Value &Effective() const noexcept {
    return custom ? manual : catalog;
  }

  void SetCustom(bool value) noexcept { custom = value; }

  void ChooseCatalog(Value value) {
    catalog = std::move(value);
    custom = false;
  }

  void ChooseCustom() {
    manual = Value{};
    catalog = Value{};
    custom = true;
  }

  void ClearCatalog() { catalog = Value{}; }

  void ClearAll() {
    manual = Value{};
    catalog = Value{};
  }
};

enum class ProtocolTabAction : std::uint8_t {
  none,
  select_protocol,
  prompt_local_entry,
  prompt_custom_entry,
};

struct ProtocolTabDecision final {
  bool enabled{};
  ProtocolTabAction action{ProtocolTabAction::none};
};

[[nodiscard]] constexpr ProtocolTabDecision
ResolveProtocolTabDecision(domain::ModelProtocol current,
                           domain::ModelProtocol target,
                           bool locked_preset) noexcept {
  const bool selected = current == target;
  const bool enabled = !locked_preset || selected;
  if (!enabled)
    return {};
  if (target == domain::ModelProtocol::local_gguf)
    return {true, ProtocolTabAction::prompt_local_entry};
  if (current == domain::ModelProtocol::local_gguf)
    return {true, ProtocolTabAction::prompt_custom_entry};
  if (!locked_preset)
    return {true, ProtocolTabAction::select_protocol};
  return {true, ProtocolTabAction::none};
}

} // namespace linecode::presentation
