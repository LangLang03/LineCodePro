package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.Message;

/** Message persistence. Stores are synchronous; the caller owns the scheduler. */
public interface MessageStore {

    List<Message> load(String conversationId);

    void append(Message message);

    void update(Message message);

    void delete(String messageId);

    int count(String conversationId);
}
