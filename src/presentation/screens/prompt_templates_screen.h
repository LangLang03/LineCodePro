#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application { class PromptTemplateRepository; }

namespace linecode::presentation {

[[huxerui::composable]] huxerui::View PromptTemplatesScreen(
    std::shared_ptr<application::PromptTemplateRepository> repository);

} // namespace linecode::presentation
