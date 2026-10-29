package cn.lineai.feature.chat;

import cn.lineai.core.model.Message;

/** Row-host hooks owned by {@link TimelineView} and consumed by message rows. */
interface SelectionHost {

    boolean multiSelect();

    boolean isSelected(String messageId);

    void toggleSelection(String messageId, Message message);

    /** Hides the long-press action bar of every row (tap elsewhere, scroll, action, multi-select). */
    void dismissActions();
}
