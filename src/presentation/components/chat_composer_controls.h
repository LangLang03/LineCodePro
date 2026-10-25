#pragma once

#include <huxerui/huxerui.h>

namespace linecode::presentation::chat_composer {

/// Builds the composer's control row: the attach control, the trailing selector
/// pills (model and reasoning depth), and the primary action.
///
/// The pill group is the row's only growing child, so the row reserves the
/// leading and trailing controls first. A long model name therefore makes the
/// pill group narrower instead of pushing the primary action out of the
/// composer; the group clips whatever still does not fit.
huxerui::View ComposerControlRow(huxerui::View attach, huxerui::View model_pill,
                                 huxerui::View reasoning_pill,
                                 huxerui::View primary);

} // namespace linecode::presentation::chat_composer
