#include "gtest_support.h"
#include <array>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/prompt_request_composer.h"
#include "application/file_content_hash.h"
#include "application/legacy_attachment_prompt_renderer.h"
#include "domain/context_usage.h"
#include "domain/prompt_renderer.h"

namespace {

using namespace linecode;

class MemorySettings final : public application::AsyncSettingsStore {
public:
  huxerui::Task<application::SettingsResult<std::string>>
  GetString(std::string key, std::string fallback) override {
    const auto found = values.find(key);
    co_return found == values.end() ? std::move(fallback) : found->second;
  }
  huxerui::Task<application::SettingsResult<bool>>
  GetBoolean(std::string key, bool fallback) override {
    const auto found = values.find(key);
    co_return found == values.end()
                  ? fallback
                  : application::ParseStoredBoolean(found->second);
  }
  huxerui::Task<application::SettingsResult<std::int64_t>>
  GetInteger(std::string key, std::int64_t fallback) override {
    const auto found = values.find(key);
    if (found == values.end())
      co_return fallback;
    const auto parsed = application::ParseStoredInteger(found->second);
    co_return parsed.value_or(fallback);
  }
  huxerui::Task<application::SettingsResult<void>>
  SetString(std::string key, std::string value) override {
    values.insert_or_assign(std::move(key), std::move(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetBoolean(std::string key, bool value) override {
    values.insert_or_assign(std::move(key), value ? "true" : "false");
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  SetInteger(std::string key, std::int64_t value) override {
    values.insert_or_assign(std::move(key), std::to_string(value));
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  Remove(std::string key) override {
    values.erase(key);
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<void>>
  ClearLineCodeSettings() override {
    values.clear();
    co_return application::SettingsResult<void>{};
  }
  huxerui::Task<application::SettingsResult<
      std::map<std::string, std::string, std::less<>>>>
  LineCodeSettings() override {
    co_return values;
  }

  std::map<std::string, std::string, std::less<>> values;
};

struct Scenario final {
  std::shared_ptr<MemorySettings> store;
  std::shared_ptr<application::PromptRequestComposer> composer;
  bool done{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      application::CompletionRequest request;
      request.model.name = "Test Name";
      request.model.protocol = domain::ModelProtocol::codex_responses;
      request.model.provider_label = "Test Provider";
      request.model.model_id = "test-model";
      request.messages.push_back(
          {.role = application::CompletionRole::user, .content = "hello"});
      request.tools.push_back({.name = "file_read",
                               .description = "Read a file",
                               .parameters_json = "{}"});

      application::PromptAssemblyContext context;
      context.chat_mode = application::PromptChatMode::plan;
      context.work_directory = "/workspace";
      context.linecode_root = "/private";
      context.global_skills_root = "/skills";
      context.workspace_private_root = "/workspace/.linecode";
      context.workspace_skills_root = "/workspace/.linecode/skills";
      context.learning_context = "LEARNING";
      context.todo_state = "1. [pending] verify";
      context.permission_mode = "confirm";
      context.attachment_history.push_back(domain::ChatMessage{
          .id = 7,
          .role = domain::MessageRole::user,
          .content = "inspect this",
          .attachments = {domain::InputAttachment{
              "source.cpp", "content://picked/source.cpp", "local"}},
      });
      auto composed = co_await scenario->composer->Compose(std::move(request),
                                                            std::move(context));
      EXPECT_EXPRESSION(composed);
      EXPECT_EXPRESSION(composed->messages.size() == 3U);
      EXPECT_EXPRESSION(composed->messages.front().role ==
             application::CompletionRole::system);
      const auto &prompt = composed->messages.front().content;
      EXPECT_EXPRESSION(prompt.starts_with("CUSTOM SYSTEM"));
      EXPECT_EXPRESSION(prompt.contains("CUSTOM CHAT TONE"));
      EXPECT_EXPRESSION(prompt.contains("Current mode: Plan"));
      EXPECT_EXPRESSION(prompt.contains("/workspace"));
      EXPECT_EXPRESSION(prompt.contains("test-model"));
      EXPECT_EXPRESSION(prompt.contains("Test Provider"));
      EXPECT_EXPRESSION(prompt.contains("Codex"));
      EXPECT_EXPRESSION(prompt.contains("Permission mode: confirmation"));
      // Names only: the schemas travel in the request's own `tools` field.
      EXPECT_EXPRESSION(prompt.contains("file_read"));
      EXPECT_EXPRESSION(!prompt.contains("file_read: Read a file"));
      EXPECT_EXPRESSION(prompt.contains("LEARNING"));
      // Plan mode cannot call todo_update (it is denied in read-only mode), so
      // the TODO section must not be rendered there.
      EXPECT_EXPRESSION(!prompt.contains("1. [pending] verify"));
      EXPECT_EXPRESSION(composed->reasoning_effort == domain::ReasoningEffort::maximum);
      EXPECT_EXPRESSION(composed->preserve_reasoning);
      EXPECT_EXPRESSION(composed->messages.back().role ==
             application::CompletionRole::user);
      EXPECT_EXPRESSION(composed->messages.back().content.contains("## 附加文件位置"));
      EXPECT_EXPRESSION(composed->messages.back().content.contains(
          "source.cpp (local): content://picked/source.cpp"));

      // Agent mode does render the live TODO list, because todo_update is
      // available there.
      application::CompletionRequest agent_request;
      agent_request.model.model_id = "test-model";
      agent_request.messages.push_back(
          {.role = application::CompletionRole::user, .content = "hello"});
      application::PromptAssemblyContext agent_context;
      agent_context.chat_mode = application::PromptChatMode::agent;
      agent_context.todo_state = "1. [pending] verify";
      agent_context.permission_mode = "auto";
      auto agent_composed = co_await scenario->composer->Compose(
          std::move(agent_request), std::move(agent_context));
      EXPECT_EXPRESSION(agent_composed);
      EXPECT_EXPRESSION(agent_composed->messages.front().content.contains(
          "1. [pending] verify"));
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("prompt-request-composer-probe");
}

// Guards the "repository defaults equal the struct defaults" invariant the
// settings screens assume, and round-trips every setter so a key rename cannot
// leave a switch that persists but never reads back.
struct BehaviorScenario final {
  std::shared_ptr<MemorySettings> store;
  bool done{};
};

std::shared_ptr<BehaviorScenario> active_behavior;

huxerui::View BehaviorProbe() {
  const auto scenario = active_behavior;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      application::AiBehaviorSettingsRepository repository{scenario->store};

      auto empty = co_await repository.Load();
      EXPECT_EXPRESSION(empty);
      EXPECT_EXPRESSION(*empty == domain::AiBehaviorSettings{});

      EXPECT_EXPRESSION(co_await repository.SetMinimalMode(true));
      EXPECT_EXPRESSION(co_await repository.SetLearningMode(true));
      EXPECT_EXPRESSION(co_await repository.SetThinkingScroll(false));
      EXPECT_EXPRESSION(co_await repository.SetThinkingAutoExpand(true));
      EXPECT_EXPRESSION(co_await repository.SetPreserveReasoning(true));
      EXPECT_EXPRESSION(co_await repository.SetSoftCompaction(false));
      EXPECT_EXPRESSION(co_await repository.SetTone(domain::ToneMode::chat));
      EXPECT_EXPRESSION(co_await repository.SetReasoning(
          domain::ReasoningEffort::maximum));

      auto reloaded = co_await repository.Load();
      EXPECT_EXPRESSION(reloaded);
      EXPECT_EXPRESSION(reloaded->minimal_mode);
      EXPECT_EXPRESSION(reloaded->learning_mode);
      EXPECT_EXPRESSION(!reloaded->thinking_scroll);
      EXPECT_EXPRESSION(reloaded->thinking_auto_expand);
      EXPECT_EXPRESSION(reloaded->preserve_reasoning);
      EXPECT_EXPRESSION(!reloaded->soft_compaction);
      EXPECT_EXPRESSION(reloaded->tone == domain::ToneMode::chat);
      EXPECT_EXPRESSION(reloaded->reasoning ==
                        domain::ReasoningEffort::maximum);

      // Turning minimal mode back off must read back as off: this is the stored
      // value every request consults.
      EXPECT_EXPRESSION(co_await repository.SetMinimalMode(false));
      auto minimal_off = co_await repository.Load();
      EXPECT_EXPRESSION(minimal_off);
      EXPECT_EXPRESSION(!minimal_off->minimal_mode);
      EXPECT_EXPRESSION(minimal_off->learning_mode);

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("behavior-settings-repository-probe");
}

// Fixture for the prompt-upgrade migration: one saved-but-unedited copy of a
// superseded default, one genuinely edited template, one orphan key left behind
// by a template that no longer exists.
struct MigrationScenario final {
  std::shared_ptr<MemorySettings> store;
  bool done{};
};

std::shared_ptr<MigrationScenario> active_migration;

huxerui::View MigrationProbe() {
  const auto scenario = active_migration;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      application::PromptTemplateRepository repository{scenario->store};
      const std::string old_default = "OLD BUILT-IN DEFAULT";
      const std::string edited = "MY OWN PROMPT";
      scenario->store
          ->values["@linecode_prompt_template_imageUnderstandingToolSystem"] =
          old_default;
      scenario->store->values["@linecode_prompt_template_todoState"] = edited;
      scenario->store->values["@linecode_prompt_template_learningContext"] =
          "stale orphan";

      std::vector<domain::SupersededPromptDefault> superseded{
          {.id = "imageUnderstandingToolSystem",
           .sha256_hex = application::Sha256Hex(old_default)}};
      auto migrated = co_await repository.MigrateSupersededDefaults(
          std::move(superseded), domain::kPromptTemplateRevision);
      EXPECT_EXPRESSION(migrated);
      // The unedited copy and the orphan key go; the real edit stays.
      EXPECT_EXPRESSION(*migrated == 2);
      EXPECT_EXPRESSION(
          !scenario->store
               ->values.contains(
                   "@linecode_prompt_template_imageUnderstandingToolSystem"));
      EXPECT_EXPRESSION(
          scenario->store->values.at("@linecode_prompt_template_todoState") ==
          edited);
      EXPECT_EXPRESSION(
          !scenario->store
               ->values.contains("@linecode_prompt_template_learningContext"));
      EXPECT_EXPRESSION(
          scenario->store->values.at("@linecode_prompt_template_revision") ==
          std::to_string(domain::kPromptTemplateRevision));

      // The revision gate makes a second run a no-op.
      auto again = co_await repository.MigrateSupersededDefaults(
          {}, domain::kPromptTemplateRevision);
      EXPECT_EXPRESSION(again);
      EXPECT_EXPRESSION(*again == 0);
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("prompt-migration-probe");
}

// Drift and budget guard over the shipped defaults: the assembled prompt must
// never leak an unsubstituted placeholder, name a tool that was not injected,
// or exceed the token budget the simplification was measured against.
struct GuardScenario final {
  std::shared_ptr<MemorySettings> store;
  bool done{};
};

std::shared_ptr<GuardScenario> active_guard;

application::CompletionRequest GuardRequest() {
  application::CompletionRequest request;
  request.model.name = "Test Name";
  request.model.provider_label = "Test Provider";
  request.model.model_id = "test-model";
  request.messages.push_back(
      {.role = application::CompletionRole::user, .content = "hello"});
  request.tools.push_back({.name = "file_read",
                           .description = "Read a file",
                           .parameters_json = "{}",
                           .allowed_in_read_only = true});
  request.tools.push_back({.name = "file_write",
                           .description = "Write a file",
                           .parameters_json = "{}",
                           .allowed_in_read_only = false});
  return request;
}

huxerui::View GuardProbe() {
  const auto scenario = active_guard;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      application::PromptRequestComposer composer{
          std::make_shared<application::PromptTemplateRepository>(scenario->store),
          std::make_shared<application::AiBehaviorSettingsRepository>(
              scenario->store),
          std::make_shared<application::LegacyAttachmentPromptRenderer>()};

      for (const auto mode :
           {application::PromptChatMode::agent,
            application::PromptChatMode::plan,
            application::PromptChatMode::chat}) {
        application::PromptAssemblyContext context;
        context.chat_mode = mode;
        context.work_directory = "/workspace";
        context.linecode_root = "/private";
        context.global_skills_root = "/skills";
        context.workspace_private_root = "/workspace/.linecode";
        context.workspace_skills_root = "/workspace/.linecode/skills";
        context.permission_mode =
            mode == application::PromptChatMode::agent ? "auto" : "readonly";
        auto composed = co_await composer.Compose(GuardRequest(),
                                                  std::move(context));
        EXPECT_EXPRESSION(composed);
        const auto &prompt = composed->messages.front().content;
        EXPECT_EXPRESSION(!prompt.contains("{{"));
        EXPECT_EXPRESSION(prompt.contains("file_read"));
        // Nothing may name a tool that was not injected for this request.
        EXPECT_EXPRESSION(!prompt.contains("shell_execute"));
        EXPECT_EXPRESSION(!prompt.contains("todo_update") ||
                          mode == application::PromptChatMode::agent);
        if (mode == application::PromptChatMode::plan) {
          // Read-only mode: the blocked list has to match the flag, not a
          // hardcoded name list that drifted away from the permission layer.
          EXPECT_EXPRESSION(prompt.contains("file_write"));
        }
        if (mode == application::PromptChatMode::agent) {
          const auto tokens =
              linecode::domain::EstimateTextTokens(prompt);
          // Measured 1210 for this fixture with the shipped defaults; the
          // previous catalog assembled to roughly 3475 tokens with a full tool
          // set, so 1400 still catches a regression that restores the bulk.
          EXPECT_EXPRESSION(tokens <= 1400);
        }
      }
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("prompt-guard-probe");
}

} // namespace

TEST(prompt_request_composer_tests, LegacySuite) {
  const std::array variables{
      domain::PromptVariable{"VALUE", "{{NESTED}}"},
      domain::PromptVariable{"NESTED", "must-not-expand"},
  };
  EXPECT_EXPRESSION(domain::RenderPromptTemplate("  A {{VALUE}} B  ", variables) ==
         "A {{NESTED}} B");
  EXPECT_EXPRESSION(domain::RenderPromptTemplate("{{UNKNOWN}}", variables) ==
         "{{UNKNOWN}}");

  active = std::make_shared<Scenario>();
  active->store = std::make_shared<MemorySettings>();
  active->store->values = {
      {"@linecode_prompt_template_systemPrompt",
       "CUSTOM SYSTEM\n{{TONE_CONTEXT}}\n{{CHAT_MODE_CONTEXT}}\n"
       "{{MODEL_IDENTITY}}\n{{TOOLS_CONTEXT}}\n{{WORK_DIRECTORY_CONTEXT}}\n"
       "{{TODO_STATE}}\n{{LEARNING_CONTEXT}}"},
      {"@linecode_prompt_template_toneChat", "CUSTOM CHAT TONE"},
      {"@lineai_tone", "chat"},
      {"@lineai_reasoning_effort", "max"},
      {"@lineai_preserve_reasoning", "true"},
  };
  auto templates =
      std::make_shared<application::PromptTemplateRepository>(active->store);
  auto behavior =
      std::make_shared<application::AiBehaviorSettingsRepository>(active->store);
  active->composer = std::make_shared<application::PromptRequestComposer>(
      std::move(templates), std::move(behavior),
      std::make_shared<application::LegacyAttachmentPromptRenderer>());
  const huxerui::Application application(Probe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  active.reset();
}

TEST(prompt_request_composer_tests, BehaviorSettingsRepositoryDefaultsAndRoundTrip) {
  active_behavior = std::make_shared<BehaviorScenario>();
  active_behavior->store = std::make_shared<MemorySettings>();
  const huxerui::Application application(BehaviorProbe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active_behavior->done; });
  active_behavior.reset();
}

TEST(prompt_request_composer_tests, PromptTemplateMigrationKeepsRealEdits) {
  active_migration = std::make_shared<MigrationScenario>();
  active_migration->store = std::make_shared<MemorySettings>();
  const huxerui::Application application(MigrationProbe,
                                         {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active_migration->done; });
  active_migration.reset();
}

TEST(prompt_request_composer_tests, ShippedDefaultsStayLeanAndConsistent) {
  active_guard = std::make_shared<GuardScenario>();
  active_guard->store = std::make_shared<MemorySettings>();
  const huxerui::Application application(GuardProbe,
                                        {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active_guard->done; });
  active_guard.reset();
}
