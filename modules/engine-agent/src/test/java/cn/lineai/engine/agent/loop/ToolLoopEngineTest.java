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
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;
import org.junit.Test;
import static org.junit.Assert.*;

public class ToolLoopEngineTest {

    @Test public void runsToolThenFinalAnswer() {
        FakeModel model = new FakeModel();
        ToolRegistry registry = new ToolRegistry().register(new EchoTool());
        List<String> texts = new ArrayList<>();
        List<String> results = new ArrayList<>();
        final ToolLoopEngine engine = new ToolLoopEngine(model, registry, context(), Scheduler.immediate(), 5);
        engine.listener(new ToolLoopEngine.Listener() {
            public void onTurnStarted() { }
            public void onAssistantMessage(Message message) { }
            public void onText(String delta) { texts.add(delta); }
            public void onReasoning(String delta) { }
            public void onToolFinished(ToolCall call, ToolResult result) { results.add(result.content); }
            public void onConfirmationRequired(List<ToolCall> calls) { engine.confirm(true); }
            public void onFinished(FinishReason reason) { }
            public void onFailed(DomainException error) { fail(error.getMessage()); }
            public void onBudgetExceeded(int requested, int used, int max) { fail("budget"); }
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
        engine.listener(new ToolLoopEngine.Listener() {
            public void onTurnStarted() { }
            public void onAssistantMessage(Message message) { }
            public void onText(String delta) { }
            public void onReasoning(String delta) { }
            public void onToolFinished(ToolCall call, ToolResult result) { }
            public void onConfirmationRequired(List<ToolCall> calls) { engine.confirm(true); }
            public void onFinished(FinishReason reason) { }
            public void onFailed(DomainException error) { }
            public void onBudgetExceeded(int requested, int used, int max) { budget.add(requested + "/" + max); }
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
        engine.listener(new ToolLoopEngine.Listener() {
            public void onTurnStarted() { }
            public void onAssistantMessage(Message message) { }
            public void onText(String delta) { }
            public void onReasoning(String delta) { }
            public void onToolFinished(ToolCall call, ToolResult result) { results.add(result.content); }
            public void onConfirmationRequired(List<ToolCall> calls) { engine.confirm(false); }
            public void onFinished(FinishReason reason) { }
            public void onFailed(DomainException error) { }
            public void onBudgetExceeded(int requested, int used, int max) { }
        });
        engine.start(config(), new ArrayList<Message>(), "c", "sys", null);
        assertTrue(results.isEmpty());
        assertEquals(LoopPhase.COMPLETED, engine.state().value().phase);
        assertEquals(FinishReason.DENIED, engine.state().value().finishReason);
    }

    private static ModelConfig config() {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("p", "model", "https://example.com/v1").build();
    }

    private static ToolContext context() {
        return new ToolContext() {
            public File workspaceRoot() { return new File("."); }
            public String modelId() { return "m"; }
            public boolean mutationAllowed() { return true; }
        };
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
}
