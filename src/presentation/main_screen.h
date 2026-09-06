#pragma once

#include <memory>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "application/theme_settings.h"

namespace linecode::application {
class ChatSession;
class AiBehaviorSettingsRepository;
class CompletionGateway;
class ErrorLogService;
class InputSettingsRepository;
class ModelCatalogGateway;
class ModelStore;
class OutputSettingsService;
class ProjectWorkspaceStore;
class PromptTemplateRepository;
class StorageStatsRepository;
} // namespace linecode::application

namespace linecode::presentation {

huxerui::View
MainScreen(std::shared_ptr<application::ChatSession> initial_session,
           std::shared_ptr<application::ProjectWorkspaceStore> project_store,
           std::shared_ptr<application::ModelStore> model_store,
           std::shared_ptr<application::ModelCatalogGateway> model_catalog,
           std::shared_ptr<application::AiBehaviorSettingsRepository>
               ai_behavior_settings,
           std::shared_ptr<application::InputSettingsRepository> input_settings,
           std::shared_ptr<application::PromptTemplateRepository>
               prompt_templates,
           std::shared_ptr<application::CompletionGateway> completion_gateway,
           std::shared_ptr<application::OutputSettingsService>
               output_settings_service,
           std::shared_ptr<application::ThemeSettingsService> theme_service,
           huxerui::State<application::ThemeSettingsState> theme_settings,
           std::shared_ptr<application::StorageStatsRepository> storage_stats =
               {},
           std::shared_ptr<application::ErrorLogService> error_logs = {});

} // namespace linecode::presentation
