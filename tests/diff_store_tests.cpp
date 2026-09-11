#include <algorithm>
#include <cassert>
#include <chrono>
#include <filesystem>
#include <fstream>
#include <iostream>
#include <memory>
#include <set>
#include <string>
#include <system_error>
#include <thread>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "domain/diff_record.h"
#include "infrastructure/sqlite_diff_file_restorer.h"
#include "infrastructure/sqlite_diff_store.h"

namespace {

using linecode::domain::DiffRecord;
using linecode::infrastructure::SqliteDiffFileRestorer;
using linecode::infrastructure::SqliteDiffStore;

// The store awaits HuxerUI's asynchronous SQLite adapter, so every scenario
// runs as a task inside a real Runtime driven by the UI test fixture.
struct StoreScenario final {
  std::shared_ptr<SqliteDiffStore> store;
  bool done{};
  bool passed{};
};

std::shared_ptr<StoreScenario> scenario;

// `Long.toString(value, 36)` renders the magnitude in lowercase digits and
// keeps the sign that Java's `Math.abs(Long.MIN_VALUE)` leaves in place.
bool IsJavaBase36(const std::string &text) {
  if (text.empty())
    return false;
  const bool negative = text.front() == '-';
  const std::string_view digits =
      negative ? std::string_view{text}.substr(1) : std::string_view{text};
  return !digits.empty() &&
         std::ranges::all_of(digits, [](const char character) {
           return (character >= '0' && character <= '9') ||
                  (character >= 'a' && character <= 'z');
         });
}

std::int64_t NowMilliseconds() {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

huxerui::View StoreProbe() {
  const auto current = scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      const std::string first_path = "/tmp/linecode-diff-store/one.txt";
      const std::string second_path = "/tmp/linecode-diff-store/two.txt";

      // An empty identifier, an unknown identifier, and an empty file path all
      // read as "nothing recorded".
      const auto missing_empty = co_await current->store->Find("");
      const auto missing_unknown = co_await current->store->Find("does-not-exist");
      const auto empty_chain = co_await current->store->Chain("");

      // Record round trip, the legacy identifier shape, and the nullable legacy
      // columns (`old_content`/`raw_json`/`new_content` may all be empty).
      const auto recorded_at = NowMilliseconds();
      auto first = co_await current->store->Record(first_path, "", "", false);

      const auto separator = first.id.find('_');
      const bool identifier_ok =
          separator != std::string::npos && separator > 0 &&
          separator + 1 < first.id.size() &&
          IsJavaBase36(first.id.substr(separator + 1)) &&
          first.timestamp >= recorded_at && first.timestamp <= NowMilliseconds();

      const auto found = co_await current->store->Find(first.id);
      const DiffRecord fallback{};
      const auto loaded = found.value_or(fallback);
      const bool round_trip =
          found && loaded.id == first.id && loaded.file_path == first_path &&
          loaded.old_content.empty() && loaded.new_content.empty() &&
          !loaded.old_exists && !loaded.reverted &&
          loaded.timestamp == first.timestamp && loaded.review_state.empty() &&
          loaded.review_message.empty();

      // Separate the timestamps so the `ORDER BY timestamp ASC` chain has an
      // unambiguous order even when records are created within one millisecond.
      std::this_thread::sleep_for(std::chrono::milliseconds{2});
      auto second = co_await current->store->Record(second_path, "old", "new", true);
      std::this_thread::sleep_for(std::chrono::milliseconds{2});
      auto third = co_await current->store->Record(second_path, "old2", "new2", false);

      // Identifiers stay unique across rapid records.
      std::set<std::string> identifiers{first.id, second.id, third.id};
      bool unique = true;
      const std::string extra_path = "/tmp/linecode-diff-store/extra.txt";
      for (int index = 0; index < 12; ++index) {
        const auto extra =
            co_await current->store->Record(extra_path, "old", "new", true);
        unique = unique && identifiers.insert(extra.id).second;
      }

      const auto chain = co_await current->store->Chain(second_path);
      const auto other_chain = co_await current->store->Chain(first_path);
      const auto extra_chain = co_await current->store->Chain(extra_path);
      const bool chain_sorted =
          std::ranges::is_sorted(chain, {}, &DiffRecord::timestamp) &&
          chain.size() == 2 && other_chain.size() == 1 &&
          extra_chain.size() == 12 && other_chain.front().id == first.id &&
          chain.front().id == second.id && chain[1].id == third.id &&
          chain.front().timestamp < chain[1].timestamp &&
          chain.front().old_content == "old" && chain[1].old_content == "old2" &&
          chain.front().old_exists && !chain[1].old_exists;

      // Review state travels through `raw_json`; quotes, backslashes, and
      // control characters must survive the JSON round trip.
      const std::string message = "Reverted \"quoted\"\\ change\nnext\tline";
      co_await current->store->SetReview(first.id, "accepted", message);
      const auto reviewed = co_await current->store->Find(first.id);
      const bool review_round_trip =
          reviewed && reviewed->review_state == "accepted" &&
          reviewed->review_message == message && !reviewed->reverted;

      // Empty identifiers are ignored instead of matching an arbitrary row.
      co_await current->store->SetReview("", "rejected", "ignored");
      co_await current->store->MarkReverted("");
      const auto after_noop = co_await current->store->Chain(second_path);
      const bool empty_id_noop =
          after_noop.size() == chain.size() &&
          std::ranges::none_of(after_noop, &DiffRecord::reverted);

      // Revert guard: unknown identifier, a later un-reverted change, readiness,
      // already reverted, and readiness once the later change is reverted too.
      const auto unknown = co_await current->store->CheckRevert("unknown");
      const auto blocked = co_await current->store->CheckRevert(second.id);
      const auto ready = co_await current->store->CheckRevert(first.id);
      co_await current->store->MarkReverted(first.id);
      const auto reverted_again = co_await current->store->CheckRevert(first.id);
      co_await current->store->MarkReverted(third.id);
      const auto ready_after_last = co_await current->store->CheckRevert(second.id);
      const auto reverted_first = co_await current->store->CheckRevert(first.id);

      const bool guard_ok =
          !unknown.success &&
          unknown.message == "Specified diff record not found" && !unknown.record &&
          !blocked.success &&
          blocked.message ==
              "Please revert subsequent changes to this file first" &&
          !blocked.record && ready.success && ready.message == "Ready to revert" &&
          ready.record && ready.record->id == first.id &&
          ready.record->file_path == first_path && reverted_again.success &&
          reverted_again.message == "This change has been reverted" &&
          !reverted_again.record && ready_after_last.success &&
          ready_after_last.message == "Ready to revert" && ready_after_last.record &&
          ready_after_last.record->id == second.id && reverted_first.success &&
          reverted_first.message == "This change has been reverted";

      current->passed = !missing_empty && !missing_unknown && empty_chain.empty() &&
                        identifier_ok && round_trip && unique && chain_sorted &&
                        review_round_trip && empty_id_noop && guard_ok;
      if (!current->passed) {
        std::cerr << "store check failed: missing=" << !missing_empty << "/"
                  << !missing_unknown << " emptychain=" << empty_chain.empty()
                  << " id=" << identifier_ok << " roundtrip=" << round_trip
                  << " unique=" << unique << " chain=" << chain_sorted
                  << " review=" << review_round_trip
                  << " noop=" << empty_id_noop << " guard=" << guard_ok << "\n";
      }
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("sqlite-diff-store-probe");
}

void AdapterReplicatesLegacyDiffRepository() {
  const auto nonce = std::chrono::steady_clock::now().time_since_epoch().count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-diff-store-" + std::to_string(nonce));
  const auto database = temporary / "linecode.db";

  scenario = std::make_shared<StoreScenario>();
  scenario->store = std::make_shared<SqliteDiffStore>(huxerui::File{database.string()});
  {
    const huxerui::Application app(StoreProbe, {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 4'000 && !scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    assert(scenario->done);
    assert(scenario->passed);
  }
  scenario.reset();
  std::error_code ignored;
  std::filesystem::remove_all(temporary, ignored);
}

// The real adapter writes through the HuxerUI file API, so its two branches and
// both legacy failure messages are exercised against a real temporary tree.
struct RestoreScenario final {
  std::shared_ptr<SqliteDiffFileRestorer> restorer;
  std::string root;
  bool done{};
  bool passed{};
  bool delete_ok{};
  bool delete_removed{};
  bool write_ok{};
  bool make_parents_ok{};
  bool delete_failure_ok{};
  bool parent_failure_ok{};
};

std::shared_ptr<RestoreScenario> restore_scenario;

DiffRecord RestoreRecord(std::string file_path, std::string content,
                         const bool old_exists) {
  return DiffRecord{
      .id = "restore-record",
      .file_path = std::move(file_path),
      .old_content = std::move(content),
      .new_content = "later",
      .old_exists = old_exists,
      .timestamp = 1,
      .reverted = false,
      .review_state = std::string{},
      .review_message = std::string{},
  };
}

huxerui::View RestoreProbe() {
  const auto current = restore_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      auto &restorer = *current->restorer;
      const huxerui::File created{current->root + "/created.txt"};
      const huxerui::File rewritten{current->root + "/sub/dir/rewritten.txt"};
      // `blocker.txt` is a regular file, so this parent can never be created.
      const huxerui::File blocked_parent{
          current->root + "/blocker.txt/sub/child.txt"};

      // `old_exists == false`: the change created the file, so reverting deletes
      // it.
      const auto delete_result = co_await restorer.RestoreOldContent(
          RestoreRecord(created.Path(), "", false));
      current->delete_ok = delete_result.success;
      current->delete_removed = !created.Exists();

      // `old_exists == true`: the old content is written back and missing parent
      // directories are created first.
      const auto write_result = co_await restorer.RestoreOldContent(
          RestoreRecord(rewritten.Path(), "restored contents", true));
      const auto reread = rewritten.ReadString();
      current->write_ok = write_result.success && reread.Succeeded() &&
                          reread.Value() == "restored contents";
      current->make_parents_ok = huxerui::File{current->root + "/sub"}
                                     .IsDirectory() &&
                                 huxerui::File{current->root + "/sub/dir"}
                                     .IsDirectory();

      // A refused deletion keeps the entry and reports the legacy message.
      // The directory is non-empty because both Java's File.delete() and
      // HuxerUI's Delete() succeed on an empty directory.
      const huxerui::File protected_file{current->root + "/protected.txt"};
      if (!protected_file.IsDirectory())
        (void)protected_file.CreateDirectories();
      {
        std::ofstream child{protected_file.Path() + "/occupied.txt"};
        child << "keeps the directory from being deleted";
      }
      const auto refused_delete = co_await restorer.RestoreOldContent(
          RestoreRecord(protected_file.Path(), "", false));
      current->delete_failure_ok =
          !refused_delete.success &&
          refused_delete.message ==
              "Cannot delete file: " + protected_file.Path();

      // A parent that is a regular file cannot be created, so the write fails
      // with the legacy parent message.
      const auto refused_parent = co_await restorer.RestoreOldContent(
          RestoreRecord(blocked_parent.Path(), "unreachable", true));
      const auto blocked_parent_directory = blocked_parent.Parent();
      current->parent_failure_ok =
          !refused_parent.success && blocked_parent_directory.has_value() &&
          refused_parent.message == "Cannot create parent directory: " +
                                        blocked_parent_directory->Path();

      current->passed = current->delete_ok && current->delete_removed &&
                        current->write_ok && current->make_parents_ok &&
                        current->delete_failure_ok && current->parent_failure_ok;
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("diff-file-restorer-probe");
}

void HuxerUiFileRestorerMatchesLegacyFileRestorer() {
  const auto nonce = std::chrono::steady_clock::now().time_since_epoch().count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-diff-restore-" + std::to_string(nonce));
  const auto database = temporary / "linecode.db";
  std::error_code ignored;
  std::filesystem::create_directories(temporary, ignored);
  // A regular file where a parent directory would have to be created.
  {
    std::ofstream blocker{temporary / "blocker.txt"};
    blocker << "not a directory";
  }

  restore_scenario = std::make_shared<RestoreScenario>();
  restore_scenario->restorer = std::make_shared<SqliteDiffFileRestorer>();
  restore_scenario->root = temporary.string();
  {
    const huxerui::Application app(RestoreProbe, {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 2'000 && !restore_scenario->done; ++attempt) {
      ui.Pump(std::chrono::milliseconds{1});
      std::this_thread::sleep_for(std::chrono::milliseconds{1});
    }
    assert(restore_scenario->done);
    if (!restore_scenario->passed) {
      std::cerr << "restore check failed: delete=" << restore_scenario->delete_ok
                << "/" << restore_scenario->delete_removed
                << " write=" << restore_scenario->write_ok
                << " parents=" << restore_scenario->make_parents_ok
                << " delete_msg=" << restore_scenario->delete_failure_ok
                << " parent_msg=" << restore_scenario->parent_failure_ok << "\n";
    }
    assert(restore_scenario->passed);
  }
  restore_scenario.reset();
  std::filesystem::remove_all(temporary, ignored);
  (void)database;
}

} // namespace

int main() {
  AdapterReplicatesLegacyDiffRepository();
  HuxerUiFileRestorerMatchesLegacyFileRestorer();
  std::cout << "diff store tests passed\n";
}
