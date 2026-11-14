package cn.lineai.engine.agent.loop;

import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.model.Message;
import cn.lineai.tool.api.ToolCall;

/**
 * The typed output stream of one {@link ToolLoopEngine} turn: everything the loop wants the
 * feature layer to know, delivered as immutable events in the order the loop produces them.
 *
 * <p>The engine emits exactly one event per former listener callback; consumers switch on
 * the concrete type. Events are render/persist decisions waiting to happen — they carry no
 * behavior, and the engine keeps no reference to them after emission.
 */
public abstract class TimelineEvent {

    /** Suppresses direct subclassing outside this file; the hierarchy is closed. */
    private TimelineEvent() {
    }

    /** A new model turn begins and owns a fresh streaming draft. */
    public static final class TurnStarted extends TimelineEvent {
    }

    /** One prose delta of the streaming assistant draft. */
    public static final class TextDelta extends TimelineEvent {
        /** The appended text fragment. */
        public final String delta;

        public TextDelta(String delta) {
            this.delta = delta;
        }
    }

    /** One reasoning delta of the streaming assistant draft. */
    public static final class ReasoningDelta extends TimelineEvent {
        /** The appended reasoning fragment. */
        public final String delta;

        public ReasoningDelta(String delta) {
            this.delta = delta;
        }
    }

    /** The model response completed; carries the engine-built assistant row (seq unset). */
    public static final class AssistantCompleted extends TimelineEvent {
        /** The assistant message with its tool calls, if any. */
        public final Message message;

        public AssistantCompleted(Message message) {
            this.message = message;
        }
    }

    /** One tool finished executing; the engine-built TOOL row needs its seq stamped. */
    public static final class ToolCompleted extends TimelineEvent {
        /** The engine-built TOOL message row. */
        public final Message toolMessage;
        /** The call that was executed. */
        public final ToolCall call;
        /** The tool outcome. */
        public final cn.lineai.tool.api.ToolResult result;

        public ToolCompleted(Message toolMessage, ToolCall call,
                             cn.lineai.tool.api.ToolResult result) {
            this.toolMessage = toolMessage;
            this.call = call;
            this.result = result;
        }
    }

    /** A hidden user message (background notice) was appended to the loop history. */
    public static final class NoticeInjected extends TimelineEvent {
        /** The hidden notice message. */
        public final Message message;

        public NoticeInjected(Message message) {
            this.message = message;
        }
    }

    /** The loop pauses for an approval decision on a batch of calls. */
    public static final class ConfirmationRequired extends TimelineEvent {
        /** The batch awaiting approval. */
        public final List<ToolCall> calls;

        public ConfirmationRequired(List<ToolCall> calls) {
            this.calls = calls;
        }
    }

    /** The turn reached a terminal state with a reason (stop, budget, denied, cancelled). */
    public static final class TurnFinished extends TimelineEvent {
        /** Why the turn ended. */
        public final FinishReason reason;

        public TurnFinished(FinishReason reason) {
            this.reason = reason;
        }
    }

    /** The turn failed with a domain error. */
    public static final class TurnFailed extends TimelineEvent {
        /** The failure. */
        public final DomainException error;

        public TurnFailed(DomainException error) {
            this.error = error;
        }
    }

    /** The tool-call budget for this turn is exhausted. */
    public static final class BudgetExceeded extends TimelineEvent {
        /** The requested call count. */
        public final int requested;
        /** The executed call count. */
        public final int used;
        /** The configured maximum. */
        public final int max;

        public BudgetExceeded(int requested, int used, int max) {
            this.requested = requested;
            this.used = used;
            this.max = max;
        }
    }

    /** A transient model failure is about to be retried (attempt starts at 1). */
    public static final class Retry extends TimelineEvent {
        /** The attempt about to run. */
        public final int attempt;
        /** The maximum attempt count. */
        public final int max;
        /** The error that triggered the retry. */
        public final DomainException error;

        public Retry(int attempt, int max, DomainException error) {
            this.attempt = attempt;
            this.max = max;
            this.error = error;
        }
    }
}
