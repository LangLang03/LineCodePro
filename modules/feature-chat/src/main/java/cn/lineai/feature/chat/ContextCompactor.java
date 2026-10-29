package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.data.api.ModelStore;
import cn.lineai.data.api.SettingsStore;

/** Summarizes older messages once the context crosses the compression threshold. */
final class ContextCompactor {

    interface Summarizer {
        String summarize(ModelConfig model, String prompt) throws Exception;
    }

    static final class Result {
        final List<Message> history;
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

    private ContextCompactor() {
    }

    static String key(String conversationId) {
        return KEY_PREFIX + conversationId;
    }

    static Result prepare(ModelConfig model, ModelStore models, SettingsStore settings,
                          String conversationId, List<Message> loaded, boolean softCompaction,
                          String instruction, Summarizer summarizer) {
        String summary = settings.get(key(conversationId), "");
        int tailStart = Math.max(0, loaded.size() - TAIL_MESSAGES);
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
        if (summary.isEmpty() || loaded.size() <= TAIL_MESSAGES) {
            return new Result(loaded, "");
        }
        return new Result(new ArrayList<>(loaded.subList(tailStart, loaded.size())), summary);
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
