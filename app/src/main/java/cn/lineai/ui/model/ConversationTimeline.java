package cn.lineai.ui.model;

import cn.lineai.model.ChatMessage;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolDisplayCategory;
import cn.lineai.tool.ui.ToolCallUtils;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
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
        /** reasoning 块 id -> 所属消息，避免展示层每次绑定时做 O(N) 线性查找。 */
        private final Map<String, ChatMessage> reasoningOwners;
        private List<Operation> diffOperations;
        private List<String> changedFilePaths;

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
            Map<String, ChatMessage> owners = new HashMap<>();
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
                    owners.put(reasoning.id, message);
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
            reasoningOwners = owners.isEmpty() ? Collections.emptyMap() : Collections.unmodifiableMap(owners);
        }

        /** 带 diff 的操作（可审阅的文件修改），按出现顺序去重。 */
        public List<Operation> diffOperations() {
            List<Operation> cached = diffOperations;
            if (cached != null) {
                return cached;
            }
            LinkedHashMap<String, Operation> edits = new LinkedHashMap<>();
            for (Block block : process) {
                for (Operation operation : block.operations) {
                    if (operation.result != null && !operation.result.getDiffId().isEmpty()) {
                        // Keep every diff reviewable, including multiple edits to the same file.
                        edits.put(operation.result.getDiffId(), operation);
                    }
                }
            }
            cached = Collections.unmodifiableList(new ArrayList<>(edits.values()));
            diffOperations = cached;
            return cached;
        }

        /** reasoning 块属于哪条消息；块 id 在 {@link #process} 内唯一。 */
        public ChatMessage ownerOfReasoning(String blockId) {
            return reasoningOwners.get(blockId);
        }

        /**
         * 变更文件列表（用于“已修改 N 个文件”展示），同一文件的多次编辑只计一次。
         * 行实例会被复用，因此只在分组真正重建时才解析工具参数。
         */
        public List<String> changedFilePaths() {
            List<String> cached = changedFilePaths;
            if (cached != null) {
                return cached;
            }
            java.util.LinkedHashSet<String> paths = new java.util.LinkedHashSet<>();
            for (Operation operation : diffOperations()) {
                org.json.JSONObject input = cn.lineai.tool.ui.ToolCallUtils.parseInput(operation.call);
                paths.add(input.optString("file_path", input.optString("path",
                        operation.result == null ? "" : operation.result.getDiffId())));
            }
            cached = Collections.unmodifiableList(new ArrayList<>(paths));
            changedFilePaths = cached;
            return cached;
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
        return new Builder().build(visibleMessages);
    }

    private static boolean endsWithCompletedAnswer(List<ChatMessage> turn) {
        if (turn.isEmpty()) return false;
        ChatMessage last = turn.get(turn.size() - 1);
        return !last.isCompactBlock() && !last.hasToolCalls() && !last.isRetryNotice() && !last.isError()
                && !last.isStreaming() && !last.getContent().trim().isEmpty()
                && (last.getProcessingStartedAt() == 0 || last.getProcessingFinishedAt() > 0);
    }

    /**
     * 增量时间线：按“消息分组”为单位缓存 {@link Row}。
     *
     * <p>{@code Row} 是其消息列表的纯函数，而 {@code ChatMessage} 不可变，所以只要一个分组内的
     * 消息实例引用未变，它的行就无需重建。流式输出只改变尾部分组，因此长对话下每次 render
     * 不再重新构建全部行（原本每 80ms 一次、O(全部消息 x 工具调用) 的分配是卡顿主因）。
     */
    public static final class Builder {
        private final ArrayList<ChatMessage> turn = new ArrayList<>();
        private final ArrayList<ChatMessage> single = new ArrayList<>(1);
        private final ArrayList<ArrayList<ChatMessage>> groups = new ArrayList<>();
        private final ArrayList<List<Row>> groupRows = new ArrayList<>();
        private final ArrayList<Row> output = new ArrayList<>();
        private int groupCount;

        public List<Row> build(List<ChatMessage> visibleMessages) {
            groupCount = 0;
            output.clear();
            turn.clear();
            if (visibleMessages != null) {
                for (ChatMessage message : visibleMessages) {
                    if (message == null || message.isHidden()
                            || message.getRole() == ChatMessage.Role.TOOL
                            || message.getRole() == ChatMessage.Role.SYSTEM) {
                        continue;
                    }
                    if (message.getRole() != ChatMessage.Role.ASSISTANT || message.isModelSwitchNotification()) {
                        publishTurn();
                        single.clear();
                        single.add(message);
                        publish(single);
                    } else {
                        if (message.isCompactBlock() && endsWithCompletedAnswer(turn)) publishTurn();
                        turn.add(message);
                    }
                }
            }
            publishTurn();
            while (groups.size() > groupCount) {
                groups.remove(groups.size() - 1);
                groupRows.remove(groupRows.size() - 1);
            }
            return output;
        }

        public void reset() {
            groups.clear();
            groupRows.clear();
            output.clear();
            turn.clear();
            single.clear();
            groupCount = 0;
        }

        private void publishTurn() {
            if (turn.isEmpty()) return;
            publish(turn);
            turn.clear();
        }

        private void publish(List<ChatMessage> messages) {
            int index = groupCount++;
            ArrayList<ChatMessage> cached = groupAt(index);
            if (cached.size() == messages.size() && sameInstances(cached, messages)) {
                output.addAll(groupRows.get(index));
                return;
            }
            cached.clear();
            cached.addAll(messages);
            List<Row> rows = rowsFor(cached);
            if (index < groupRows.size()) {
                groupRows.set(index, rows);
            } else {
                groupRows.add(rows);
            }
            output.addAll(rows);
        }

        private ArrayList<ChatMessage> groupAt(int index) {
            while (groups.size() <= index) {
                groups.add(new ArrayList<>());
            }
            return groups.get(index);
        }

        private static boolean sameInstances(List<ChatMessage> cached, List<ChatMessage> next) {
            for (int i = 0; i < next.size(); i++) {
                if (cached.get(i) != next.get(i)) return false;
            }
            return !next.isEmpty();
        }

        private static List<Row> rowsFor(List<ChatMessage> messages) {
            Row row = new Row(messages);
            if (row.isTurn) return Collections.singletonList(row);
            ArrayList<Row> rows = new ArrayList<>(messages.size());
            for (ChatMessage message : messages) rows.add(new Row(Collections.singletonList(message)));
            return rows;
        }
    }
}
