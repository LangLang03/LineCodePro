package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.Conversation;

/** Conversation headers. Implemented in the same module; features only see this interface. */
public interface ConversationStore {

    List<Conversation> list();

    Conversation create(String title);

    void rename(String id, String title);

    void delete(String id);

    void touch(String id, long nowMs, int messageCount);
}
