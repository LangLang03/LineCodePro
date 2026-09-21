#pragma once

#include <cstddef>
#include <functional>
#include <memory>
#include <optional>
#include <string>

#include <huxerui/state.h>
#include <huxerui/task.h>
#include <huxerui/text_input.h>
#include <huxerui/view.h>

#include "application/chat_mode_service.h"
#include "application/ports/todo_state_store.h"
#include "application/prompt_request_composer.h"
#include "application/tool_review_broker.h"
#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "presentation/components/drawer.h"

namespace linecode::application {
class AgentResultReader;
class ChatSession;
class AiBehaviorSettingsRepository;
class ContextCompactionService;
class DiffReviewService;
class DiffStore;
class GenerationController;
class McpCompletionLoop;
class MemoryContextService;
class ModelStore;
class OutputSettingsService;
class SkillRepository;
class McpExecutionSettingsService;
class PendingMessageQueue;
class StoragePermissionService;
class ToolPermissionService;
} // namespace linecode::application

namespace linecode::presentation {

struct ChatScreenServices final {
  std::shared_ptr<application::ChatSession> session;
  std::shared_ptr<application::GenerationController> generation;
  std::shared_ptr<application::ModelStore> model_store;
  std::shared_ptr<application::McpCompletionLoop> completion_loop;
  std::shared_ptr<application::AgentResultReader> agent_results;
  std::shared_ptr<application::StoragePermissionService> storage_permission;
  std::shared_ptr<application::PendingMessageQueue> pending_messages;
  std::shared_ptr<application::MemoryContextService> memory_context;
  std::shared_ptr<application::AiBehaviorSettingsRepository> behavior_settings;
  std::shared_ptr<application::TodoStateStore> todo_state;
  std::shared_ptr<application::SkillRepository> skills;
  std::shared_ptr<application::McpExecutionSettingsService> execution_settings;
  std::shared_ptr<application::ContextCompactionService> compaction_service;
  std::shared_ptr<application::DiffStore> diff_store;
  std::shared_ptr<application::DiffReviewService> diff_review;
  std::shared_ptr<application::OutputSettingsService> output_settings;
  std::shared_ptr<application::ToolPermissionService> tool_permissions;
  std::shared_ptr<application::ToolReviewBroker> tool_reviews;
  std::shared_ptr<application::ChatModeService> chat_modes;
};

struct ChatScreenState final {
  huxerui::State<huxerui::TextEditingValue> draft;
  std::optional<bool> has_selected_model;
  huxerui::State<huxerui::TaskHandle> active_generation;
  huxerui::State<std::size_t> revision;
  huxerui::State<DrawerModel> workspace;
  huxerui::State<application::ChatInteractionModeState> interaction_mode;
  domain::InputSettings input_settings;
  std::string current_project_id;
  application::PromptAssemblyContext prompt_context;
  std::string project_label;
};

struct ChatScreenActions final {
  std::function<void()> open_drawer;
  std::function<void()> show_project_picker;
  std::function<void()> refresh_workspace;
};

[[huxerui::composable]] huxerui::View
ChatScreen(ChatScreenServices services, ChatScreenState state,
           ChatScreenActions actions);

} // namespace linecode::presentation
