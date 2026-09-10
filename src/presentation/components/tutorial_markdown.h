#pragma once

#include <huxerui/view.h>

#include "domain/tutorial_document.h"

namespace linecode::presentation {

[[nodiscard]] huxerui::View TutorialMarkdownBlockView(
    const domain::TutorialBlock& block, bool code_wrap_enabled = true);

[[nodiscard]] huxerui::View TutorialMarkdownDocumentView(
    const domain::TutorialDocument& document, bool code_wrap_enabled = true);

} // namespace linecode::presentation
