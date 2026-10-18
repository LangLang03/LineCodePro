#pragma once

#include <cstdint>
#include <optional>
#include <string>
#include <utility>

#include "domain/behavior_settings.h"

namespace linecode::presentation {

// One shared snapshot of the AI behaviour settings for the whole signed-in
// scope. Every screen reads this cell and every write goes through the helpers
// below, so the switch the user sees and the value the next request loads from
// the store cannot drift apart.
//
// The previous design gave the LLM settings screen, the chat screen and the
// store three independent copies: a write that failed (or a read that landed
// after the user's tap) left the screen showing a value the store never had,
// which is how "minimal mode is off but the app is still minimal" happened.
struct BehaviorSettingsState final {
  domain::AiBehaviorSettings value{};
  // False until the first successful load. Screens must not render editable
  // controls before this is true: showing the struct defaults would again be a
  // value the store never had.
  bool loaded{};
  // Last load/persist failure, shown inline by the settings screen.
  std::string error{};
  // Bumped by every user edit. A load result is only applied when this has not
  // changed since that load started, so a slow read can never undo a newer tap.
  std::uint64_t edit_revision{};
};

// Applies an optimistic edit and returns the new edit revision.
[[nodiscard]] inline std::uint64_t ApplyBehaviorEdit(
    BehaviorSettingsState &state, domain::AiBehaviorSettings next) {
  state.value = std::move(next);
  state.error.clear();
  return ++state.edit_revision;
}

// True when a load that started at `load_started_at` may still be applied.
[[nodiscard]] inline bool CanApplyBehaviorLoad(
    const BehaviorSettingsState &state,
    std::uint64_t load_started_at) noexcept {
  return state.edit_revision == load_started_at;
}

// Applies a load result unless the user edited after the load started.
[[nodiscard]] inline bool ApplyBehaviorLoad(
    BehaviorSettingsState &state, std::uint64_t load_started_at,
    domain::AiBehaviorSettings loaded) {
  if (!CanApplyBehaviorLoad(state, load_started_at))
    return false;
  state.value = std::move(loaded);
  state.loaded = true;
  state.error.clear();
  return true;
}

// Records a load failure and leaves `loaded` untouched: the UI keeps showing
// that settings could not be read instead of a default that was never stored.
inline void ApplyBehaviorLoadFailure(BehaviorSettingsState &state,
                                     std::string message) {
  state.error = std::move(message);
}

// Rolls an optimistic edit back after a failed persist. A newer edit wins: the
// revert only happens while the cell still holds `optimistic`.
[[nodiscard]] inline bool ApplyBehaviorPersistFailure(
    BehaviorSettingsState &state, const domain::AiBehaviorSettings &optimistic,
    const domain::AiBehaviorSettings &previous, std::string message) {
  state.error = std::move(message);
  if (!(state.value == optimistic))
    return false;
  state.value = previous;
  return true;
}

} // namespace linecode::presentation
