#pragma once

#include <functional>

#include <huxerui/data.h>
#include <huxerui/view.h>

#include "domain/tutorial_document.h"

namespace linecode::presentation {

using TutorialMarkdownLinkHandler =
    std::function<void(const huxerui::Uri &target)>;

// Legacy `MarkdownCodeBlockView` attached a copy action to every code block
// header and reported it with the `markdown_code_copied` toast. The handler is
// optional so non-interactive call sites can keep rendering plain content.
using TutorialMarkdownCopyHandler = std::function<void(std::string code)>;

[[nodiscard]] huxerui::View TutorialMarkdownBlockView(
    const domain::TutorialBlock& block, bool code_wrap_enabled = true,
    float text_scale = 1.0F,
    TutorialMarkdownLinkHandler on_link = {},
    TutorialMarkdownCopyHandler on_copy = {});

[[nodiscard]] huxerui::View TutorialMarkdownDocumentView(
    const domain::TutorialDocument& document, bool code_wrap_enabled = true,
    float text_scale = 1.0F,
    TutorialMarkdownLinkHandler on_link = {},
    TutorialMarkdownCopyHandler on_copy = {});

} // namespace linecode::presentation
