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
     * Lists the user conversation headers (agent conversations excluded), most recently
     * updated first.
     *
     * @return every stored user conversation header
     */
    List<Conversation> list();

    /**
     * Lists the conversation headers of one kind, most recently updated first.
     *
     * @param kind the conversation kind to match; see the {@link Conversation} kind constants
     * @return every stored conversation header of that kind
     */
    List<Conversation> listByKind(String kind);

    /**
     * Creates a user conversation with the given title and current timestamps.
     *
     * @param title initial display title
     * @return the created conversation, not yet holding any messages
     */
    Conversation create(String title);

    /**
     * Creates a conversation of the given kind with the given title and current timestamps.
     *
     * @param title initial display title
     * @param kind conversation kind; see the {@link Conversation} kind constants
     * @param parentId conversation the agent was spawned from; empty for user conversations
     * @return the created conversation, not yet holding any messages
     */
    Conversation create(String title, String kind, String parentId);

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
