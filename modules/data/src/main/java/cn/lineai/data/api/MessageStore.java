package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.Message;

/**
 * Message persistence. Stores are synchronous; the caller owns the scheduler.
 *
 * <p>A message row is stored together with its content blocks and tool call records, so append and
 * update are atomic rewrites of the whole aggregate.</p>
 */
public interface MessageStore {

    /**
     * Loads every message of one conversation in sequence order, with blocks and tool calls
     * attached.
     *
     * @param conversationId conversation whose messages are loaded
     * @return messages ordered by their sequence number, ascending
     */
    List<Message> load(String conversationId);

    /**
     * Inserts one message together with its blocks and tool calls in a single transaction.
     *
     * @param message message to insert; the identifier must not collide with an existing row
     */
    void append(Message message);

    /**
     * Replaces one message and rewrites its blocks and tool calls in a single transaction.
     *
     * @param message message to store; must carry the identifier of an existing row
     */
    void update(Message message);

    /**
     * Deletes one message. Dependent blocks and tool calls are removed by foreign keys.
     *
     * @param messageId message identifier; unknown identifiers are ignored
     */
    void delete(String messageId);

    /**
     * Counts the messages of one conversation.
     *
     * @param conversationId conversation to count
     * @return number of stored messages for the conversation
     */
    int count(String conversationId);
}
