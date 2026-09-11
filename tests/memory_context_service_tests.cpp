#include <cassert>
#include <memory>
#include <optional>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/memory_context_service.h"
#include "application/memory_prompt_renderer.h"
#include "application/ports/memory_store.h"
#include "domain/memory_rag.h"

namespace {

using linecode::application::LegacyMemoryPromptRenderer;
using linecode::application::MemoryContextService;
using linecode::application::MemoryRetrievalCorpus;
using linecode::application::MemoryStore;
using linecode::application::MemoryStoreResult;
using linecode::application::PreparedMemoryContext;
using linecode::domain::ConversationIndexRecord;
using linecode::domain::ExplicitMemoryExtractionPolicy;
using linecode::domain::MemoryConversationTurn;
using linecode::domain::MemoryOverview;
using linecode::domain::MemoryRecord;

class ProbeMemoryStore final : public MemoryStore {
public:
  huxerui::Task<MemoryStoreResult<MemoryOverview>>
  LoadOverview(std::string) override {
    co_return MemoryOverview{};
  }

  huxerui::Task<MemoryStoreResult<MemoryRecord>>
  SaveManual(MemoryRecord memory) override {
    co_return std::move(memory);
  }

  huxerui::Task<MemoryStoreResult<void>>
  Delete(std::vector<std::string>) override {
    co_return MemoryStoreResult<void>{};
  }

  huxerui::Task<MemoryStoreResult<MemoryRetrievalCorpus>>
  LoadRetrievalCorpus(std::string project_id,
                      std::string exclude_conversation_id) override {
    events.emplace_back("load-retrieval");
    retrieval_projects.push_back(std::move(project_id));
    excluded_conversations.push_back(std::move(exclude_conversation_id));
    co_return retrieval_corpus;
  }

  huxerui::Task<MemoryStoreResult<std::vector<MemoryRecord>>>
  LoadManualMemories(std::string project_id) override {
    events.emplace_back("load-manual");
    manual_projects.push_back(std::move(project_id));
    co_return manual_memories;
  }

  huxerui::Task<MemoryStoreResult<void>>
  MarkUsed(std::vector<std::string> ids) override {
    events.emplace_back("mark-used");
    marked_ids.push_back(std::move(ids));
    co_return MemoryStoreResult<void>{};
  }

  huxerui::Task<MemoryStoreResult<MemoryRecord>>
  SaveExtracted(MemoryRecord memory) override {
    events.emplace_back("save-extracted");
    extracted.push_back(memory);
    co_return std::move(memory);
  }

  huxerui::Task<MemoryStoreResult<void>>
  IndexConversationTurn(MemoryConversationTurn turn) override {
    events.emplace_back("index-turn");
    indexed_turns.push_back(std::move(turn));
    co_return MemoryStoreResult<void>{};
  }

  MemoryRetrievalCorpus retrieval_corpus;
  std::vector<MemoryRecord> manual_memories;
  std::vector<std::string> events;
  std::vector<std::string> retrieval_projects;
  std::vector<std::string> excluded_conversations;
  std::vector<std::string> manual_projects;
  std::vector<std::vector<std::string>> marked_ids;
  std::vector<MemoryRecord> extracted;
  std::vector<MemoryConversationTurn> indexed_turns;
};

struct Scenario final {
  std::shared_ptr<ProbeMemoryStore> store;
  std::shared_ptr<MemoryContextService> service;
  std::optional<MemoryStoreResult<PreparedMemoryContext>> learning_context;
  std::optional<MemoryStoreResult<void>> learning_commit;
  std::optional<MemoryStoreResult<PreparedMemoryContext>> manual_context;
  std::optional<MemoryStoreResult<void>> disabled_commit;
  bool done{};
};

std::shared_ptr<Scenario> active_scenario;

huxerui::View MemoryContextProbe() {
  const auto scenario = active_scenario;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      scenario->learning_context = co_await scenario->service->Prepare(
          "project-alpha", "C++23 SQLite", "conversation-current", true);
      scenario->learning_commit = co_await scenario->service->CommitTurn(
          true,
          MemoryConversationTurn{
              .project_id = "project-alpha",
              .conversation_id = "conversation-current",
              .title = "Memory contract",
              .messages = {
                  {.id = "user-1",
                   .role = "user",
                   .content = "记住项目：使用 C++23",
                   .timestamp = 100},
                  {.id = "assistant-1",
                   .role = "assistant",
                   .content = "已记录",
                   .timestamp = 101},
              },
              .updated_at = 102,
          },
          "记住项目：使用 C++23");
      scenario->manual_context = co_await scenario->service->Prepare(
          "project-alpha", "ignored while learning is disabled",
          "conversation-current", false);
      scenario->disabled_commit = co_await scenario->service->CommitTurn(
          false,
          MemoryConversationTurn{
              .project_id = "project-alpha",
              .conversation_id = "conversation-current",
              .title = {},
              .messages = {},
          },
          "记住项目：不应自动保存");
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("memory-context-probe");
}

void AssertMemoryContextContract(const Scenario &scenario) {
  assert(scenario.done);
  assert(scenario.learning_context.has_value());
  assert(scenario.learning_context->has_value());
  assert((*scenario.learning_context)->learning_enabled);
  assert((*scenario.learning_context)->prompt.contains("C++23"));
  assert((*scenario.learning_context)->prompt.contains("SQLite"));
  assert((*scenario.learning_context)->prompt.contains("native-cpp"));

  assert(scenario.learning_commit.has_value());
  assert(scenario.learning_commit->has_value());
  assert(scenario.manual_context.has_value());
  assert(scenario.manual_context->has_value());
  assert(!(*scenario.manual_context)->learning_enabled);
  assert((*scenario.manual_context)->prompt.contains("手工保存"));
  assert((*scenario.manual_context)->prompt.contains("始终使用中文回复"));
  assert(scenario.disabled_commit.has_value());
  assert(scenario.disabled_commit->has_value());

  const auto &store = *scenario.store;
  assert(store.retrieval_projects ==
         std::vector<std::string>{"project-alpha"});
  assert(store.excluded_conversations ==
         std::vector<std::string>{"conversation-current"});
  assert(store.manual_projects == std::vector<std::string>{"project-alpha"});
  assert(store.marked_ids ==
         std::vector<std::vector<std::string>>{{"memory-cpp23"}});

  assert(store.indexed_turns.size() == 1U);
  assert(store.indexed_turns.front().messages.size() == 2U);
  assert(store.indexed_turns.front().messages.back().role == "assistant");
  assert(store.extracted.size() == 1U);
  assert(store.extracted.front().scope ==
         linecode::domain::MemoryScope::project);
  assert(store.extracted.front().project_id == "project-alpha");
  assert(store.extracted.front().content == "使用 C++23");

  assert(store.events ==
         std::vector<std::string>{"load-retrieval", "mark-used", "index-turn",
                                  "save-extracted", "load-manual"});
}

} // namespace

int main() {
  auto store = std::make_shared<ProbeMemoryStore>();
  store->retrieval_corpus.memories.push_back(MemoryRecord{
      .id = "memory-cpp23",
      .scope = linecode::domain::MemoryScope::project,
      .project_id = "project-alpha",
      .content = "此项目使用 C++23 和 SQLite",
      .source = "manual",
      .confidence = 1.0,
      .updated_at = 100,
  });
  store->retrieval_corpus.skills.push_back({
      .name = "native-cpp",
      .path = "/skills/native-cpp",
      .description = "C++23 application architecture",
      .updated_at = 100,
  });
  store->manual_memories.push_back(MemoryRecord{
      .id = "manual-language",
      .scope = linecode::domain::MemoryScope::user,
      .project_id = {},
      .content = "始终使用中文回复",
      .source = "manual",
      .confidence = 1.0,
      .updated_at = 100,
  });

  active_scenario = std::make_shared<Scenario>();
  active_scenario->store = store;
  active_scenario->service = std::make_shared<MemoryContextService>(
      store, std::make_shared<ExplicitMemoryExtractionPolicy>(),
      std::make_shared<LegacyMemoryPromptRenderer>());

  const huxerui::Application application(
      MemoryContextProbe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active_scenario->done; });
  AssertMemoryContextContract(*active_scenario);
  active_scenario.reset();
}
