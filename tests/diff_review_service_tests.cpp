#include <algorithm>
#include <cassert>
#include <chrono>
#include <iostream>
#include <memory>
#include <optional>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/diff_review_service.h"
#include "application/ports/diff_file_restore.h"
#include "application/ports/diff_store.h"
#include "domain/diff_record.h"

namespace {

using linecode::application::DiffFileRestore;
using linecode::application::DiffRestoreResult;
using linecode::application::DiffReviewService;
using linecode::application::DiffRevertResult;
using linecode::application::DiffStore;
using linecode::domain::DiffRecord;

// In-memory stand-in for `SqliteDiffStore`: the review service only needs the
// legacy guard semantics plus a visible call log. One vector is authoritative so
// `MarkReverted`/`SetReview` are observable through every read path.
class FakeDiffStore final : public DiffStore {
public:
  [[nodiscard]] huxerui::Task<DiffRecord>
  Record(std::string file_path, std::string old_content, std::string new_content,
         bool old_exists) override {
    DiffRecord record{
        .id = "generated-" + std::to_string(records.size()),
        .file_path = std::move(file_path),
        .old_content = std::move(old_content),
        .new_content = std::move(new_content),
        .old_exists = old_exists,
        .timestamp = static_cast<std::int64_t>(records.size()) + 1,
        .reverted = false,
        .review_state = std::string{},
        .review_message = std::string{},
    };
    records.push_back(record);
    co_return record;
  }

  [[nodiscard]] huxerui::Task<std::optional<DiffRecord>>
  Find(std::string diff_id) override {
    ++find_calls;
    if (diff_id.empty())
      co_return std::nullopt;
    const auto *found = find(diff_id);
    if (found == nullptr)
      co_return std::nullopt;
    co_return *found;
  }

  [[nodiscard]] huxerui::Task<std::vector<DiffRecord>>
  Chain(std::string file_path) override {
    ++chain_calls;
    co_return chain_for(file_path);
  }

  [[nodiscard]] huxerui::Task<DiffRevertResult>
  CheckRevert(std::string diff_id) override {
    ++check_calls;
    const auto *found = find(diff_id);
    if (diff_id.empty() || found == nullptr)
      co_return DiffRevertResult{.success = false,
                                  .message = "Specified diff record not found",
                                  .record = std::nullopt};
    if (found->reverted) {
      co_return DiffRevertResult{.success = true,
                                  .message = "This change has been reverted",
                                  .record = std::nullopt};
    }
    bool saw_target = false;
    for (const auto &candidate : chain_for(found->file_path)) {
      if (saw_target && !candidate.reverted) {
        co_return DiffRevertResult{
            .success = false,
            .message = "Please revert subsequent changes to this file first",
            .record = std::nullopt};
      }
      if (candidate.id == found->id)
        saw_target = true;
    }
    co_return DiffRevertResult{.success = true,
                                .message = "Ready to revert",
                                .record = *found};
  }

  [[nodiscard]] huxerui::Task<void> MarkReverted(std::string diff_id) override {
    ++mark_calls;
    if (auto *found = find(diff_id); found != nullptr)
      found->reverted = true;
    co_return;
  }

  [[nodiscard]] huxerui::Task<void> SetReview(std::string diff_id,
                                              std::string state,
                                              std::string message) override {
    ++set_review_calls;
    if (diff_id.empty())
      co_return;
    last_reviewed_id = diff_id;
    last_state = state;
    last_message = message;
    if (auto *found = find(diff_id); found != nullptr) {
      found->review_state = state;
      found->review_message = message;
    }
    co_return;
  }

  void Add(DiffRecord record) { records.push_back(std::move(record)); }

  [[nodiscard]] DiffRecord *find(std::string_view diff_id) {
    const auto found = std::ranges::find(records, diff_id, &DiffRecord::id);
    return found == records.end() ? nullptr : &*found;
  }

  [[nodiscard]] std::vector<DiffRecord> chain_for(std::string_view file_path) {
    std::vector<DiffRecord> chain;
    for (const auto &record : records) {
      if (!file_path.empty() && record.file_path == file_path)
        chain.push_back(record);
    }
    std::ranges::sort(chain, {}, &DiffRecord::timestamp);
    return chain;
  }

  std::vector<DiffRecord> records;
  int find_calls{};
  int chain_calls{};
  int check_calls{};
  int mark_calls{};
  int set_review_calls{};
  std::string last_reviewed_id;
  std::string last_state;
  std::string last_message;
};

// Records what the service asked to restore and can simulate either failure
// message of the legacy `FileRestorer`.
class FakeFileRestore final : public DiffFileRestore {
public:
  [[nodiscard]] huxerui::Task<DiffRestoreResult>
  RestoreOldContent(DiffRecord record) override {
    ++calls;
    restored.push_back(std::move(record));
    co_return result;
  }

  DiffRestoreResult result{.success = true, .message = std::string{}};
  std::vector<DiffRecord> restored;
  int calls{};
};

struct ReviewScenario final {
  std::shared_ptr<FakeDiffStore> store;
  std::shared_ptr<FakeFileRestore> restorer;
  std::shared_ptr<DiffReviewService> service;
  bool done{};
  bool passed{};
};

std::shared_ptr<ReviewScenario> scenario;

DiffRecord MakeRecord(std::string id, std::string file_path,
                      const std::int64_t timestamp, const bool old_exists) {
  return DiffRecord{
      .id = std::move(id),
      .file_path = std::move(file_path),
      .old_content = "before",
      .new_content = "after",
      .old_exists = old_exists,
      .timestamp = timestamp,
      .reverted = false,
      .review_state = std::string{},
      .review_message = std::string{},
  };
}

huxerui::View ReviewProbe() {
  const auto current = scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      auto &store = *current->store;
      auto &restorer = *current->restorer;
      auto &service = *current->service;
      bool ok = true;

      // 1. A rejection with an empty tool call identifier is ignored entirely.
      store.Add(MakeRecord("kept", "/projects/kept.txt", 1, true));
      store.Add(MakeRecord("pending", "/projects/a.txt", 10, true));
      store.Add(MakeRecord("later", "/projects/a.txt", 20, true));
      store.Add(MakeRecord("second", "/projects/second.txt", 30, true));

      const auto set_reviews_before = store.set_review_calls;
      co_await service.Review("", "rejected", "pending");
      if (store.set_review_calls != set_reviews_before || restorer.calls != 0 ||
          store.mark_calls != 0) {
        std::cerr << "check 1 failed\n";
        ok = false;
      }

      // 2. State normalization: only the exact legacy token rejects, and an
      // accepted change is recorded without touching the file.
      co_await service.Review("call-1", "Rejected", "kept");
      if (store.last_state != "accepted" || !store.last_message.empty() ||
          restorer.calls != 0) {
        std::cerr << "check 2 failed: state='" << store.last_state << "'\n";
        ok = false;
      }
      co_await service.Review("call-2", "", "kept");
      if (store.last_state != "accepted" || restorer.calls != 0) {
        std::cerr << "check 3 failed\n";
        ok = false;
      }

      // 3. The exact "rejected" token reverts: the file is restored, the record
      // is marked, and the message names the file rather than the identifier.
      co_await service.Review("call-3", "rejected", "kept");
      if (store.last_state != "rejected" || restorer.calls != 1 ||
          restorer.restored.back().id != "kept" || store.mark_calls != 1 ||
          !store.find("kept")->reverted ||
          store.find("kept")->review_state != "rejected" ||
          store.last_message != "Reverted change to /projects/kept.txt") {
        std::cerr << "check 4 failed: restore=" << restorer.calls
                  << " mark=" << store.mark_calls << " msg='" << store.last_message
                  << "'\n";
        ok = false;
      }

      // 4. A rejection blocked by a later un-reverted change publishes the guard
      // message verbatim, clears the state, and never touches the file.
      const auto restore_calls_before = restorer.calls;
      const auto mark_calls_before = store.mark_calls;
      co_await service.RejectWithRevert("pending");
      if (store.last_reviewed_id != "pending" || !store.last_state.empty() ||
          store.last_message !=
              "Please revert subsequent changes to this file first" ||
          restorer.calls != restore_calls_before ||
          store.mark_calls != mark_calls_before ||
          store.find("pending")->reverted) {
        std::cerr << "check 5 failed: msg='" << store.last_message << "'\n";
        ok = false;
      }

      // 5. An unknown identifier reports the legacy "not found" message.
      co_await service.RejectWithRevert("missing");
      if (!store.last_state.empty() ||
          store.last_message != "Specified diff record not found" ||
          restorer.calls != restore_calls_before) {
        std::cerr << "check 6 failed: msg='" << store.last_message << "'\n";
        ok = false;
      }

      // 6. Reverting a different record through `RejectWithRevert` restores its
      // file and marks it.
      co_await service.RejectWithRevert("second");
      if (restorer.calls != restore_calls_before + 1 ||
          restorer.restored.back().id != "second" ||
          !restorer.restored.back().old_exists || store.mark_calls != 2 ||
          !store.find("second")->reverted || store.last_state != "rejected" ||
          store.last_message != "Reverted change to /projects/second.txt") {
        std::cerr << "check 7 failed: restore=" << restorer.calls
                  << " mark=" << store.mark_calls << " msg='" << store.last_message
                  << "'\n";
        ok = false;
      }

      // 7. A refused restore reports `"File restore failed: " + message`, stays
      // un-reverted, and keeps the record's state empty.
      store.Add(MakeRecord("denied", "/projects/denied.txt", 40, false));
      restorer.result = {.success = false,
                         .message = "Cannot delete file: /projects/denied.txt"};
      co_await service.RejectWithRevert("denied");
      if (!store.last_state.empty() ||
          store.last_message !=
              "File restore failed: Cannot delete file: /projects/denied.txt" ||
          store.find("denied")->reverted || store.mark_calls != 2) {
        std::cerr << "check 8 failed: msg='" << store.last_message << "'\n";
        ok = false;
      }

      // 8. Both restore paths hand the resolved record to the port: deletion for
      // a created file, rewrite for an existing one.
      restorer.result = {.success = true, .message = std::string{}};
      co_await service.RejectWithRevert("denied");
      if (restorer.restored.back().id != "denied" ||
          restorer.restored.back().old_exists || store.mark_calls != 3 ||
          !store.find("denied")->reverted) {
        std::cerr << "check 9 failed\n";
        ok = false;
      }
      store.Add(MakeRecord("rewritten", "/projects/sub/new.txt", 50, true));
      co_await service.RejectWithRevert("rewritten");
      if (restorer.restored.back().id != "rewritten" ||
          !restorer.restored.back().old_exists ||
          restorer.restored.back().old_content != "before" ||
          !store.find("rewritten")->reverted) {
        std::cerr << "check 10 failed\n";
        ok = false;
      }

      // 9. Already-reverted records stay a success and skip the file restore;
      // the legacy controller still reports the resolved file path.
      const auto reverted_restores = restorer.calls;
      const auto reverted_marks = store.mark_calls;
      co_await service.RejectWithRevert("kept");
      if (restorer.calls != reverted_restores ||
          store.mark_calls != reverted_marks ||
          store.last_state != "rejected" ||
          store.last_message != "Reverted change to /projects/kept.txt") {
        std::cerr << "check 11 failed\n";
        ok = false;
      }

      // 10. The local cache answers before the store: a write repopulates it, an
      // unknown identifier is remembered as absent, and a fresh write
      // invalidates the entry so the next read sees the committed state.
      store.Add(MakeRecord(std::string{"cached"},
                           std::string{"/projects/cached.txt"}, 60, true));
      co_await service.RejectWithRevert("cached");
      store.find_calls = 0;
      const auto cached_hit = co_await service.CachedReview("cached");
      if (!cached_hit || cached_hit->id != "cached" ||
          cached_hit->review_state != "rejected" ||
          cached_hit->review_message != "Reverted change to /projects/cached.txt" ||
          store.find_calls != 0) {
        std::cerr << "check 12 failed: finds=" << store.find_calls << "\n";
        ok = false;
      }
      const auto blocked_local = co_await service.CachedReview("pending");
      const auto absent_first = co_await service.CachedReview("nowhere");
      const auto finds_after_misses = store.find_calls;
      const auto absent_second = co_await service.CachedReview("nowhere");
      // The blocked record is cached with the legacy shape: no explicit state,
      // the guard message, and `reverted == 0` (so no synthetic "rejected").
      if (!blocked_local || !blocked_local->review_state.empty() ||
          !blocked_local->EffectiveReviewState().empty() ||
          blocked_local->reverted ||
          blocked_local->review_message !=
              "Please revert subsequent changes to this file first" ||
          absent_first || absent_second || finds_after_misses != 1 ||
          store.find_calls != finds_after_misses) {
        std::cerr << "check 13 failed: finds=" << store.find_calls << "\n";
        ok = false;
      }
      co_await service.Review("call-9", "accepted", "cached");
      const auto refreshed = co_await service.CachedReview("cached");
      if (!refreshed || refreshed->EffectiveReviewState() != "accepted" ||
          !refreshed->review_message.empty() || store.find_calls != 2) {
        std::cerr << "check 14 failed: finds=" << store.find_calls << "\n";
        ok = false;
      }

      current->passed = ok;
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("diff-review-service-probe");
}

void ReviewServiceMirrorsLegacyToolReviewController() {
  scenario = std::make_shared<ReviewScenario>();
  scenario->store = std::make_shared<FakeDiffStore>();
  scenario->restorer = std::make_shared<FakeFileRestore>();
  scenario->service = std::make_shared<DiffReviewService>(*scenario->store,
                                                         *scenario->restorer);
  {
    const huxerui::Application app(ReviewProbe, {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 2'000 && !scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    assert(scenario->done);
    assert(scenario->passed);
  }
  scenario.reset();
}

} // namespace

int main() {
  ReviewServiceMirrorsLegacyToolReviewController();
  std::cout << "diff review service tests passed\n";
}
