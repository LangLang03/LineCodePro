package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.tool.api.ToolCall;
import cn.lineai.tool.api.ToolResult;

/**
 * Streaming draft of one tool-loop generation.
 *
 * <p>Owns the buffers and the assistant message that is being written, and is the only place that
 * advances {@code seq} for model/tool output. Every method runs on the {@link SerialEvents} drain,
 * so the order in which the loop reports text, completed messages and tool results is exactly the
 * order this pipeline appends and persists them.</p>
 */
final class ChatTurnPipeline {

    private final ChatController chat;
    private final ModelConfig model;
    private final StringBuilder streamText = new StringBuilder();
    private final StringBuilder streamReasoning = new StringBuilder();
    private Message streamingMessage;
    private List<ToolApprovalItem> pendingApprovals = new ArrayList<>();

    ChatTurnPipeline(ChatController chat, ModelConfig model) {
        this.chat = chat;
        this.model = model;
    }

    List<ToolApprovalItem> pendingApprovals() {
        return pendingApprovals;
    }

    /** True once a newer generation owns the chat; late events from this draft are ignored. */
    private boolean stale() {
        return chat.pipeline != this;
    }

    /** A new model turn starts and owns a fresh streaming draft. */
    void beginTurn() {
        if (stale()) {
            return;
        }
        streamText.setLength(0);
        streamReasoning.setLength(0);
        long now = chat.clock.nowMs();
        streamingMessage = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(chat.conversationId)
                .seq(chat.seq++)
                .role(Role.ASSISTANT)
                .status(Message.Status.STREAMING)
                .model(model.modelId, model.providerId)
                .times(now, now)
                .build();
        chat.publish(chat.state.value().toBuilder()
                .add(streamingMessage)
                .streaming(true)
                .model(model.displayName())
                .revision(System.nanoTime())
                .build());
    }

    void appendText(String delta) {
        if (stale()) {
            return;
        }
        streamText.append(delta);
        publishStreaming();
    }

    void appendReasoning(String delta) {
        if (stale()) {
            return;
        }
        streamReasoning.append(delta);
        publishStreaming();
    }

    /** The model response finished, so the draft becomes a complete message. */
    void finishAssistant(Message message) {
        if (stale()) {
            return;
        }
        Message current = streamingMessage;
        if (current == null) {
            current = Message.builder()
                    .id(Ids.prefixed("msg"))
                    .conversationId(chat.conversationId)
                    .seq(chat.seq++)
                    .role(Role.ASSISTANT)
                    .status(Message.Status.STREAMING)
                    .model(model.modelId, model.providerId)
                    .times(chat.clock.nowMs(), chat.clock.nowMs())
                    .build();
        }
        final Message finished = current.toBuilder()
                .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                .toolCalls(message.toolCalls)
                .status(Message.Status.COMPLETE)
                .finishReason(message.toolCalls.isEmpty() ? "stop" : "tool_calls")
                .times(current.createdAt, chat.clock.nowMs())
                .build();
        streamingMessage = null;
        chat.publish(chat.state.value().toBuilder()
                .replace(finished)
                .streaming(true)
                .revision(System.nanoTime())
                .build());
        chat.writes.submit(() -> chat.messages.append(finished));
    }

    /** One tool result is appended behind the call that requested it. */
    void appendTool(ToolCall call, ToolResult result) {
        if (stale()) {
            return;
        }
        long now = chat.clock.nowMs();
        Message.Builder toolBuilder = Message.builder()
                .id(Ids.prefixed("msg"))
                .conversationId(chat.conversationId)
                .seq(chat.seq++)
                .role(Role.TOOL)
                .status(result.error ? Message.Status.FAILED : Message.Status.COMPLETE)
                .tool(call.id, call.name)
                .addBlock(ContentBlock.text(result.content))
                .times(now, now);
        if (!result.imagePath.isEmpty()) {
            toolBuilder.addBlock(ContentBlock.image(result.imagePath));
        }
        final Message tool = toolBuilder.build();
        chat.publish(chat.state.value().toBuilder()
                .add(tool)
                .revision(System.nanoTime())
                .build());
        chat.writes.submit(() -> chat.messages.append(tool));
    }

    void requestConfirmation(List<ToolCall> calls) {
        if (stale()) {
            return;
        }
        if (chat.autoApprove(calls)) {
            chat.engineConfirm(true);
            return;
        }
        List<ToolApprovalItem> approvals = new ArrayList<>();
        for (ToolCall call : calls) {
            approvals.add(new ToolApprovalItem(call,
                    chat.toolsForLookup().presentation(call.name, call.arguments)));
        }
        pendingApprovals = approvals;
        chat.publish(chat.state.value().toBuilder()
                .pendingApprovals(approvals)
                .revision(System.nanoTime())
                .build());
    }

    void finishTurn() {
        if (stale()) {
            return;
        }
        chat.engine = null;
        pendingApprovals = new ArrayList<>();
        chat.publish(chat.state.value().toBuilder()
                .streaming(false)
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        final String conversation = chat.conversationId;
        chat.writes.submit(() -> {
            chat.conversations.touch(conversation, chat.clock.nowMs(),
                    chat.messages.count(conversation));
            chat.publishConversations(chat.conversations.list());
        });
    }

    void failTurn(DomainException error) {
        if (stale()) {
            return;
        }
        chat.engine = null;
        pendingApprovals = new ArrayList<>();
        String detail = ChatFailure.detail(error, chat.failureText);
        if (chat.errorLogs != null) {
            chat.errorLogs.record(model.displayName(),
                    error.code().name() + " · " + error.detail(), detail);
        }
        android.util.Log.e("LineCodeChat", "generation failed: " + detail);
        boolean created = streamingMessage == null;
        final Message failed = ChatFailure.message(streamingMessage, error, chat.conversationId,
                chat.seq, detail, chat.clock.nowMs(), model,
                ChatTurnConfig.blocks(streamText, streamReasoning));
        if (created) {
            chat.seq++;
        }
        streamingMessage = null;
        chat.publish(chat.state.value().toBuilder()
                .replace(failed)
                .streaming(false)
                .status(error.code().name())
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        chat.writes.submit(() -> chat.messages.append(failed));
        final String conversation = chat.conversationId;
        chat.writes.submit(() -> {
            chat.conversations.touch(conversation, chat.clock.nowMs(),
                    chat.messages.count(conversation));
            chat.publishConversations(chat.conversations.list());
        });
    }

    void budgetExceeded() {
        if (stale()) {
            return;
        }
        chat.engine = null;
        pendingApprovals = new ArrayList<>();
        chat.publish(chat.state.value().toBuilder()
                .streaming(false)
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
    }

    /** Stops the live draft and keeps the partial text as a cancelled answer. */
    void cancel() {
        if (stale()) {
            return;
        }
        pendingApprovals = new ArrayList<>();
        if (streamingMessage == null) {
            chat.publish(chat.state.value().toBuilder()
                    .streaming(false)
                    .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                    .build());
            return;
        }
        final Message stopped = streamingMessage.toBuilder()
                .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                .status(Message.Status.CANCELLED)
                .finishReason(Message.Status.CANCELLED.name().toLowerCase(java.util.Locale.ROOT))
                .times(streamingMessage.createdAt, chat.clock.nowMs())
                .build();
        streamingMessage = null;
        chat.publish(chat.state.value().toBuilder()
                .replace(stopped)
                .streaming(false)
                .pendingApprovals(Collections.<ToolApprovalItem>emptyList())
                .revision(System.nanoTime())
                .build());
        chat.writes.submit(() -> chat.messages.append(stopped));
    }

    private void publishStreaming() {
        if (streamingMessage == null) {
            return;
        }
        streamingMessage = streamingMessage.toBuilder()
                .blocks(ChatTurnConfig.blocks(streamText, streamReasoning))
                .times(streamingMessage.createdAt, chat.clock.nowMs())
                .build();
        chat.publish(chat.state.value().toBuilder()
                .replace(streamingMessage)
                .streaming(true)
                .model(model.displayName())
                .contextPercent(ChatTurnConfig.percent(chat.state.value().messages,
                        ChatContextLimits.of(model, chat.settings)))
                .revision(System.nanoTime())
                .build());
    }
}
