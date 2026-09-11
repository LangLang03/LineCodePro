#include "application/diff_review_service.h"

#include <exception>
#include <optional>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>

#include "domain/diff_record.h"

namespace linecode::application {

DiffReviewService::DiffReviewService(DiffStore &diffs, DiffFileRestore &restore)
    : diffs_(diffs), restore_(restore) {}

huxerui::Task<void> DiffReviewService::Review(std::string tool_call_id,
                                              std::string state,
                                              std::string diff_id) {
  if (tool_call_id.empty())
    co_return;

  // Only the exact legacy token rejects; every other value accepts.
  const std::string normalized_state =
      state == "rejected" ? "rejected" : "accepted";

  if (normalized_state == "rejected" && !diff_id.empty()) {
    co_await RejectWithRevert(std::move(diff_id));
    co_return;
  }

  // Accepted changes (and rejections without a persistent record) only publish
  // a local state; the legacy controller writes it with an empty message.
  co_await SetLocalReview(diff_id, normalized_state, "");
}

huxerui::Task<void> DiffReviewService::RejectWithRevert(std::string diff_id) {
  // The legacy controller resolves the record first so it can name the file in
  // the success message even when the revert itself has nothing left to do.
  const auto target = co_await diffs_.Find(diff_id);
  const auto guard = co_await diffs_.CheckRevert(diff_id);
  if (!guard.success) {
    // The guard message is the user-visible explanation, e.g. a later change to
    // the same file is still applied.
    co_await SetLocalReview(diff_id, "", guard.message);
    co_return;
  }
  if (guard.record) {
    // A restore failure short-circuits the revert; the record stays applied and
    // the message mirrors the legacy `"File restore failed: " + what`.
    std::string failure;
    try {
      const auto restored = co_await restore_.RestoreOldContent(*guard.record);
      if (!restored.success)
        throw std::runtime_error(restored.message);
      co_await diffs_.MarkReverted(diff_id);
    } catch (const std::exception &error) {
      failure = "File restore failed: " + std::string{error.what()};
    }
    if (!failure.empty()) {
      co_await SetLocalReview(diff_id, "", std::move(failure));
      co_return;
    }
  }

  const std::string file_path = target ? target->file_path : std::string{};
  co_await SetLocalReview(diff_id, "rejected", "Reverted change to " + file_path);
}

huxerui::Task<std::optional<domain::DiffRecord>>
DiffReviewService::CachedReview(std::string_view diff_id) {
  if (diff_id.empty())
    co_return std::nullopt;
  std::optional<domain::DiffRecord> record;
  if (const auto found = cache_.find(diff_id); found != cache_.end()) {
    record = found->second;
  } else {
    record = co_await diffs_.Find(std::string{diff_id});
    cache_.insert_or_assign(std::string{diff_id}, record);
  }
  if (!record)
    co_return std::nullopt;
  // A pending record without state or message is not worth surfacing.
  if (record->EffectiveReviewState().empty() && record->review_message.empty())
    co_return std::nullopt;
  co_return record;
}

huxerui::Task<void> DiffReviewService::SetLocalReview(std::string_view diff_id,
                                                      std::string state,
                                                      std::string message) {
  if (diff_id.empty())
    co_return;
  const std::string id{diff_id};
  co_await diffs_.SetReview(id, state, message);
  // The write invalidates whatever the cache held; repopulating it with the
  // committed row keeps the overlay consistent and lets the next render answer
  // from the cache instead of querying again.
  cache_.insert_or_assign(id, co_await diffs_.Find(id));
}

} // namespace linecode::application
