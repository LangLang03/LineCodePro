package cn.lineai.mvp;

import android.os.SystemClock;
import cn.lineai.ai.ToolCallTextParser;
import java.util.HashMap;

public class StreamingRenderController {

    private static final long STREAM_RENDER_INTERVAL_MS = 80L;
    private static final java.util.regex.Pattern PROCESSING_END_MARKER = java.util.regex.Pattern.compile("<LEOF\\s+\"[^\"]*\">", java.util.regex.Pattern.DOTALL);

    private final MainThreadDispatcher mainThread;
    private final FlushCallback flushCallback;
    private final ActiveGenerationChecker activeGenerationChecker;

    private final HashMap<String, StringBuilder> rawTextByMessageId = new HashMap<>();
    private final StringBuilder pendingTextDelta = new StringBuilder();
    private final StringBuilder pendingReasoningDelta = new StringBuilder();

    private int pendingGenerationId = -1;
    private String pendingAssistantId = "";
    private boolean streamRenderScheduled;
    private long lastStreamRenderAt;

    private FlushResult lastFlushResult;

    public interface FlushCallback {
        void onFlush(FlushResult result);
    }

    public StreamingRenderController(MainThreadDispatcher mainThread,
                                     FlushCallback flushCallback,
                                     ActiveGenerationChecker activeGenerationChecker) {
        this.mainThread = mainThread;
        this.flushCallback = flushCallback;
        this.activeGenerationChecker = activeGenerationChecker;
    }

    public void initRawText(String assistantId) {
        rawTextByMessageId.put(assistantId, new StringBuilder());
    }

    public StringBuilder removeRawText(String assistantId) {
        return rawTextByMessageId.remove(assistantId);
    }

    public void appendDelta(int generationId, String assistantId, String textDelta, String reasoningDelta) {
        mainThread.post(() -> {
            if (!activeGenerationChecker.isActiveGeneration(generationId)) {
                return;
            }
            if (pendingGenerationId != generationId || !pendingAssistantId.equals(assistantId)) {
                flush();
                pendingGenerationId = generationId;
                pendingAssistantId = assistantId;
            }
            if (textDelta != null && textDelta.length() > 0) {
                pendingTextDelta.append(textDelta);
            }
            if (reasoningDelta != null && reasoningDelta.length() > 0) {
                pendingReasoningDelta.append(reasoningDelta);
            }
            scheduleFlush();
        });
    }

    public void flush() {
        streamRenderScheduled = false;
        if (pendingTextDelta.length() == 0 && pendingReasoningDelta.length() == 0) {
            lastFlushResult = null;
            return;
        }
        int generationId = pendingGenerationId;
        String assistantId = pendingAssistantId;
        String textDelta = pendingTextDelta.toString();
        String reasoningDelta = pendingReasoningDelta.toString();
        pendingTextDelta.setLength(0);
        pendingReasoningDelta.setLength(0);
        pendingGenerationId = -1;
        pendingAssistantId = "";

        StringBuilder rawText = rawTextByMessageId.get(assistantId);
        if (rawText == null) {
            rawText = new StringBuilder();
            rawTextByMessageId.put(assistantId, rawText);
        }
        rawText.append(textDelta);

        String rawTextStr = rawText.toString();
        ToolCallTextParser.Result parsedToolCalls = ToolCallTextParser.parseStreamingPreview(rawTextStr);

        lastFlushResult = new FlushResult(generationId, assistantId, textDelta, reasoningDelta, rawTextStr, parsedToolCalls);
        lastStreamRenderAt = SystemClock.uptimeMillis();
        flushCallback.onFlush(lastFlushResult);
    }

    public void clear() {
        rawTextByMessageId.clear();
        pendingTextDelta.setLength(0);
        pendingReasoningDelta.setLength(0);
        pendingGenerationId = -1;
        pendingAssistantId = "";
        streamRenderScheduled = false;
        lastFlushResult = null;
    }

    public FlushResult getLastFlushResult() {
        return lastFlushResult;
    }

    public static boolean hasProcessingEndMarker(String raw) {
        return raw != null && PROCESSING_END_MARKER.matcher(raw).find();
    }

    /** Removes the internal final-answer capture markers from model output. */
    public static String visibleCapturedText(String raw) {
        if (raw == null || raw.length() == 0) return "";
        String cleaned = raw;
        int marker = cleaned.indexOf("<L");
        if (marker < 0) return cleaned;
        int markerEnd = cleaned.indexOf("\">", marker + 2);
        if (markerEnd < 0) return cleaned.substring(0, marker);
        return cleaned.substring(markerEnd + 2);
        /*
        int eof = cleaned.indexOf("<LEOF");
        while (eof >= 0) {
            int eofEnd = cleaned.indexOf("\">", eof + 5);
            if (eofEnd < 0) return cleaned.substring(0, eof);
            cleaned = cleaned.substring(0, eof) + cleaned.substring(eofEnd + 2);
            eof = cleaned.indexOf("<LEOF");
        }
        int start = cleaned.indexOf("<L");
        if (start < 0) return cleaned;
        String before = cleaned.substring(0, start);
        int end = cleaned.indexOf("\">", start + 2);
        if (end < 0) return before;
        String after = cleaned.substring(end + 2);
        return before + after;*/
    }

    private void scheduleFlush() {
        if (streamRenderScheduled) {
            return;
        }
        long now = SystemClock.uptimeMillis();
        long delay = Math.max(0L, STREAM_RENDER_INTERVAL_MS - (now - lastStreamRenderAt));
        streamRenderScheduled = true;
        mainThread.postDelayed(this::flush, delay);
    }

    public interface ActiveGenerationChecker {
        boolean isActiveGeneration(int generationId);
    }

    public static final class FlushResult {
        public final int generationId;
        public final String assistantId;
        public final String textDelta;
        public final String reasoningDelta;
        public final String rawText;
        public final ToolCallTextParser.Result parsedToolCalls;

        FlushResult(int generationId, String assistantId, String textDelta, String reasoningDelta,
                    String rawText, ToolCallTextParser.Result parsedToolCalls) {
            this.generationId = generationId;
            this.assistantId = assistantId;
            this.textDelta = textDelta;
            this.reasoningDelta = reasoningDelta;
            this.rawText = rawText;
            this.parsedToolCalls = parsedToolCalls;
        }
    }
}
