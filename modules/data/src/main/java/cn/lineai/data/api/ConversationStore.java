package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.Conversation;

/**
 * Conversation headers. Implemented in the same module; features only see this interface.
 *
 * <p>Headers carry no message content; the message bodies live in the message store. Implementations
 * are synchronous; callers own the scheduler.</p>
 */
public interface ConversationStore {

    /**
     * Lists the conversation headers, most recently updated first.
     *
     * @return every stored conversation header
     */
    List<Conversation> list();

    /**
     * Creates a conversation with the given title and current timestamps.
     *
     * @param title initial display title
     * @return the created conversation, not yet holding any messages
     */
    Conversation create(String title);

    /**
     * Renames a conversation and refreshes its update timestamp so it sorts to the top.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     * @param title new display title
     */
    void rename(String id, String title);

    /**
     * Deletes a conversation. Dependent messages, blocks and tool calls are removed by foreign
     * keys.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     */
    void delete(String id);

    /**
     * Updates the sort timestamp and the denormalized message counter without touching other
     * columns; used by the message store after each append.
     *
     * @param id conversation identifier; unknown identifiers are ignored
     * @param nowMs new update timestamp in epoch milliseconds
     * @param messageCount number of messages now held by the conversation
     */
    void touch(String id, long nowMs, int messageCount);
}
