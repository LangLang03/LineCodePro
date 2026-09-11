#pragma once

#include <functional>

#include <huxerui/data.h>
#include <huxerui/view.h>

#include "domain/tutorial_document.h"

namespace linecode::presentation {

using TutorialMarkdownLinkHandler =
    std::function<void(const huxerui::Uri &target)>;

[[nodiscard]] huxerui::View TutorialMarkdownBlockView(
    const domain::TutorialBlock& block, bool code_wrap_enabled = true,
    float text_scale = 1.0F,
    TutorialMarkdownLinkHandler on_link = {});

[[nodiscard]] huxerui::View TutorialMarkdownDocumentView(
    const domain::TutorialDocument& document, bool code_wrap_enabled = true,
    float text_scale = 1.0F,
    TutorialMarkdownLinkHandler on_link = {});

} // namespace linecode::presentation
