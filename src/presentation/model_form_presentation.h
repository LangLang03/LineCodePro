#pragma once

#include <cstdint>
#include <utility>

#include "domain/model_config.h"

namespace linecode::presentation {

struct ModelFormLayoutMetrics final {
  // The legacy action TextView measures 148px at 420dpi (about 56.4dp),
  // including its horizontal content padding. Keep one fixed slot per action
  // so both the title and the trailing group retain the measured positions.
  static constexpr float header_action_minimum_width = 56.0F;
  static constexpr float header_action_minimum_height = 39.0F;
  static constexpr float header_action_baseline_padding = 3.0F;
  static constexpr float toggle_height = 46.0F;
  static constexpr float toggle_baseline_padding = 2.0F;
  static constexpr float latin_toggle_height = 44.5F;
  static constexpr float latin_toggle_baseline_padding = 0.75F;
  static constexpr float acceleration_label_bottom_padding = 7.0F;
};

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

[[nodiscard]] constexpr ProtocolTabDecision ResolveProtocolTabDecision(
    domain::ModelProtocol current, domain::ModelProtocol target,
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
