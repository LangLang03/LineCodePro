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
#include "presentation/components/drawer.h"

namespace linecode::application {
class ChatSession;
class CompletionGateway;
class GenerationController;
class ModelStore;
}

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
ChatScreen(std::function<void()> open_drawer,
           huxerui::State<huxerui::TextEditingValue> draft,
           const std::shared_ptr<application::ChatSession> &session,
           const std::shared_ptr<application::GenerationController> &generation,
           const std::shared_ptr<application::ModelStore> &model_store,
           const std::shared_ptr<application::CompletionGateway>
               &completion_gateway,
           std::optional<bool> has_selected_model,
           huxerui::State<huxerui::TaskHandle> active_generation,
           huxerui::State<std::size_t> revision,
           huxerui::State<DrawerModel> workspace);

} // namespace linecode::presentation
