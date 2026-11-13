package cn.lineai.feature.chat;

import cn.lineai.core.model.Message;

/** Row-host hooks owned by {@link TimelineView} and consumed by message rows. */
interface SelectionHost {

    /** True while multi-select mode is active. */
    boolean multiSelect();

    /** True when the message is part of the current selection. */
    boolean isSelected(String messageId);

    /** Adds the message to, or removes it from, the current selection. */
    void toggleSelection(String messageId, Message message);

    /** Hides the long-press action bar of every row (tap elsewhere, scroll, action, multi-select). */
    void dismissActions();
}
