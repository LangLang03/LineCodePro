package cn.lineai.feature.chat;

import java.util.List;

import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.Message;

/**
 * Conversation CRUD on the chat controller, extracted so the controller stays within its
 * line budget. Every operation stops the running loop before touching the transcript and
 * hops state changes onto the ordered event queue.
 */
final class ChatConversations {

    private ChatConversations() {
    }

    /** Loads the most recent conversation, or starts empty when there is none. */
    static void start(ChatController chat) {
        if (chat.started) return;
        chat.started = true;
        chat.scheduler.io(() -> {
            List<Conversation> loaded = chat.conversations.list();
            chat.publishConversations(loaded);
            if (loaded.isEmpty()) {
                chat.publish(ChatState.empty().toBuilder().model(chat.modelLabel()).build());
                return;
            }
            open(chat, loaded.get(0).id, null);
        });
    }

    /**
     * Loads a conversation; a running loop is stopped first so it cannot write into the new
     * list. An optional completion action runs on the event queue after the binding lands
     * (used by sub agent revive to restore the seq counter before sending).
     *
     * @param chat the driving controller
     * @param id conversation to load
     * @param afterBind action queued after the conversation is bound; may be null
     */
    static void open(ChatController chat, String id, Runnable afterBind) {
        if (chat.engine != null) chat.stop();
        chat.invalidatePendingGeneration();
        chat.scheduler.io(() -> {
            List<Message> loaded = chat.messages.load(id);
            final int nextSeq = loaded.isEmpty() ? 1
                    : (int) loaded.get(loaded.size() - 1).seq + 1;
            final ChatState snapshot = ChatState.builder()
                    .messages(loaded)
                    .project("")
                    .contextPercent(ChatTurnConfig.percent(loaded,
                            ChatContextLimits.of(chat.modelConfig(), chat.settings)))
                    .model(chat.modelLabel())
                    .revision(System.nanoTime())
                    .build();
            chat.events.post(() -> {
                chat.conversationId = id;
                chat.seq = nextSeq;
                chat.discardPipeline();
                chat.publish(snapshot);
                if (afterBind != null) {
                    chat.events.post(afterBind);
                }
            });
        });
    }

    /** Clears the transcript and returns to the unsaved empty conversation. */
    static void newConversation(ChatController chat) {
        if (chat.engine != null) chat.stop();
        chat.invalidatePendingGeneration();
        chat.scheduler.io(() -> {
            final String label = chat.modelLabel();
            chat.events.post(() -> {
                chat.conversationId = "";
                chat.seq = 1;
                chat.discardPipeline();
                chat.publish(ChatState.empty().toBuilder().model(label).build());
            });
        });
    }

    /** Deletes a conversation; the active one is stopped and its draft discarded first. */
    static void delete(ChatController chat, String id) {
        if (id.equals(chat.conversationId)) {
            chat.stop();
            chat.discardPipeline();
        }
        chat.scheduler.io(() -> {
            chat.conversations.delete(id);
            chat.settings.put(ContextCompactor.key(id), "");
            final boolean active = id.equals(chat.conversationId);
            final String label = active ? chat.modelLabel() : "";
            final List<Conversation> remaining = chat.conversations.list();
            chat.events.post(() -> {
                if (active) {
                    chat.conversationId = "";
                    chat.seq = 1;
                    chat.discardPipeline();
                    chat.publish(ChatState.empty().toBuilder().model(label).build());
                }
                chat.publishConversations(remaining);
            });
        });
    }
}
