package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolPresentation;

/** Builds the inner content of one tool card; dispatch is by {@link cn.lineai.tool.api.ToolIcon}. */
interface ToolCardRenderer {

    void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation);

    /** Whether the card exposes a chevron and a body at all. */
    default boolean expandable(ToolCallEntry entry, ToolPresentation presentation) {
        return true;
    }

    /** Cards with an immediately useful body (diffs, agent output) open on first bind. */
    default boolean initiallyExpanded() {
        return false;
    }
}
