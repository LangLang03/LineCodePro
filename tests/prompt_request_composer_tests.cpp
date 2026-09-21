#include "gtest_support.h"
#include <array>
#include <map>
#include <memory>
#include <string>
#include <utility>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/prompt_request_composer.h"
#include "application/legacy_attachment_prompt_renderer.h"
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
      EXPECT_EXPRESSION(prompt.contains("file_read: Read a file"));
      EXPECT_EXPRESSION(prompt.contains("LEARNING"));
      EXPECT_EXPRESSION(prompt.contains("1. [pending] verify"));
      EXPECT_EXPRESSION(composed->reasoning_effort == domain::ReasoningEffort::maximum);
      EXPECT_EXPRESSION(composed->preserve_reasoning);
      EXPECT_EXPRESSION(composed->messages.back().role ==
             application::CompletionRole::user);
      EXPECT_EXPRESSION(composed->messages.back().content.contains("## 附加文件位置"));
      EXPECT_EXPRESSION(composed->messages.back().content.contains(
          "source.cpp (local): content://picked/source.cpp"));
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("prompt-request-composer-probe");
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
