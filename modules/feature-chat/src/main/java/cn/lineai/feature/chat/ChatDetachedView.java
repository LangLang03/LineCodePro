package cn.lineai.feature.chat;

/**
 * Detached-view bookkeeping: while a turn keeps running in one conversation, the UI may show
 * another. The write target stays with the running loop; the viewed conversation keeps its
 * own seq so a handover appends correctly once the background turn ends.
 */
final class ChatDetachedView {

    private ChatDetachedView() {
    }

    /** Conversation currently displayed; falls back to the write target when attached. */
    static String viewed(ChatController chat) {
        // While detached the viewed id may legitimately be empty (a brand-new unsaved chat),
        // and it must not collapse into the running conversation.
        return chat.detached ? chat.viewedConversationId : chat.conversationId;
    }

    /** Conversation a background turn is writing into; empty when idle. */
    static String running(ChatController chat) {
        return chat.engine != null || chat.starting ? chat.conversationId : "";
    }

    static boolean detached(ChatController chat) {
        return chat.detached;
    }

    /** Marks the controller as viewing another conversation while the turn keeps running. */
    static void detach(ChatController chat, String id, int seqAtView) {
        chat.viewedConversationId = id;
        chat.viewedSeq = seqAtView;
        chat.detached = true;
    }

    /** Rebinds the view to the running conversation. */
    static void reattach(ChatController chat) {
        chat.viewedConversationId = "";
        chat.detached = false;
    }

    /** Hands the write target to the viewed conversation after the background turn ended. */
    static void adopt(ChatController chat) {
        if (!chat.detached) return;
        chat.conversationId = chat.viewedConversationId;
        chat.seq = chat.viewedSeq;
        chat.viewedConversationId = "";
        chat.detached = false;
        chat.discardPipeline();
        chat.publish(chat.state.value().toBuilder()
                .busyElsewhere(false)
                .revision(System.nanoTime())
                .build());
    }
}
