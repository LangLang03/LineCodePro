#include <cassert>
#include <chrono>
#include <filesystem>
#include <iostream>
#include <memory>
#include <string>
#include <thread>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "infrastructure/sqlite_skill_record_store.h"

namespace {

using linecode::domain::SkillLocation;
using linecode::domain::SkillRecord;

struct StoreScenario final {
  std::shared_ptr<linecode::infrastructure::SqliteSkillRecordStore> store;
  bool done{};
  bool passed{};
};

std::shared_ptr<StoreScenario> scenario;

huxerui::View StoreProbe() {
  const auto current = scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([current, tasks] {
    const auto handle = tasks.Launch([current]() -> huxerui::Task<void> {
      SkillRecord discovered{
          .id = "app:/skills/pdf/SKILL.md",
          .name = "PDF Helper",
          .description = "first",
          .root_path = "/skills/pdf",
          .skill_markdown_path = "/skills/pdf/SKILL.md",
          .location = SkillLocation::app,
          .enabled = true,
          .discovered_at = 10,
          .updated_at = 20,
      };
      const auto inserted =
          co_await current->store->UpsertDiscovered({discovered});
      const auto disabled = co_await current->store->SetEnabled(
          discovered.id, false);

      discovered.name = "PDF Helper Updated";
      discovered.description = "second";
      discovered.enabled = true;
      discovered.discovered_at = 30;
      discovered.updated_at = 40;
      const auto refreshed =
          co_await current->store->UpsertDiscovered({discovered});
      const auto listed = co_await current->store->List();
      current->passed = inserted && disabled && refreshed && listed &&
                        listed->size() == 1 &&
                        listed->front().name == "PDF Helper Updated" &&
                        listed->front().description == "second" &&
                        !listed->front().enabled &&
                        listed->front().location == SkillLocation::app &&
                        listed->front().skill_markdown_path ==
                            "/skills/pdf/SKILL.md" &&
                        listed->front().discovered_at == 30 &&
                        listed->front().updated_at == 40;

      const auto deleted =
          co_await current->store->Delete({discovered.id});
      const auto empty = co_await current->store->List();
      current->passed = current->passed && deleted && empty && empty->empty();
      current->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("sqlite-skill-record-store-probe");
}

void AdapterPreservesEnabledStateAcrossDiscovery() {
  const auto nonce = std::chrono::steady_clock::now()
                         .time_since_epoch()
                         .count();
  const auto temporary = std::filesystem::temp_directory_path() /
                         ("linecode-skill-store-" + std::to_string(nonce));
  const auto database = temporary / "linecode.db";

  scenario = std::make_shared<StoreScenario>();
  scenario->store =
      std::make_shared<linecode::infrastructure::SqliteSkillRecordStore>(
          huxerui::File{database.string()});
  {
    const huxerui::Application app(StoreProbe,
                                   {.show_debug_overlay = false});
    huxerui::testing::UiTest ui(app);
    for (int attempt = 0; attempt < 2'000 && !scenario->done; ++attempt) {
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

} // namespace

int main() {
  AdapterPreservesEnabledStateAcrossDiscovery();
  std::cout << "sqlite skill record store tests passed\n";
}
