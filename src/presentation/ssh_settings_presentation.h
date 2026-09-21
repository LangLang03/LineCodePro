#pragma once

namespace linecode::presentation {

enum class TermuxEntryAction {
  open_integration,
  show_unavailable,
};

[[nodiscard]] constexpr TermuxEntryAction
ResolveTermuxEntryAction(bool termux_available) noexcept {
  return termux_available ? TermuxEntryAction::open_integration
                          : TermuxEntryAction::show_unavailable;
}

} // namespace linecode::presentation
