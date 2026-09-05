#pragma once

#include <cstddef>
#include <memory>

#include <huxerui/state.h>
#include <huxerui/text_input.h>
#include <huxerui/view.h>

#include "domain/app_state.h"

namespace linecode::application {
class ChatSession;
}

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View ChatScreen(
    huxerui::State<bool> drawer_open,
    huxerui::State<huxerui::TextEditingValue> draft,
    const std::shared_ptr<application::ChatSession>& session,
    huxerui::State<std::size_t> revision
);

} // namespace linecode::presentation
