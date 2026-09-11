#pragma once

#include <cstdint>
#include <string>

namespace linecode::domain {

// One recorded file change, mirroring the legacy `DiffRecord` value object.
//
// `old_exists` distinguishes "created a file" from "rewrote an empty one",
// which the revert path needs in order to delete instead of truncate.
struct DiffRecord final {
  std::string id;
  std::string file_path;
  std::string old_content;
  std::string new_content;
  bool old_exists{true};
  std::int64_t timestamp{};
  bool reverted{};
  // "accepted" / "rejected" / empty while the change is still pending review.
  std::string review_state;
  std::string review_message;

  // The legacy `reviewState(record)`: an explicit state wins, otherwise a
  // reverted record reports "rejected".
  [[nodiscard]] std::string EffectiveReviewState() const {
    if (!review_state.empty())
      return review_state;
    return reverted ? "rejected" : std::string{};
  }

  bool operator==(const DiffRecord &) const = default;
};

} // namespace linecode::domain
