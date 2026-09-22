#pragma once

#include <functional>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/huxerui.h>

#include "application/pending_message_queue.h"
#include "application/prompt_request_composer.h"
#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "domain/chat_image.h"
#include "domain/input_attachment.h"
#include "domain/model_config.h"
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
class ToolReviewCoordinator;
} // namespace linecode::application

namespace linecode::presentation::chat_composer {

struct ImageSelection final {
  domain::ChatImage message;
  huxerui::ImageAsset preview;

  bool operator==(const ImageSelection &) const = default;
};

struct Services final {
  std::shared_ptr<application::ChatSession> session;
  std::shared_ptr<application::GenerationController> generation;
  std::shared_ptr<application::ModelStore> model_store;
  std::shared_ptr<application::McpCompletionLoop> completion_loop;
  std::shared_ptr<application::MemoryContextService> memory_context;
  std::shared_ptr<application::AiBehaviorSettingsRepository> behavior_settings;
  std::shared_ptr<application::TodoStateStore> todo_state;
  std::shared_ptr<application::SkillRepository> skills;
  std::shared_ptr<application::ToolReviewCoordinator> tool_reviews;
  std::shared_ptr<application::PendingMessageQueue> pending_messages;
  std::shared_ptr<application::ContextCompactionService> compaction;
};

struct ViewState final {
  huxerui::State<huxerui::TextEditingValue> draft;
  std::optional<bool> has_selected_model;
  huxerui::TaskScope tasks;
  huxerui::State<huxerui::TaskHandle> active_generation;
  huxerui::State<std::size_t> revision;
  huxerui::State<std::vector<domain::InputAttachment>> attachments;
  huxerui::State<std::optional<ImageSelection>> image;
  huxerui::State<std::optional<std::string>> quote;
  domain::ChatMode chat_mode{domain::ChatMode::chat};
  huxerui::State<std::vector<domain::ModelConfig>> slash_models;
  huxerui::State<std::string> selected_model_id;
  // Reasoning depth shown by the composer's control row; owned by the screen so
  // the picker sheet and the pill never disagree.
  huxerui::State<std::optional<domain::ReasoningEffort>> reasoning_effort;
  domain::InputSettings input_settings;
  std::string current_project_id;
  application::PromptAssemblyContext prompt_context;
  domain::ToolPermissionMode permission_mode{
      domain::ToolPermissionMode::automatic};
  huxerui::ToastHandle toast;
  std::shared_ptr<AutoCompactionUiState> auto_compaction;
  RetryLabels retry_labels;
};

struct Actions final {
  std::function<void()> show_attachment_picker;
  std::function<void()> show_image_picker;
  std::function<void()> show_model_picker;
  std::function<void()> show_reasoning_picker;
  std::function<bool(std::string_view)> handle_slash_command;
};

[[huxerui::composable]] huxerui::View
Composer(Services services, ViewState state, Actions actions);

} // namespace linecode::presentation::chat_composer
