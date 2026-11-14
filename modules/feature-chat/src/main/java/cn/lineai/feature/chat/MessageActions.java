package cn.lineai.feature.chat;

import cn.lineai.core.model.Message;

/**
 * Host hooks behind the long-press message action bar.
 *
 * <p>Mirrors the legacy {@code MessageActionListener} / C++ {@code ActionCallbacks}. Copy, share
 * and text selection work without a host; quote, retry, multi-select and recall are delegated
 * because they mutate the conversation, the composer or the selection bar owned by the screen.</p>
 */
public interface MessageActions {

    /** False lets the screen hide a host-backed action it cannot service yet. */
    default boolean canQuote() {
        return true;
    }

    /** False hides the multi-select entry in the action bar. */
    default boolean canMultiSelect() {
        return true;
    }

    /** False hides the retry entry in the action bar. */
    default boolean canRetry() {
        return true;
    }

    /** Quotes the message body into the composer. */
    default void onQuote(Message message) {
    }

    /** Restarts the generation from the given user message. */
    default void onRetry(Message message) {
    }

    /** Recalls (removes) the message and everything after it. */
    default void onRecall(Message message) {
    }

    /** Enters multi-select mode anchored on the given message. */
    default void onMultiSelect(Message message) {
    }

    /** Called whenever a row is selected or deselected in multi-select mode. */
    default void onSelectionChanged(java.util.List<Message> selected) {
    }
}
