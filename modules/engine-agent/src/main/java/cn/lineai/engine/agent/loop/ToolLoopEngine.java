package cn.lineai.engine.agent.loop;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.ReasoningSpec;
import cn.lineai.engine.protocol.StreamEvent;
import cn.lineai.engine.protocol.ToolCallDraft;
import cn.lineai.engine.protocol.ToolCallMerger;
import cn.lineai.engine.protocol.ToolSchema;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

/** Drives the tool loop: model stream -> tool batch -> model stream ... until a final answer. */
public final class ToolLoopEngine implements Cancellable {

    public interface Listener {
        void onTurnStarted();

        void onText(String delta);

        void onReasoning(String delta);

        void onAssistantMessage(Message message);

        void onToolFinished(ToolCall call, ToolResult result);

        void onConfirmationRequired(List<ToolCall> calls);

        void onFinished(FinishReason reason);

        void onFailed(DomainException error);

        void onBudgetExceeded(int requested, int used, int max);
    }

    private final ModelClient client;
    private final ToolRegistry tools;
    private final ToolContext toolContext;
    private final Scheduler scheduler;
    private final int maxToolCalls;
    private final ToolLoopDecider decider = new ToolLoopDecider();
    private final ToolCallMerger merger = new ToolCallMerger();
    private final Observable<ToolLoopState> observable;

    private Listener listener;
    private ArrayList<Message> history = new ArrayList<>();
    private String conversationId = "loop";
    private ModelConfig model;
    private String systemPrompt = "";
    private ReasoningSpec reasoning;
    private Cancellable generation;
    private StringBuilder text;
    private long seq = 1;
    private boolean cancelled;

    public ToolLoopEngine(ModelClient client, ToolRegistry tools, ToolContext toolContext,
                          Scheduler scheduler, int maxToolCalls) {
        this.client = client;
        this.tools = tools;
        this.toolContext = toolContext;
        this.scheduler = scheduler;
        this.maxToolCalls = maxToolCalls;
        this.observable = new Observable<>(ToolLoopState.idle(maxToolCalls));
    }

    public Observable<ToolLoopState> state() {
        return observable;
    }

    public void listener(Listener value) {
        listener = value;
    }

    public void start(ModelConfig model, List<Message> history, String conversationId,
                      String systemPrompt, ReasoningSpec reasoning) {
        this.model = model;
        this.history = history == null ? new ArrayList<Message>() : new ArrayList<>(history);
        this.conversationId = conversationId == null ? "loop" : conversationId;
        this.systemPrompt = systemPrompt == null ? "" : systemPrompt;
        this.reasoning = reasoning;
        seq = 1;
        for (Message message : this.history) seq = Math.max(seq, message.seq + 1);
        dispatch(new ToolLoopEvent.Start());
    }

    public void confirm(boolean allowed) {
        dispatch(new ToolLoopEvent.ConfirmAnswered(allowed));
    }

    @Override public void cancel() {
        cancelled = true;
        if (generation != null) {
            generation.cancel();
            generation = null;
        }
        dispatch(new ToolLoopEvent.Cancel());
        if (listener != null) listener.onFinished(FinishReason.CANCELLED);
    }

    @Override public boolean isCancelled() {
        return cancelled;
    }

    private void dispatch(ToolLoopEvent event) {
        scheduler.main(() -> {
            NextAction action = decider.decide(observable.value(), event);
            observable.set(action.state);
            for (NextAction.Effect effect : action.effects) effects(effect, action.state);
        });
    }

    private void effects(NextAction.Effect effect, ToolLoopState state) {
        if (effect instanceof NextAction.CallModel) {
            callModel();
        } else if (effect instanceof NextAction.RequestConfirmation) {
            if (listener != null) listener.onConfirmationRequired(state.pending);
        } else if (effect instanceof NextAction.ExecuteTools) {
            executeBatch(state.pending);
        } else if (effect instanceof NextAction.NotifyFinished) {
            if (listener != null) listener.onFinished(state.finishReason);
        } else if (effect instanceof NextAction.NotifyFailed) {
            if (listener != null) {
                listener.onFailed(new DomainException(ErrorCode.UNKNOWN, state.lastError));
            }
        } else if (effect instanceof NextAction.NotifyBudgetExceeded) {
            NextAction.NotifyBudgetExceeded budget = (NextAction.NotifyBudgetExceeded) effect;
            if (listener != null) {
                listener.onBudgetExceeded(budget.requested, budget.used, budget.max);
            }
        }
    }

    private void callModel() {
        if (cancelled) return;
        merger.clear();
        text = new StringBuilder();
        if (listener != null) listener.onTurnStarted();
        ChatRequest request = ChatRequest.builder()
                .model(model.modelId)
                .system(systemPrompt)
                .messages(history)
                .tools(schemas())
                .sampling(model.temperature, model.maxTokens)
                .reasoning(reasoning)
                .stream(true)
                .build();
        generation = client.stream(model, request, new ModelClient.Listener() {
            @Override public void onEvent(StreamEvent event) {
                if (event.text.length() > 0) {
                    text.append(event.text);
                    if (listener != null) listener.onText(event.text);
                }
                if (event.reasoning.length() > 0 && listener != null) {
                    listener.onReasoning(event.reasoning);
                }
                for (ToolCallDraft draft : event.calls) {
                    merger.accept(draft.index, draft.id, draft.name, draft.arguments);
                }
            }

            @Override public void onComplete() {
                scheduler.io(() -> onResponse());
            }

            @Override public void onError(DomainException error) {
                dispatch(new ToolLoopEvent.Failed(error.detail().isEmpty()
                        ? error.code().name() : error.detail()));
            }
        });
    }

    private void onResponse() {
        if (cancelled) return;
        generation = null;
        List<ToolCall> calls = new ArrayList<>();
        List<ToolCallRecord> records = new ArrayList<>();
        for (ToolCallDraft draft : merger.completed()) {
            String id = draft.id.isEmpty() ? Ids.prefixed("call") : draft.id;
            calls.add(new ToolCall(id, draft.name, draft.arguments));
            records.add(new ToolCallRecord(id, draft.name, draft.arguments));
        }
        Message assistant = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(conversationId)
                .seq(seq)
                .role(Role.ASSISTANT)
                .status(Message.Status.COMPLETE)
                .addBlock(ContentBlock.text(text.toString()))
                .toolCalls(records)
                .build();
        if (!records.isEmpty()) {
            seq++;
            history.add(assistant);
        }
        final Message delivered = assistant;
        scheduler.main(() -> {
            if (listener != null) listener.onAssistantMessage(delivered);
        });
        dispatch(new ToolLoopEvent.ResponseReady(calls));
    }

    private void executeBatch(List<ToolCall> calls) {
        scheduler.io(() -> {
            for (ToolCall call : calls) {
                if (cancelled) return;
                ToolResult result = execute(call);
                Message.Builder toolMessage = Message.builder()
                        .id(Ids.prefixed("msg"))
                        .conversationId(conversationId)
                        .seq(seq++)
                        .role(Role.TOOL)
                        .tool(call.id, call.name)
                        .addBlock(ContentBlock.text(result.content));
                if (!result.imagePath.isEmpty()) {
                    toolMessage.addBlock(ContentBlock.image(result.imagePath));
                }
                history.add(toolMessage.build());
                final ToolCall finished = call;
                final ToolResult outcome = result;
                scheduler.main(() -> {
                    if (listener != null) listener.onToolFinished(finished, outcome);
                });
            }
            dispatch(new ToolLoopEvent.ToolsFinished());
        });
    }

    private ToolResult execute(ToolCall call) {
        Tool tool = tools.find(call.name);
        if (tool == null) return ToolResult.failure(call.id, "Unknown tool: " + call.name);
        if (tool.mutating() && !toolContext.mutationAllowed()) {
            return ToolResult.failure(call.id, "Tool requires confirmation: " + call.name);
        }
        try {
            JSONObject arguments = call.arguments.trim().isEmpty()
                    ? new JSONObject() : new JSONObject(call.arguments);
            return tool.execute(toolContext, arguments);
        } catch (Exception error) {
            return ToolResult.failure(call.id, error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }

    private List<ToolSchema> schemas() {
        List<ToolSchema> schemas = new ArrayList<>();
        for (Tool tool : tools.enabled(toolContext.mutationAllowed())) {
            schemas.add(new ToolSchema(tool.name(), tool.description(), tool.parametersJson()));
        }
        return schemas;
    }

    public List<Message> history() {
        return new ArrayList<>(history);
    }
}
