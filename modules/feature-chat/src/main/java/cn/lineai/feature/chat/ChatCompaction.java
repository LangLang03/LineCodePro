package cn.lineai.feature.chat;

import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.ai.ModelProbe;

/**
 * Manual context compaction entry point, extracted from {@link ChatController} so the
 * controller stays within its line budget.
 */
final class ChatCompaction {

    private ChatCompaction() {
    }

    /**
     * Summarizes older messages now and shows a compact marker.
     *
     * @param chat the driving controller
     * @param callback receives the outcome on the event queue
     */
    static void compact(ChatController chat,
                        java.util.function.Consumer<ChatController.CompactionResult> callback) {
        if (chat.engine != null || chat.starting) {
            chat.events.post(() -> callback.accept(ChatController.CompactionResult.BUSY));
            return;
        }
        chat.scheduler.io(() -> {
            final String id = chat.conversationId;
            final ModelConfig model = chat.modelConfig();
            if (model == null) {
                chat.events.post(() -> callback.accept(ChatController.CompactionResult.NO_MODEL));
                return;
            }
            if (id.isEmpty()) {
                chat.events.post(() -> callback.accept(ChatController.CompactionResult.INSUFFICIENT));
                return;
            }
            try {
                List<Message> loaded = chat.messages.load(id);
                boolean done = ContextCompactor.compactNow(model, chat.models, chat.settings, id,
                        loaded, chat.prompts.compactionTemplate(), new ModelProbe()::complete);
                final int percent = ChatTurnConfig.percent(loaded,
                        ChatContextLimits.of(model, chat.settings));
                chat.events.post(() -> {
                    if (!done) {
                        callback.accept(ChatController.CompactionResult.INSUFFICIENT);
                        return;
                    }
                    chat.appendCompactMarker(percent);
                    callback.accept(ChatController.CompactionResult.DONE);
                });
            } catch (Exception error) {
                chat.events.post(() -> callback.accept(ChatController.CompactionResult.FAILED));
            }
        });
    }
}
