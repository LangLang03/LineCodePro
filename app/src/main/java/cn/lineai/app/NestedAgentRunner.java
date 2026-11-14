package cn.lineai.app;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.ModelStore;
import cn.lineai.engine.agent.loop.FinishReason;
import cn.lineai.engine.agent.loop.TimelineEvent;
import cn.lineai.engine.agent.loop.ToolLoopEngine;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.tool.agent.AgentRunner;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolCatalog;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolKind;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

/** Runs a custom agent as a nested read-only tool loop and waits for its final text. */
public final class NestedAgentRunner implements AgentRunner {

    private final ModelClient client;
    private final ModelStore models;
    private final ToolCatalog baseTools;
    private final ToolContext toolContext;
    private final Scheduler scheduler;

    /**
     * Creates the runner over the shared model client and tool base catalog.
     *
     * @param client model client used by the nested loop
     * @param models model store resolving the currently selected model
     * @param baseTools base catalog filtered down to read-only tools per agent
     * @param toolContext host context adapted to a read-only view
     * @param scheduler scheduler used to start the loop on the main thread
     */
    public NestedAgentRunner(ModelClient client, ModelStore models, ToolCatalog baseTools,
                             ToolContext toolContext, Scheduler scheduler) {
        this.client = client;
        this.models = models;
        this.baseTools = baseTools;
        this.toolContext = toolContext;
        this.scheduler = scheduler;
    }

    /**
     * Runs the agent to completion and returns its final text. The call blocks on a latch
     * (the sanctioned synchronous bridge) while the loop streams on the scheduler; tool
     * confirmations are auto-approved because the registry only contains read-only tools.
     *
     * @param agent the extension agent to execute
     * @param task the user task handed to the agent
     * @return the accumulated assistant text, trimmed
     * @throws IllegalStateException when no model is configured, the loop failed, the tool
     *         budget was exceeded, or the 300 second timeout elapsed (after cancelling)
     * @throws Exception when the engine start fails
     */
    @Override public String run(AgentExtension agent, String task) throws Exception {
        ModelConfig model = models.selected();
        if (model == null) throw new IllegalStateException("No model configured for the agent");
        final CountDownLatch latch = new CountDownLatch(1);
        final StringBuilder text = new StringBuilder();
        final AtomicReference<String> failure = new AtomicReference<>();
        ToolRegistry registry = readOnlyRegistry(agent.toolNames);
        final ToolContext readOnly = new ToolContext() {
            @Override public java.io.File workspaceRoot() { return toolContext.workspaceRoot(); }
            @Override public String modelId() { return model.modelId; }
            @Override public boolean mutationAllowed() { return false; }
            @Override public cn.lineai.tool.api.TodoStore todos() { return toolContext.todos(); }
            @Override public cn.lineai.tool.api.DiffSink diffs() { return null; }
            // Reading background output is a read-only operation, so agents may observe tasks.
            @Override public cn.lineai.tool.api.BackgroundTasks backgrounds() {
                return toolContext.backgrounds();
            }
        };
        final ToolLoopEngine loop = new ToolLoopEngine(client, registry, readOnly, scheduler, 200);
        loop.listen(event -> {
            if (event instanceof TimelineEvent.TextDelta) {
                String delta = ((TimelineEvent.TextDelta) event).delta;
                synchronized (text) { text.append(delta); }
            } else if (event instanceof TimelineEvent.ConfirmationRequired) {
                loop.confirm(true);
            } else if (event instanceof TimelineEvent.TurnFinished) {
                latch.countDown();
            } else if (event instanceof TimelineEvent.TurnFailed) {
                DomainException error = ((TimelineEvent.TurnFailed) event).error;
                failure.set(error.getMessage() == null ? "Agent failed" : error.getMessage());
                latch.countDown();
            } else if (event instanceof TimelineEvent.BudgetExceeded) {
                failure.set("Agent exceeded its tool-call budget");
                latch.countDown();
            }
        });
        Message user = Message.builder().id("agent-task").conversationId("agent").seq(1)
                .role(Role.USER).addBlock(ContentBlock.text(task)).build();
        List<Message> history = new ArrayList<>();
        history.add(user);
        scheduler.main(() -> loop.start(model, history, "agent",
                agent.prompt, null));
        if (!latch.await(300, TimeUnit.SECONDS)) {
            loop.cancel();
            throw new IllegalStateException("Agent timed out");
        }
        if (failure.get() != null) throw new IllegalStateException(failure.get());
        synchronized (text) {
            return text.toString().trim();
        }
    }

    /**
     * Filters the base catalog to non-mutating, non-phone tools, optionally restricted to
     * the agent's declared tool names.
     *
     * @param allowed agent-declared tool names; empty or {@code null} keeps every
     *                read-only tool
     * @return a fresh registry containing only the permitted tools
     */
    private ToolRegistry readOnlyRegistry(List<String> allowed) {
        ToolRegistry result = new ToolRegistry();
        for (Tool tool : baseTools.registry().all()) {
            if (tool.mutating() || tool.kind() == ToolKind.PHONE) continue;
            if (allowed != null && !allowed.isEmpty() && !allowed.contains(tool.name())) continue;
            result.register(tool);
        }
        return result;
    }
}
