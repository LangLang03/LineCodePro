#pragma once

#include <cstddef>
#include <functional>
#include <memory>

#include <huxerui/state.h>
#include <huxerui/text_input.h>
#include <huxerui/view.h>

#include "domain/app_state.h"
#include "presentation/components/drawer.h"

namespace linecode::application {
class ChatSession;
}

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View
ChatScreen(std::function<void()> open_drawer,
           huxerui::State<huxerui::TextEditingValue> draft,
           const std::shared_ptr<application::ChatSession> &session,
           huxerui::State<std::size_t> revision,
           huxerui::State<DrawerModel> workspace);

} // namespace linecode::presentation
