#pragma once

#include <map>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/diff_file_restore.h"
#include "application/ports/diff_store.h"

namespace linecode::application {

// Portable half of the legacy `ToolReviewController`: the review cache plus the
// Accepted / Rejected decision for one tool call. The Android Handler, worker
// thread, and weak host callbacks are replaced by coroutines; the presentation
// layer drives `Review` and renders whatever the cache now reports.
class DiffReviewService {
public:
  DiffReviewService(DiffStore &diffs, DiffFileRestore &restore);

  DiffReviewService(const DiffReviewService &) = delete;
  DiffReviewService &operator=(const DiffReviewService &) = delete;

  // Records the user's decision. `state` normalizes exactly like the legacy
  // controller: only "rejected" rejects, everything else accepts. An empty
  // `tool_call_id` is ignored. A rejection with a resolvable diff identifier
  // reverts the change instead of merely recording the state.
  [[nodiscard]] huxerui::Task<DiffStoreResult<void>>
  Review(std::string tool_call_id, std::string state, std::string diff_id);

  // Second half of `rejectWithRevert`: guard the revert, restore the file, and
  // mark the record. Failures publish their legacy message and leave the state
  // empty so the card stays actionable.
  [[nodiscard]] huxerui::Task<DiffStoreResult<void>>
  RejectWithRevert(std::string diff_id);

  // The local-review overlay the legacy host applied before rendering a tool
  // message: the cache wins, a miss falls back to `Find`, and a record without
  // any state or message reports "nothing local" rather than an empty state.
  [[nodiscard]] huxerui::Task<
      DiffStoreResult<std::optional<domain::DiffRecord>>>
  CachedReview(std::string_view diff_id);

protected:
  // `localReviewCache` of the legacy controller. A present value with an empty
  // `optional` caches "the store has no such record" so repeated renders do not
  // re-query it; entries are erased whenever a review is written.
  std::map<std::string, std::optional<domain::DiffRecord>, std::less<>> cache_;

private:
  [[nodiscard]] huxerui::Task<DiffStoreResult<void>>
  SetLocalReview(std::string_view diff_id, std::string state,
                 std::string message);

  DiffStore &diffs_;
  DiffFileRestore &restore_;
};

} // namespace linecode::application
