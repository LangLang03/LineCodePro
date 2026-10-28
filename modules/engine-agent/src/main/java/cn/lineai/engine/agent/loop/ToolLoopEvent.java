package cn.lineai.engine.agent.loop;

import java.util.List;

import cn.lineai.tool.api.ToolCall;

/** Inputs the decider reacts to. */
public interface ToolLoopEvent {

    final class Start implements ToolLoopEvent { }

    final class ResponseReady implements ToolLoopEvent {
        public final List<ToolCall> calls;

        public ResponseReady(List<ToolCall> calls) {
            this.calls = calls == null ? java.util.Collections.<ToolCall>emptyList() : calls;
        }
    }

    final class ConfirmAnswered implements ToolLoopEvent {
        public final boolean allowed;

        public ConfirmAnswered(boolean allowed) {
            this.allowed = allowed;
        }
    }

    final class ToolsFinished implements ToolLoopEvent { }

    final class Cancel implements ToolLoopEvent { }

    final class Failed implements ToolLoopEvent {
        public final String message;

        public Failed(String message) {
            this.message = message == null ? "" : message;
        }
    }
}
