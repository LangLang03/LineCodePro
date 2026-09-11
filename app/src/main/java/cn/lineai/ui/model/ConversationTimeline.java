package cn.lineai.ui.model;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ui.ToolCallUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.HashMap;
import java.util.Map;

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
        public final String compactStatus;
        public final List<Operation> operations;
        public final List<Block> steps;
        private Block(String id, String text, boolean reasoning, List<Operation> operations) {
            this(id, text, reasoning, "", operations, Collections.emptyList());
        }
        private Block(String id, String text, boolean reasoning, List<Operation> operations, List<Block> steps) {
            this(id, text, reasoning, "", operations, steps);
        }
        private Block(String id, String text, boolean reasoning, String compactStatus,
                      List<Operation> operations, List<Block> steps) {
            this.id = id; this.text = text; this.reasoning = reasoning;
            this.compactStatus = compactStatus == null ? "" : compactStatus;
            this.operations = Collections.unmodifiableList(new ArrayList<>(operations));
            this.steps = Collections.unmodifiableList(new ArrayList<>(steps));
        }
        public boolean isTools() { return !operations.isEmpty(); }
        public boolean isCompact() { return !compactStatus.isEmpty(); }
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
                hasProcess |= message.hasToolCalls() || message.isRetryNotice() || message.isError() || message.isCompactBlock();
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
            isTurn = hasProcess && first.getRole() == ChatMessage.Role.ASSISTANT;
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
            Map<String, Integer> blockIds = new HashMap<>();
            for (ChatMessage message : messages) {
                if (message.isCompactBlock()) {
                    flush(blocks, group, steps, blockIds);
                    blocks.add(new Block(uniqueId(message.getId() + ":compact", blockIds), "", false,
                            message.getCompactStatus(), Collections.emptyList(), Collections.emptyList()));
                    continue;
                }
                boolean hasProse = message != answer && !message.getContent().trim().isEmpty();
                if (hasProse) flush(blocks, group, steps, blockIds);
                if (message != answer && !message.getReasoningContent().trim().isEmpty()) {
                    Block reasoning = new Block(uniqueId(message.getId() + ":reasoning", blockIds),
                            message.getReasoningContent(), true, Collections.emptyList());
                    // Internal reasoning is part of the work, not a new outward assistant reply.
                    if (group.isEmpty()) {
                        if (hasProse) blocks.add(reasoning);
                    }
                    else steps.add(reasoning);
                }
                if (hasProse) {
                    blocks.add(new Block(uniqueId(message.getId() + ":text", blockIds),
                            message.getContent(), false, Collections.emptyList()));
                }
                for (int callIndex = 0; callIndex < message.getToolCalls().size(); callIndex++) {
                    ToolCall call = message.getToolCalls().get(callIndex);
                    Operation operation = new Operation(call, message.getToolResult(call.getId()));
                    Block callBlock = new Block(uniqueId("call:" + call.getId(), blockIds),
                            "", false, Collections.singletonList(operation));
                    if (callBlock.isAgent()) {
                        flush(blocks, group, steps, blockIds);
                        blocks.add(callBlock);
                        continue;
                    }
                    group.add(operation);
                    steps.add(callBlock);
                }
            }
            flush(blocks, group, steps, blockIds);
            process = Collections.unmodifiableList(blocks);
        }
    }

    private static void flush(List<Block> blocks, ArrayList<Operation> group, ArrayList<Block> steps,
                              Map<String, Integer> blockIds) {
        if (group.isEmpty()) return;
        blocks.add(new Block(uniqueId("tools:" + group.get(0).call.getId(), blockIds), "", false, group, steps));
        group.clear(); steps.clear();
    }

    private static String uniqueId(String preferred, Map<String, Integer> counts) {
        int occurrence = counts.containsKey(preferred) ? counts.get(preferred) + 1 : 1;
        counts.put(preferred, occurrence);
        return occurrence == 1 ? preferred : preferred + "#" + occurrence;
    }

    public static List<Row> build(List<ChatMessage> visibleMessages) {
        ArrayList<Row> rows = new ArrayList<>();
        ArrayList<ChatMessage> turn = new ArrayList<>();
        for (ChatMessage message : visibleMessages) {
            if (message.isHidden() || message.getRole() == ChatMessage.Role.TOOL || message.getRole() == ChatMessage.Role.SYSTEM) continue;
            if (message.getRole() != ChatMessage.Role.ASSISTANT || message.isModelSwitchNotification()) {
                flushTurn(rows, turn);
                rows.add(new Row(Collections.singletonList(message)));
            } else {
                if (message.isCompactBlock() && endsWithCompletedAnswer(turn)) flushTurn(rows, turn);
                turn.add(message);
            }
        }
        flushTurn(rows, turn);
        return rows;
    }

    private static boolean endsWithCompletedAnswer(List<ChatMessage> turn) {
        if (turn.isEmpty()) return false;
        ChatMessage last = turn.get(turn.size() - 1);
        return !last.isCompactBlock() && !last.hasToolCalls() && !last.isRetryNotice() && !last.isError()
                && !last.isStreaming() && !last.getContent().trim().isEmpty()
                && (last.getProcessingStartedAt() == 0 || last.getProcessingFinishedAt() > 0);
    }

    private static void flushTurn(List<Row> rows, ArrayList<ChatMessage> turn) {
        if (turn.isEmpty()) return;
        Row row = new Row(turn);
        if (row.isTurn) rows.add(row);
        else for (ChatMessage message : turn) rows.add(new Row(Collections.singletonList(message)));
        turn.clear();
    }
}
