#pragma once

#include <cstddef>
#include <functional>
#include <memory>
#include <optional>

#include <huxerui/state.h>
#include <huxerui/task.h>
#include <huxerui/text_input.h>
#include <huxerui/view.h>

#include "domain/app_state.h"
#include "domain/behavior_settings.h"
#include "application/chat_mode_service.h"
#include "application/prompt_request_composer.h"
#include "application/ports/todo_state_store.h"
#include "presentation/components/drawer.h"

namespace linecode::application {
class ChatSession;
class AiBehaviorSettingsRepository;
class ContextCompactionService;
class GenerationController;
class McpCompletionLoop;
class MemoryContextService;
class ModelStore;
class OutputSettingsService;
class PendingMessageQueue;
class StoragePermissionService;
class ToolPermissionService;
} // namespace linecode::application

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ChatScreen(
    std::function<void()> open_drawer,
    huxerui::State<huxerui::TextEditingValue> draft,
    const std::shared_ptr<application::ChatSession> &session,
    const std::shared_ptr<application::GenerationController> &generation,
    const std::shared_ptr<application::ModelStore> &model_store,
    const std::shared_ptr<application::McpCompletionLoop> &completion_loop,
    const std::shared_ptr<application::StoragePermissionService>
        &storage_permission,
    std::optional<bool> has_selected_model,
    huxerui::State<huxerui::TaskHandle> active_generation,
    huxerui::State<std::size_t> revision,
    const std::shared_ptr<application::PendingMessageQueue> &pending_messages,
    huxerui::State<DrawerModel> workspace,
    const std::shared_ptr<application::MemoryContextService> &memory_context,
    const std::shared_ptr<application::AiBehaviorSettingsRepository>
        &behavior_settings,
    const std::shared_ptr<application::TodoStateStore> &todo_state,
    const std::shared_ptr<application::ContextCompactionService>
        &compaction_service,
    const std::shared_ptr<application::OutputSettingsService> &output_settings,
    const std::shared_ptr<application::ToolPermissionService>
        &tool_permissions,
    const std::shared_ptr<application::ChatModeService> &chat_modes,
    huxerui::State<application::ChatInteractionModeState> interaction_mode,
    domain::InputSettings input_settings,
    std::string current_project_id,
    application::PromptAssemblyContext prompt_context,
    std::string project_label,
    std::function<void()> show_project_picker);

} // namespace linecode::presentation
