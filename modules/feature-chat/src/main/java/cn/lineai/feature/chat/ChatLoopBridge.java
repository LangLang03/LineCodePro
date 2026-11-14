package cn.lineai.feature.chat;

import java.util.List;
import java.util.function.Consumer;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.model.Message;
import cn.lineai.engine.agent.loop.TimelineEvent;
import cn.lineai.tool.api.ToolCall;

/**
 * Bridges the engine's {@link TimelineEvent} stream onto the chat's ordered event queue.
 *
 * <p>Every event is posted to {@link SerialEvents} and the draft mutates state only inside
 * that drain, so production order and persistence order stay identical regardless of which
 * thread the loop runs on.
 */
final class ChatLoopBridge implements Consumer<TimelineEvent> {

    private final ChatController chat;
    private final ChatTurnPipeline draft;

    /**
     * Creates the bridge.
     *
     * @param chat the owning controller supplying the event queue
     * @param draft the streaming draft the events mutate
     */
    ChatLoopBridge(ChatController chat, ChatTurnPipeline draft) {
        this.chat = chat;
        this.draft = draft;
    }

    /** Dispatches one event onto the ordered queue. */
    @Override
    public void accept(TimelineEvent event) {
        if (event instanceof TimelineEvent.TurnStarted) {
            chat.events.post(draft::beginTurn);
        } else if (event instanceof TimelineEvent.TextDelta) {
            String delta = ((TimelineEvent.TextDelta) event).delta;
            chat.events.post(() -> draft.appendText(delta));
        } else if (event instanceof TimelineEvent.ReasoningDelta) {
            String delta = ((TimelineEvent.ReasoningDelta) event).delta;
            chat.events.post(() -> draft.appendReasoning(delta));
        } else if (event instanceof TimelineEvent.AssistantCompleted) {
            Message message = ((TimelineEvent.AssistantCompleted) event).message;
            chat.events.post(() -> draft.finishAssistant(message));
        } else if (event instanceof TimelineEvent.ToolCompleted) {
            TimelineEvent.ToolCompleted completed = (TimelineEvent.ToolCompleted) event;
            chat.events.post(() -> draft.appendTool(completed.toolMessage,
                    completed.call, completed.result));
        } else if (event instanceof TimelineEvent.NoticeInjected) {
            Message message = ((TimelineEvent.NoticeInjected) event).message;
            chat.events.post(() -> draft.appendNotice(message));
        } else if (event instanceof TimelineEvent.ConfirmationRequired) {
            List<ToolCall> calls = ((TimelineEvent.ConfirmationRequired) event).calls;
            chat.events.post(() -> draft.requestConfirmation(calls));
        } else if (event instanceof TimelineEvent.TurnFinished) {
            chat.events.post(draft::finishTurn);
        } else if (event instanceof TimelineEvent.TurnFailed) {
            DomainException error = ((TimelineEvent.TurnFailed) event).error;
            chat.events.post(() -> draft.failTurn(error));
        } else if (event instanceof TimelineEvent.Retry) {
            TimelineEvent.Retry retry = (TimelineEvent.Retry) event;
            chat.events.post(() -> draft.retry(retry.attempt, retry.max, retry.error));
        } else if (event instanceof TimelineEvent.BudgetExceeded) {
            chat.events.post(draft::budgetExceeded);
        }
    }
}
