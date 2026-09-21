#pragma once

#include <cstdint>
#include <initializer_list>
#include <memory>
#include <string>
#include <string_view>
#include <unordered_map>
#include <vector>

#include <huxerui/task.h>

#include "application/behavior_settings_repository.h"
#include "application/ports/attachment_prompt_renderer.h"
#include "application/ports/completion_gateway.h"
#include "application/prompt_template_repository.h"
#include "domain/prompt_renderer.h"

namespace linecode::application {

enum class PromptChatMode : std::uint8_t { chat, plan, agent };

struct PromptAssemblyContext final {
  PromptChatMode chat_mode{PromptChatMode::agent};
  std::string work_directory;
  std::string linecode_root;
  std::string global_skills_root;
  std::string workspace_private_root;
  std::string workspace_skills_root;
  std::string learning_context;
  std::string todo_state;
  std::string permission_mode{"auto"};
  std::string tools_context;
  std::vector<domain::ChatMessage> attachment_history;
};

// Request-time policy boundary used by the tool loop after it has resolved the
// exact advertised tool set. This keeps transport orchestration independent of
// persisted prompt/template details.
class CompletionRequestComposer {
public:
  virtual ~CompletionRequestComposer() = default;

  [[nodiscard]] virtual huxerui::Task<SettingsResult<CompletionRequest>>
  Compose(CompletionRequest request, PromptAssemblyContext context) = 0;
};

class SystemPromptComposer final {
public:
  explicit SystemPromptComposer(
      std::vector<domain::PromptTemplateItem> templates);

  [[nodiscard]] std::string Compose(
      const CompletionRequest &request, const domain::AiBehaviorSettings &behavior,
      const PromptAssemblyContext &context) const;

private:
  [[nodiscard]] std::string_view Template(std::string_view id) const;
  [[nodiscard]] std::string Render(
      std::string_view id,
      std::initializer_list<domain::PromptVariable> values = {}) const;

  std::unordered_map<std::string, std::string> templates_;
};

// Application boundary which makes persisted behavior and customized prompt
// templates part of the request. Presentation supplies only per-request state.
class PromptRequestComposer final : public CompletionRequestComposer {
public:
  PromptRequestComposer(
      std::shared_ptr<PromptTemplateRepository> prompt_templates,
      std::shared_ptr<AiBehaviorSettingsRepository> behavior_settings,
      std::shared_ptr<const AttachmentPromptRenderer> attachment_renderer);

  [[nodiscard]] huxerui::Task<SettingsResult<CompletionRequest>>
  Compose(CompletionRequest request, PromptAssemblyContext context) override;

private:
  std::shared_ptr<PromptTemplateRepository> prompt_templates_;
  std::shared_ptr<AiBehaviorSettingsRepository> behavior_settings_;
  std::shared_ptr<const AttachmentPromptRenderer> attachment_renderer_;
};

} // namespace linecode::application
