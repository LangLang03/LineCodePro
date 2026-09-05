package cn.lineai.ui.model;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ui.ToolCallUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Presentation only: never changes the messages sent to the model or exported by the user. */
public final class ConversationTimeline {
    private ConversationTimeline() {}

    public static final class Operation {
        public final ToolCall call;
        public final ToolResult result;
        Operation(ToolCall call, ToolResult result) { this.call = call; this.result = result; }
    }

    public static final class Block {
        public final String id;
        public final String text;
        public final boolean reasoning;
        public final List<Operation> operations;
        public final List<Block> steps;
        private Block(String id, String text, boolean reasoning, List<Operation> operations) {
            this(id, text, reasoning, operations, Collections.emptyList());
        }
        private Block(String id, String text, boolean reasoning, List<Operation> operations, List<Block> steps) {
            this.id = id; this.text = text; this.reasoning = reasoning;
            this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
        public boolean isTools() { return !operations.isEmpty(); }
        public boolean isAgent() {
            if (operations.size() != 1) return false;
            ToolDisplayCategory category = ToolCallUtils.getDisplayCategory(operations.get(0).call.getName());
            return category == ToolDisplayCategory.AGENT || category == ToolDisplayCategory.AGENT_PIPELINE;
        }
    }

    public static final class Row {
        public final ChatMessage first;
        public final List<ChatMessage> messages;
        public final List<Block> process;
        public final ChatMessage answer;
        public final boolean isTurn;
        public final boolean running;
        public final boolean pending;
        public final long processingStartedAt;
        public final long processingFinishedAt;
        private Row(List<ChatMessage> messages) {
            this.messages = Collections.unmodifiableList(new ArrayList<>(messages));
            first = messages.get(0);
            boolean hasProcess = false, active = false, awaiting = false;
            long startedAt = 0, finishedAt = 0;
            for (ChatMessage message : messages) {
                hasProcess |= message.hasToolCalls() || message.isRetryNotice() || message.isError();
                if (message.getProcessingStartedAt() > 0) {
                    startedAt = startedAt == 0 ? message.getProcessingStartedAt() : Math.min(startedAt, message.getProcessingStartedAt());
                    finishedAt = Math.max(finishedAt, message.getProcessingFinishedAt());
                }
                active |= message.isStreaming();
                for (ToolCall call : message.getToolCalls()) {
                    ToolResult result = message.getToolResult(call.getId());
                    awaiting |= result != null && "pending".equals(result.getReviewState());
                    active |= result == null || "running".equals(result.getReviewState());
                }
            }
            isTurn = hasProcess && first.getRole() == ChatMessage.Role.ASSISTANT && !first.isCompactBlock();
            ChatMessage last = messages.get(messages.size() - 1);
            answer = isTurn && !last.hasToolCalls() && !last.isRetryNotice() && !last.isError()
                    && (last.getProcessingStartedAt() == 0 || last.getProcessingFinishedAt() > 0)
                    && !last.getContent().trim().isEmpty() ? last : null;
            running = active;
            pending = awaiting;
            processingStartedAt = startedAt;
            processingFinishedAt = finishedAt;
            ArrayList<Block> blocks = new ArrayList<>();
            ArrayList<Operation> group = new ArrayList<>();
            ArrayList<Block> steps = new ArrayList<>();
            for (ChatMessage message : messages) {
                boolean hasProse = message != answer && !message.getContent().trim().isEmpty();
                if (hasProse) flush(blocks, group, steps);
                if (message != answer && !message.getReasoningContent().trim().isEmpty()) {
                    Block reasoning = new Block(message.getId() + ":reasoning", message.getReasoningContent(), true, Collections.emptyList());
                    // Internal reasoning is part of the work, not a new outward assistant reply.
                    if (group.isEmpty()) {
                        if (hasProse) blocks.add(reasoning);
                    }
                    else steps.add(reasoning);
                }
                if (hasProse) {
                    blocks.add(new Block(message.getId() + ":text", message.getContent(), false, Collections.emptyList()));
                }
                for (ToolCall call : message.getToolCalls()) {
                    Operation operation = new Operation(call, message.getToolResult(call.getId()));
                    Block callBlock = new Block("call:" + call.getId(), "", false, Collections.singletonList(operation));
                    if (callBlock.isAgent()) {
                        flush(blocks, group, steps);
                        blocks.add(callBlock);
                        continue;
                    }
                    group.add(operation);
                    steps.add(callBlock);
                }
            }
            flush(blocks, group, steps);
            process = Collections.unmodifiableList(blocks);
        }
    }

    private static void flush(List<Block> blocks, ArrayList<Operation> group, ArrayList<Block> steps) {
        if (group.isEmpty()) return;
        blocks.add(new Block("tools:" + group.get(0).call.getId(), "", false, group, steps));
        group.clear(); steps.clear();
    }

    public static List<Row> build(List<ChatMessage> visibleMessages) {
        ArrayList<Row> rows = new ArrayList<>();
        ArrayList<ChatMessage> turn = new ArrayList<>();
        for (ChatMessage message : visibleMessages) {
            if (message.isHidden() || message.getRole() == ChatMessage.Role.TOOL || message.getRole() == ChatMessage.Role.SYSTEM) continue;
            if (message.getRole() != ChatMessage.Role.ASSISTANT || message.isCompactBlock() || message.isModelSwitchNotification()) {
                flushTurn(rows, turn);
                rows.add(new Row(Collections.singletonList(message)));
            } else {
                turn.add(message);
            }
        }
        flushTurn(rows, turn);
        return rows;
    }

    private static void flushTurn(List<Row> rows, ArrayList<ChatMessage> turn) {
        if (turn.isEmpty()) return;
        Row row = new Row(turn);
        if (row.isTurn) rows.add(row);
        else for (ChatMessage message : turn) rows.add(new Row(Collections.singletonList(message)));
        turn.clear();
    }
}
