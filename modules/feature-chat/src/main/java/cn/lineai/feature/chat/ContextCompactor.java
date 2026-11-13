package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.SettingsStore;

/**
 * Summarizes older messages once the context crosses the compression threshold.
 *
 * <p>Automatic compaction runs at 80% of the context window, or at 50% when soft compaction is
 * enabled, and always leaves a recent tail untouched. The tail boundary is snapped past raw
 * TOOL rows so the wire history never opens with an orphan tool result. The summary itself is
 * persisted per conversation in settings under {@link #key}.</p>
 */
final class ContextCompactor {

    /** Pluggable summarization backend; production wiring calls the model. */
    interface Summarizer {
        String summarize(ModelConfig model, String prompt) throws Exception;
    }

    /** Wire history plus the running summary that replaces the summarized range. */
    static final class Result {
        /** Messages actually sent to the model (tail plus later rows). */
        final List<Message> history;
        /** Persisted summary text; empty when nothing was compacted. */
        final String summary;

        Result(List<Message> history, String summary) {
            this.history = history;
            this.summary = summary == null ? "" : summary;
        }
    }

    private static final String KEY_PREFIX = "context.summary.";
    private static final int TAIL_MESSAGES = 16;
    private static final int SOFT_THRESHOLD_PERCENT = 50;
    private static final int HARD_THRESHOLD_PERCENT = 80;
    private static final int MAX_PROMPT_CHARS = 30_000;

    /** Static utility; not instantiable. */
    private ContextCompactor() {
    }

    /** Settings key holding the persisted summary for one conversation. */
    static String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    /**
     * Prepares the wire history for the next model turn, compacting first when the current
     * usage crosses the hard (80%) or soft (50%) threshold and compression is enabled for the
     * model. Always returns the safe tail plus summary, even when compaction was skipped.
     */
    static Result prepare(ModelConfig model, ModelStore models, SettingsStore settings,
                          String conversationId, List<Message> loaded, boolean softCompaction,
                          String instruction, Summarizer summarizer) {
        String summary = settings.get(key(conversationId), "");
        // The boundary is snapped past raw TOOL rows first, so the summarized range and the
        // returned tail cover the message list completely with no gap in between.
        int tailStart = safeTailStart(loaded, Math.max(0, loaded.size() - TAIL_MESSAGES));
        int threshold = softCompaction ? SOFT_THRESHOLD_PERCENT : HARD_THRESHOLD_PERCENT;
        int contextSize = ChatContextLimits.of(model, settings);
        if (model.compressionEnabled && model.compressionAuto
                && tailStart > 0
                && ChatTurnConfig.percent(loaded, contextSize) >= threshold) {
            ModelConfig compressor = model;
            if (!model.compressionModelId.isEmpty() && models != null) {
                ModelConfig custom = models.find(model.compressionModelId);
                if (custom != null) compressor = custom;
            }
            try {
                String text = summarizer.summarize(compressor,
                        prompt(instruction, summary, loaded.subList(0, tailStart)));
                if (text != null && !text.trim().isEmpty()) {
                    summary = text.trim();
                    settings.put(key(conversationId), summary);
                }
            } catch (Exception error) {
                System.err.println("Context compaction failed: " + error);
            }
        }
        if (summary.isEmpty() || loaded.size() <= TAIL_MESSAGES || tailStart >= loaded.size()) {
            return new Result(loaded, "");
        }
        return new Result(new ArrayList<>(loaded.subList(tailStart, loaded.size())), summary);
    }

    /**
     * The wire history must never open with a TOOL row: its owning assistant tool_calls
     * would sit in the summarized range and strict providers reject the orphan tool_result.
     * Advance past any leading tool rows so they are summarized away with the older range.
     */
    private static int safeTailStart(List<Message> loaded, int tailStart) {
        while (tailStart < loaded.size() && loaded.get(tailStart).role == Role.TOOL) {
            tailStart++;
        }
        return Math.min(tailStart, loaded.size());
    }

    /**
     * Manual compaction: summarizes everything except the recent tail regardless of the
     * automatic thresholds. Returns false when the conversation is too short to compact.
     */
    static boolean compactNow(ModelConfig model, ModelStore models, SettingsStore settings,
                              String conversationId, List<Message> loaded, String instruction,
                              Summarizer summarizer)
            throws Exception {
        if (model == null || loaded.size() <= TAIL_MESSAGES) {
            return false;
        }
        int tailStart = loaded.size() - TAIL_MESSAGES;
        String summary = settings.get(key(conversationId), "");
        ModelConfig compressor = model;
        if (!model.compressionModelId.isEmpty() && models != null) {
            ModelConfig custom = models.find(model.compressionModelId);
            if (custom != null) compressor = custom;
        }
        String text = summarizer.summarize(compressor,
                prompt(instruction, summary, loaded.subList(0, tailStart)));
        if (text == null || text.trim().isEmpty()) {
            return false;
        }
        settings.put(key(conversationId), text.trim());
        return true;
    }

    /** Builds the summarization prompt: instruction, previous summary, then truncated messages. */
    private static String prompt(String instruction, String previous, List<Message> messages) {
        StringBuilder out = new StringBuilder();
        String template = instruction == null ? "" : instruction.trim();
        if (!template.isEmpty()) {
            out.append(template).append("\n\n");
        }
        if (!previous.isEmpty()) {
            out.append("Previous summary:\n").append(previous).append("\n\n");
        }
        out.append("Messages:\n");
        for (Message message : messages) {
            String text = message.plainText().trim();
            if (text.isEmpty()) continue;
            if (text.length() > 2000) text = text.substring(0, 2000) + "…";
            out.append('[').append(message.role.wireName()).append("] ")
                    .append(text).append('\n');
            if (out.length() > MAX_PROMPT_CHARS) break;
        }
        return out.toString();
    }
}
