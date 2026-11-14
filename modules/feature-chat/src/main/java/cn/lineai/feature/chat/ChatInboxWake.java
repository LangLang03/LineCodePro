package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.tool.api.RuntimeEvent;

/**
 * Idle wake for the chat loop: when the runtime inbox holds events after the last turn
 * ended, one hidden user message aggregates them and a new turn starts so the model can
 * react. A busy controller skips the wake — the events ride the next turn's own
 * start-of-turn drain, so no event is lost and none is delivered twice.
 */
final class ChatInboxWake {

    private ChatInboxWake() {
    }

    /** Runs on the event queue: the engine is already down after {@code finishTurn}. */
    static void wake(ChatController chat) {
        if (chat.engine != null || chat.starting) {
            return;
        }
        ModelConfig model = chat.modelConfig();
        if (model == null || chat.conversationId.isEmpty()) {
            return;
        }
        final int generation = ++chat.generationSeq;
        chat.scheduler.io(() -> {
            List<RuntimeEvent> events = drainQuietly(chat);
            if (events.isEmpty() || chat.conversationId.isEmpty()) {
                return;
            }
            StringBuilder body = new StringBuilder("(runtime event notice - not from the user)\n");
            for (RuntimeEvent event : events) {
                body.append("- ").append(event.noticeText()).append('\n');
            }
            long now = chat.clock.nowMs();
            Message notice = Message.builder()
                    .id(cn.lineai.core.kernel.util.Ids.prefixed("msg"))
                    .conversationId(chat.conversationId)
                    .seq(chat.seq++)
                    .role(Role.USER)
                    .visibility(Message.Visibility.HIDDEN)
                    .status(Message.Status.COMPLETE)
                    .addBlock(ContentBlock.text(body.toString()))
                    .times(now, now)
                    .build();
            chat.messages.append(notice);
            List<Message> loaded = chat.messages.load(chat.conversationId);
            boolean softCompaction = Boolean.parseBoolean(
                    chat.settings.get(cn.lineai.core.model.UiPreferences.Field.SOFT_COMPACTION.key,
                            "true"));
            final ContextCompactor.Result context = ContextCompactor.prepare(model, chat.models,
                    chat.settings, chat.conversationId, loaded, softCompaction,
                    chat.prompts.compactionTemplate(), new cn.lineai.engine.ai.ModelProbe()::complete);
            final String summarySection = chat.prompts == null || context.summary.isEmpty()
                    ? "" : chat.prompts.summary(context.summary);
            final String target = chat.conversationId;
            chat.events.post(() -> chat.beginGeneration(notice, context.history, summarySection,
                    model, generation, target));
        });
    }

    /** One destructive inbox drain; a failed poll is retried at the next wake. */
    private static List<RuntimeEvent> drainQuietly(ChatController chat) {
        List<RuntimeEvent> events = new ArrayList<>();
        if (chat.toolContext.inbox() == null) {
            return events;
        }
        try {
            events.addAll(chat.toolContext.inbox().drain());
        } catch (Exception error) {
            System.err.println("runtime inbox wake poll failed: " + error);
        }
        return events;
    }
}
