#pragma once

#include <cstddef>
#include <memory>
#include <string>

#include <huxerui/huxerui.h>

#include "application/pending_message_queue.h"
#include "application/prompt_request_composer.h"
#include "domain/behavior_settings.h"
#include "domain/tool_permission.h"
#include "presentation/components/chat_generation_state.h"

namespace linecode::application {
class AiBehaviorSettingsRepository;
class ChatSession;
class ContextCompactionService;
class GenerationController;
class McpCompletionLoop;
class MemoryContextService;
class ModelStore;
class SkillRepository;
class TodoStateStore;
class TokenUsageTracker;
class ToolReviewCoordinator;
} // namespace linecode::application

namespace linecode::presentation {

struct ChatGenerationDependencies final {
  std::shared_ptr<application::ChatSession> session;
  std::shared_ptr<application::GenerationController> generation;
  std::shared_ptr<application::ModelStore> model_store;
  std::shared_ptr<application::McpCompletionLoop> completion_loop;
  std::shared_ptr<application::MemoryContextService> memory_context;
  std::shared_ptr<application::AiBehaviorSettingsRepository> behavior_settings;
  std::shared_ptr<application::TodoStateStore> todo_state;
  std::shared_ptr<application::TokenUsageTracker> token_usage;
  std::shared_ptr<application::SkillRepository> skills;
  std::shared_ptr<application::ContextCompactionService> compaction;
  std::shared_ptr<AutoCompactionUiState> auto_compaction;
};

class ChatGenerationRunner {
public:
  virtual ~ChatGenerationRunner() = default;

  [[nodiscard]] virtual bool Start(application::PendingMessage message) = 0;
  virtual void CancelAndContinue() = 0;
};

[[nodiscard]] std::shared_ptr<ChatGenerationRunner> MakeChatGenerationRunner(
    ChatGenerationDependencies dependencies,
    std::shared_ptr<application::ToolReviewCoordinator> tool_reviews,
    std::shared_ptr<application::PendingMessageQueue> pending_messages,
    huxerui::TaskScope tasks,
    huxerui::State<huxerui::TaskHandle> active_generation,
    huxerui::State<std::size_t> revision, std::string current_project_id,
    application::PromptAssemblyContext prompt_context,
    domain::ToolPermissionMode permission_mode, huxerui::ToastHandle toast,
    RetryLabels retry_labels);

} // namespace linecode::presentation
