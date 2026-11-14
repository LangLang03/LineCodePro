package cn.lineai.engine.agent.loop;

import org.json.JSONObject;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;
import cn.lineai.engine.protocol.ToolCallDraft;
import cn.lineai.tool.api.BackgroundTasks;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolLoopEngineTest {

    /** Adapter with no-op defaults so each test overrides only the events it cares about. */
    private abstract static class Events implements java.util.function.Consumer<TimelineEvent> {
        public void accept(TimelineEvent event) {
            if (event instanceof TimelineEvent.TurnStarted) {
                turnStarted();
            } else if (event instanceof TimelineEvent.TextDelta) {
                text(((TimelineEvent.TextDelta) event).delta);
            } else if (event instanceof TimelineEvent.ReasoningDelta) {
                reasoning(((TimelineEvent.ReasoningDelta) event).delta);
            } else if (event instanceof TimelineEvent.AssistantCompleted) {
                assistant(((TimelineEvent.AssistantCompleted) event).message);
            } else if (event instanceof TimelineEvent.ToolCompleted) {
                tool(((TimelineEvent.ToolCompleted) event).result);
            } else if (event instanceof TimelineEvent.NoticeInjected) {
                notice(((TimelineEvent.NoticeInjected) event).message);
            } else if (event instanceof TimelineEvent.ConfirmationRequired) {
                confirm(((TimelineEvent.ConfirmationRequired) event).calls);
            } else if (event instanceof TimelineEvent.TurnFinished) {
                finished(((TimelineEvent.TurnFinished) event).reason);
            } else if (event instanceof TimelineEvent.TurnFailed) {
                failed(((TimelineEvent.TurnFailed) event).error);
            } else if (event instanceof TimelineEvent.BudgetExceeded) {
                TimelineEvent.BudgetExceeded budget = (TimelineEvent.BudgetExceeded) event;
                overBudget(budget.requested, budget.used, budget.max);
            } else if (event instanceof TimelineEvent.Retry) {
                TimelineEvent.Retry retry = (TimelineEvent.Retry) event;
                retrying(retry.attempt, retry.max, retry.error);
            }
        }

        void turnStarted() { }
        void text(String delta) { }
        void reasoning(String delta) { }
        void assistant(Message message) { }
        void tool(ToolResult result) { }
        void notice(Message message) { }
        void confirm(List<ToolCall> calls) { }
        void finished(FinishReason reason) { }
        void failed(DomainException error) { }
        void overBudget(int requested, int used, int max) { }
        void retrying(int attempt, int max, DomainException error) { }
    }

    @Test public void runsToolThenFinalAnswer() {
        FakeModel model = new FakeModel();
        ToolRegistry registry = new ToolRegistry().register(new EchoTool());
        List<String> texts = new ArrayList<>();
        List<String> results = new ArrayList<>();
        final ToolLoopEngine engine = new ToolLoopEngine(model, registry, context(), Scheduler.immediate(), 5);
        engine.listen(new Events() {
            public void text(String delta) { texts.add(delta); }
            public void tool(ToolResult result) { results.add(result.content); }
            public void confirm(List<ToolCall> calls) { engine.confirm(true); }
            public void failed(DomainException error) { fail(error.getMessage()); }
            public void overBudget(int requested, int used, int max) { fail("budget"); }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertEquals(2, model.calls);
        assertEquals(Collections.singletonList("echo:hi"), results);
        assertTrue(texts.contains("done"));
        assertEquals(2, engine.history().size());
        assertEquals(Role.TOOL, engine.history().get(1).role);
        assertEquals(1, engine.history().get(0).toolCalls.size());
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
    }

    @Test public void budgetStopsBeforeExecuting() {
        FakeModel model = new FakeModel();
        final List<String> budget = new ArrayList<>();
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry().register(new EchoTool()), context(), Scheduler.immediate(), 0);
        engine.listen(new Events() {
            public void confirm(List<ToolCall> calls) { engine.confirm(true); }
            public void overBudget(int requested, int used, int max) { budget.add(requested + "/" + max); }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertEquals(Collections.singletonList("1/0"), budget);
        assertEquals(1, model.calls);
    }

    @Test public void denialFinishesWithoutRunningTheTool() {
        FakeModel model = new FakeModel();
        final List<String> results = new ArrayList<>();
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry().register(new EchoTool()), context(), Scheduler.immediate(), 5);
        engine.listen(new Events() {
            public void tool(ToolResult result) { results.add(result.content); }
            public void confirm(List<ToolCall> calls) { engine.confirm(false); }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertTrue(results.isEmpty());
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
        assertEquals(FinishReason.DENIED, engine.state().value().finishReason);
    }

    @Test public void finishedAnswerContinuesWhenABackgroundTaskCompleted() {
        TextModel model = new TextModel();
        // The start-of-turn drain is empty; the task finishes while the model is streaming.
        NoticeBackgrounds backgrounds = new NoticeBackgrounds(1,
                "Background shell task bg-1 finished (exit 0).");
        final List<Message> injected = new ArrayList<>();
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry(), context(backgrounds), Scheduler.immediate(), 5);
        engine.listen(new Events() {
            public void notice(Message message) { injected.add(message); }
            public void failed(DomainException error) { fail(error.getMessage()); }
            public void overBudget(int requested, int used, int max) { fail("budget"); }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        // The first response had no tool calls; the pending notice keeps the loop alive and the
        // second response (no further notices) finishes normally.
        assertEquals(2, model.calls);
        assertEquals(1, injected.size());
        assertEquals(Role.USER, injected.get(0).role);
        assertEquals(Message.Visibility.HIDDEN, injected.get(0).visibility);
        assertEquals(1, engine.history().size());
        assertEquals(Role.USER, engine.history().get(0).role);
        assertTrue(engine.history().get(0).plainText().contains("bg-1"));
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
        assertEquals(FinishReason.STOP, engine.state().value().finishReason);
    }

    @Test public void noticeRidesAlongAfterTheNextToolBatch() {
        FakeModel model = new FakeModel();
        NoticeBackgrounds backgrounds = new NoticeBackgrounds(1,
                "Background shell task bg-2 finished (exit 0).");
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry().register(new EchoTool()), context(backgrounds),
                Scheduler.immediate(), 5);
        engine.listen(new Events() {
            public void confirm(List<ToolCall> calls) { engine.confirm(true); }
            public void failed(DomainException error) { fail(error.getMessage()); }
            public void overBudget(int requested, int used, int max) { fail("budget"); }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertEquals(2, model.calls);
        // assistant(tool_calls), tool result, then the hidden user notice.
        assertEquals(3, engine.history().size());
        assertEquals(Role.TOOL, engine.history().get(1).role);
        assertEquals(Role.USER, engine.history().get(2).role);
        assertEquals(Message.Visibility.HIDDEN, engine.history().get(2).visibility);
        assertTrue(engine.history().get(2).plainText().contains("bg-2"));
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
    }

    @Test public void noContinuationWithoutPendingNotices() {
        TextModel model = new TextModel();
        NoticeBackgrounds backgrounds = new NoticeBackgrounds();
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry(), context(backgrounds), Scheduler.immediate(), 5);
        engine.listen(new Events() { });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertEquals(1, model.calls);
        assertTrue(engine.history().isEmpty());
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
    }

    @Test public void toolsFieldIsOmittedForModelsWithoutNativeSupport() {
        ToolCallRecordingModel model = new ToolCallRecordingModel();
        final ToolLoopEngine engine = new ToolLoopEngine(model,
                new ToolRegistry().register(new EchoTool()), context(),
                Scheduler.immediate(), 5);
        engine.listen(new Events() {
            public void confirm(List<ToolCall> calls) { engine.confirm(false); }
        });
        engine.start(noToolsConfig(), new ArrayList<Message>(), "c", "sys", null);
        assertEquals(0, model.lastToolCount);
    }

    private static ModelConfig config() {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "https://example.com/v1").build();
    }

    private static ModelConfig noToolsConfig() {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "https://example.com/v1")
                .capabilities(true, false).build();
    }

    private static ToolContext context() {
        return context(null);
    }

    private static ToolContext context(final BackgroundTasks backgrounds) {
        return new ToolContext() {
            public File workspaceRoot() { return new File("."); }
            public String modelId() { return "m"; }
            public boolean mutationAllowed() { return true; }
            public BackgroundTasks backgrounds() { return backgrounds; }
        };
    }

    /** Background registry stub: leading empty drains first, then one queued notice per drain. */
    private static final class NoticeBackgrounds implements BackgroundTasks {
        private final List<String> queue;

        NoticeBackgrounds(int leadingEmptyDrains, String... notices) {
            queue = new ArrayList<>();
            for (int i = 0; i < leadingEmptyDrains; i++) queue.add(null);
            queue.addAll(java.util.Arrays.asList(notices));
        }

        NoticeBackgrounds(String... notices) {
            this(0, notices);
        }

        public String start(String command, String cwd, boolean remote) {
            throw new UnsupportedOperationException();
        }

        public String output(String id, boolean block, int timeoutMs) {
            return "";
        }

        public String kill(String id) {
            return "";
        }

        public List<String> drainCompleted() {
            if (queue.isEmpty()) return Collections.<String>emptyList();
            String next = queue.remove(0);
            return next == null ? Collections.<String>emptyList()
                    : Collections.singletonList(next);
        }

        public void shutdown() { }
    }

    private static final class EchoTool implements Tool {
        public String name() { return "echo"; }
        public String description() { return "Echo"; }
        public String parametersJson() { return "{\"type\":\"object\"}"; }
        public boolean mutating() { return false; }
        public ToolResult execute(ToolContext context, JSONObject arguments) {
            return ToolResult.success("call_1", "echo:" + arguments.optString("text"));
        }
    }

    private static final class FakeModel implements ModelClient {
        int calls;

        public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
            calls++;
            if (calls == 1) {
                listener.onEvent(StreamEvent.calls(Collections.singletonList(
                        new ToolCallDraft(0, "call_1", "echo", "{\"text\":\"hi\"}"))));
            } else {
                listener.onEvent(StreamEvent.text("done"));
            }
            listener.onComplete();
            return Cancellable.NONE;
        }
    }

    /** Records the tool schema list of every request; always answers plain text. */
    private static final class ToolCallRecordingModel implements ModelClient {
        int lastToolCount = -1;

        public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
            lastToolCount = request.tools.size();
            listener.onComplete();
            return Cancellable.NONE;
        }
    }

    /** Always answers with plain text and no tool calls. */
    private static final class TextModel implements ModelClient {
        int calls;

        public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
            calls++;
            listener.onEvent(StreamEvent.text("done"));
            listener.onComplete();
            return Cancellable.NONE;
        }
    }
}
