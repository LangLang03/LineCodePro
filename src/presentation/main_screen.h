#pragma once

#include <memory>

#include <huxerui/state.h>
#include <huxerui/view.h>

#include "application/theme_settings.h"

namespace linecode::application {
class ChatSession;
class ModelCatalogGateway;
class ModelStore;
class ProjectWorkspaceStore;
} // namespace linecode::application

namespace linecode::presentation {

huxerui::View
MainScreen(std::shared_ptr<application::ChatSession> initial_session,
           std::shared_ptr<application::ProjectWorkspaceStore> project_store,
           std::shared_ptr<application::ModelStore> model_store,
           std::shared_ptr<application::ModelCatalogGateway> model_catalog,
           std::shared_ptr<application::ThemeSettingsService> theme_service,
           huxerui::State<application::ThemeSettingsState> theme_settings);

} // namespace linecode::presentation
