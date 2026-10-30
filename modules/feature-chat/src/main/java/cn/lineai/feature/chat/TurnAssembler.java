package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.Role;
import cn.lineai.core.model.ToolCallRecord;
import cn.lineai.feature.chat.toolcall.ToolCallEntry;

/**
 * Groups the flat message list into render rows.
 *
 * <p>One user turn produces a user row followed by at most one assistant turn row. The assistant
 * turn keeps its process (reasoning, interstitial prose, tool groups) separately from the final
 * answer so the timeline can fold the whole process behind one disclosure, exactly like the
 * legacy {@code ConversationTimeline}. Tool calls are paired with their {@link Role#TOOL} result
 * messages by call id.</p>
 */
public final class TurnAssembler {

    public static final int ITEM_REASONING = 0;
    public static final int ITEM_MARKDOWN = 1;
    public static final int ITEM_TOOLS = 2;
    public static final int ITEM_COMPACT = 3;

    private TurnAssembler() {
    }

    /** One process entry: a reasoning disclosure, interstitial prose or a collapsed tool group. */
    public static final class Item {
        public final int kind;
        public final String key;
        public final String text;
        public final String status;
        public final List<ToolCallEntry> tools;

        private Item(int kind, String key, String text, String status,
                     List<ToolCallEntry> tools) {
            this.kind = kind;
            this.key = key;
            this.text = text;
            this.status = status;
            this.tools = tools == null
                    ? Collections.<ToolCallEntry>emptyList()
                    : Collections.unmodifiableList(new ArrayList<>(tools));
        }
    }

    /** One render row: either a user bubble or a folded assistant turn. */
    public static final class Row {
        public final String key;
        public final boolean user;
        public final Message subject;
        public final Message userMessage;
        public final Message answer;
        /** Visible failure reason from an ERROR block on a failed turn. */
        public final String error;
        public final List<Item> process;
        public final List<ToolCallEntry> tools;
        public final boolean live;
        public final boolean running;
        public final boolean pending;
        /** True when any message of the turn failed, so the process panel opens on the error. */
        public final boolean failed;
        public final long processingStartedAt;
        public final long processingFinishedAt;
        /** Everything the row draws; identical signatures skip a rebind. */
        public final String signature;

        private Row(Draft draft) {
            this.key = draft.key;
            this.user = draft.user;
            this.subject = draft.subject;
            this.userMessage = draft.userMessage;
            this.answer = draft.answer;
            this.error = draft.error == null ? "" : draft.error;
            this.process = Collections.unmodifiableList(new ArrayList<>(draft.process));
            this.tools = Collections.unmodifiableList(new ArrayList<>(draft.tools));
            this.live = draft.live;
            this.running = draft.running;
            this.pending = draft.pending;
            this.failed = draft.failed;
            this.processingStartedAt = draft.startedAt;
            this.processingFinishedAt = draft.finishedAt;
            this.signature = buildSignature();
        }

        /** Mutable accumulator so the row constructor stays short. */
        static final class Draft {
            String key = "";
            boolean user;
            Message subject;
            Message userMessage;
            Message answer;
            String error = "";
            final List<Item> process = new ArrayList<>();
            final List<ToolCallEntry> tools = new ArrayList<>();
            boolean live;
            boolean running;
            boolean pending;
            boolean failed;
            long startedAt;
            long finishedAt;
        }

        private String buildSignature() {
            StringBuilder value = new StringBuilder(key).append('\u0001').append(user)
                    .append('\u0001').append(live).append(running).append(pending).append(failed)
                    .append('\u0001').append(processingStartedAt).append('/')
                    .append(processingFinishedAt);
            if (userMessage != null) {
                value.append('\u0001').append(userMessage.plainText());
                for (ContentBlock block : userMessage.blocks) {
                    value.append('\u0001').append(block.type).append(':').append(block.refId);
                }
            }
            if (answer != null) {
                value.append('\u0001').append(answer.id).append('\u0001')
                        .append(answer.plainText());
            }
            if (!error.isEmpty()) {
                value.append('\u0001').append(error);
            }
            for (Item item : process) {
                value.append('\u0002').append(item.kind).append(':').append(item.key)
                        .append(':').append(item.text).append(':').append(item.status);
                for (ToolCallEntry entry : item.tools) {
                    value.append('\u0003').append(entry.callId).append('/')
                            .append(entry.name).append('/').append(entry.result.length())
                            .append('/').append(entry.failed).append('/').append(entry.running)
                            .append('/').append(entry.diffId).append('/')
                            .append(entry.reviewState);
                }
            }
            return value.toString();
        }

        public boolean hasProcess() {
            return !process.isEmpty();
        }

        /** True when the row has something to draw at all. */
        public boolean visible() {
            return user || answer != null || hasProcess() || live || !error.isEmpty();
        }
    }

    public static List<Row> assemble(List<Message> messages, Set<String> pendingCallIds,
                                     boolean streaming) {
        List<Row> rows = new ArrayList<>();
        if (messages == null) {
            return rows;
        }
        Map<String, Message> results = new LinkedHashMap<>();
        for (Message message : messages) {
            if (message.role == Role.TOOL && !message.toolCallId.isEmpty()) {
                results.put(message.toolCallId, message);
            }
        }
        Set<String> pending = pendingCallIds == null
                ? Collections.<String>emptySet() : pendingCallIds;
        List<Message> turn = new ArrayList<>();
        for (Message message : messages) {
            if (message.visibility == Message.Visibility.HIDDEN) {
                continue;
            }
            if (message.role == Role.TOOL || message.role == Role.SYSTEM) {
                continue;
            }
            if (message.role == Role.USER) {
                flush(rows, turn, results, pending, streaming, false);
                Row.Draft draft = new Row.Draft();
                draft.key = "u:" + message.id;
                draft.user = true;
                draft.subject = message;
                draft.userMessage = message;
                rows.add(new Row(draft));
            } else {
                turn.add(message);
            }
        }
        flush(rows, turn, results, pending, streaming, true);
        return rows;
    }

    private static void flush(List<Row> rows, List<Message> turn,
                              Map<String, Message> results, Set<String> pending,
                              boolean streaming, boolean lastTurn) {
        if (turn.isEmpty()) {
            return;
        }
        // Compaction markers may trail the final answer; keep looking backwards for the answer.
        Message answer = null;
        for (int index = turn.size() - 1; index >= 0; index--) {
            if (isAnswer(turn.get(index))) {
                answer = turn.get(index);
                break;
            }
        }
        boolean live = streaming && lastTurn;
        boolean running = live;
        boolean awaiting = false;
        List<Item> process = new ArrayList<>();
        List<ToolCallEntry> all = new ArrayList<>();
        List<ToolCallEntry> group = new ArrayList<>();
        for (Message message : turn) {
            boolean isAnswer = message == answer;
            String reasoning = message.reasoningText();
            String text = message.plainText();
            // Legacy ConversationTimeline: prose flushes the open tool group; thinking is only
            // rendered when it arrives together with an outward prose block and no group is
            // open. Thinking produced between tool calls stays folded inside the group, so a
            // long run of tool calls folds into one group and never leaves an empty block.
            boolean hasProse = !isAnswer && !text.trim().isEmpty();
            if (hasProse) {
                flushGroup(process, group);
            }
            if (!isAnswer && !reasoning.trim().isEmpty() && group.isEmpty() && hasProse) {
                process.add(new Item(ITEM_REASONING, message.id + ":reasoning",
                        reasoning, "", null));
            }
            if (hasProse) {
                process.add(new Item(ITEM_MARKDOWN, message.id + ":text", text, "", null));
            }
            for (ContentBlock block : message.blocks) {
                if (block.type == ContentBlock.Type.COMPACT) {
                    flushGroup(process, group);
                    process.add(new Item(ITEM_COMPACT, message.id + ":compact", "",
                            block.status, null));
                }
            }
            for (ToolCallRecord call : message.toolCalls) {
                Message result = results.get(call.id);
                boolean missing = result == null;
                if (missing && live) {
                    running = true;
                }
                boolean pendingCall = pending.contains(call.id);
                awaiting |= pendingCall;
                ToolCallEntry entry = new ToolCallEntry(call.id, call.name, call.arguments,
                        result == null ? "" : result.plainText(),
                        result != null && result.status == Message.Status.FAILED,
                        missing && (live || pendingCall),
                        diffIdOf(result), result == null ? "" : result.reviewState);
                group.add(entry);
                all.add(entry);
            }
        }
        flushGroup(process, group);
        String error = "";
        for (Message message : turn) {
            for (ContentBlock block : message.blocks) {
                if (block.type == ContentBlock.Type.ERROR && !block.text.trim().isEmpty()) {
                    error = block.text.trim();
                }
            }
        }
        long startedAt = turn.get(0).createdAt;
        long finishedAt = 0L;
        boolean failed = false;
        for (Message message : turn) {
            failed |= message.status == Message.Status.FAILED;
            if (message.updatedAt > finishedAt) {
                finishedAt = message.updatedAt;
            }
        }
        if (running || finishedAt < startedAt) {
            finishedAt = 0L;
        }
        Row.Draft draft = new Row.Draft();
        draft.key = "t:" + turn.get(0).id;
        draft.subject = answer == null ? turn.get(0) : answer;
        draft.answer = answer;
        draft.error = error;
        draft.process.addAll(process);
        draft.tools.addAll(all);
        draft.live = live;
        draft.running = running;
        draft.pending = awaiting;
        draft.failed = failed;
        draft.startedAt = startedAt;
        draft.finishedAt = finishedAt;
        rows.add(new Row(draft));
        turn.clear();
    }

    private static void flushGroup(List<Item> process, List<ToolCallEntry> group) {
        if (group.isEmpty()) {
            return;
        }
        List<ToolCallEntry> copy = new ArrayList<>(group);
        group.clear();
        process.add(new Item(ITEM_TOOLS, "tools:" + copy.get(0).callId, "", "", copy));
    }

    /**
     * Diff id stored on a tool result message.
     *
     * <p>Mutating tools persist their reviewable snapshot as a {@code DIFF} block on the
     * {@code Role.TOOL} message; handing that id to the card lets it render the real diff
     * instead of falling back to the request arguments.</p>
     */
    private static String diffIdOf(Message result) {
        if (result == null) {
            return "";
        }
        for (ContentBlock block : result.blocks) {
            if (block.type == ContentBlock.Type.DIFF && !block.refId.isEmpty()) {
                return block.refId;
            }
        }
        return "";
    }

    /**
     * A stopped stream is not an answer yet; a failed message may still carry the partial body
     * the user already saw, and that body belongs in the answer area (the plain-text error block
     * is rendered separately).
     */
    private static boolean isAnswer(Message message) {
        if (message.status == Message.Status.STREAMING) {
            return false;
        }
        if (!message.toolCalls.isEmpty()) {
            return false;
        }
        return !plainText(message).isEmpty();
    }

    private static String plainText(Message message) {
        StringBuilder text = new StringBuilder();
        for (ContentBlock block : message.blocks) {
            if (block.type == ContentBlock.Type.TEXT) {
                text.append(block.text);
            }
        }
        return text.toString();
    }
}
