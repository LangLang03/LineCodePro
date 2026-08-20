package cn.lineai.ai.protocol.reasoning;

import org.json.JSONArray;
import org.json.JSONObject;
import java.util.ArrayList;
import java.util.List;

public final class ReasoningDeltaExtractor {
    private final List<DeltaStrategy> strategies;
    private final ReasoningFieldStrategy reasoningFieldStrategy = new ReasoningFieldStrategy();
    private final ReasoningDetailsStrategy reasoningDetailsStrategy = new ReasoningDetailsStrategy();
    private final AdjacentBoldSummarySpacer summarySpacer = new AdjacentBoldSummarySpacer();
    private final TrailingSummaryWhitespaceNormalizer summaryWhitespaceNormalizer =
            new TrailingSummaryWhitespaceNormalizer();

    public ReasoningDeltaExtractor() {
        strategies = new ArrayList<>();
        strategies.add(new ReasoningContentStrategy());
        strategies.add(reasoningFieldStrategy);
        strategies.add(reasoningDetailsStrategy);
    }

    public String extract(JSONObject delta) {
        for (DeltaStrategy strategy : strategies) {
            String result = strategy.extract(delta);
            if (result.length() > 0) {
                if (strategy == reasoningFieldStrategy) {
                    return summaryWhitespaceNormalizer.flush() + summarySpacer.append(result);
                }
                if (strategy == reasoningDetailsStrategy) {
                    return summarySpacer.flush() + summaryWhitespaceNormalizer.append(result);
                }
                return summarySpacer.flush() + summaryWhitespaceNormalizer.flush() + result;
            }
        }
        return "";
    }

    public String flush() {
        return summarySpacer.flush() + summaryWhitespaceNormalizer.flush();
    }

    interface DeltaStrategy {
        String extract(JSONObject delta);
    }

    private static final class ReasoningContentStrategy implements DeltaStrategy {
        @Override
        public String extract(JSONObject delta) {
            if (delta.has("reasoning_content") && !delta.isNull("reasoning_content")) {
                return delta.optString("reasoning_content");
            }
            return "";
        }
    }

    private static final class ReasoningFieldStrategy implements DeltaStrategy {
        @Override
        public String extract(JSONObject delta) {
            Object reasoning = delta.opt("reasoning");
            if (reasoning instanceof String) {
                return (String) reasoning;
            }
            if (reasoning instanceof JSONObject) {
                JSONObject object = (JSONObject) reasoning;
                if (object.has("content")) {
                    return object.optString("content");
                }
                if (object.has("text")) {
                    return object.optString("text");
                }
            }
            return "";
        }
    }

    private static final class ReasoningDetailsStrategy implements DeltaStrategy {
        private boolean hasSummary;
        private String lastSummaryKey = "";

        @Override
        public String extract(JSONObject delta) {
            Object details = delta.opt("reasoning_details");
            if (details instanceof JSONObject) {
                return fromObject((JSONObject) details);
            }
            if (details instanceof JSONArray) {
                return fromArray((JSONArray) details);
            }
            return "";
        }

        private String fromObject(JSONObject object) {
            if (object.has("summary")) {
                return textValue(object.opt("summary"));
            }
            if (object.has("content")) {
                return textValue(object.opt("content"));
            }
            if (object.has("text")) {
                return textValue(object.opt("text"));
            }
            if (object.has("reasoning_content")) {
                return textValue(object.opt("reasoning_content"));
            }
            return "";
        }

        private String textValue(Object value) {
            if (value instanceof String) {
                return (String) value;
            }
            if (value instanceof JSONObject) {
                return fromObject((JSONObject) value);
            }
            if (value instanceof JSONArray) {
                return fromArray((JSONArray) value);
            }
            return "";
        }

        private String fromArray(JSONArray array) {
            StringBuilder builder = new StringBuilder();
            int unkeyedSummariesInChunk = 0;
            for (int i = 0; i < array.length(); i++) {
                Object item = array.opt(i);
                JSONObject object = item instanceof JSONObject ? (JSONObject) item : null;
                String segment = item instanceof String
                        ? (String) item
                        : object == null ? "" : fromObject(object);
                if (segment.length() == 0) {
                    continue;
                }
                boolean summary = object != null && "reasoning.summary".equals(object.optString("type"));
                boolean newSummaryBlock = false;
                if (summary) {
                    String summaryKey = summaryKey(object);
                    newSummaryBlock = hasSummary && (summaryKey.length() > 0
                            ? !summaryKey.equals(lastSummaryKey)
                            : unkeyedSummariesInChunk > 0);
                    hasSummary = true;
                    lastSummaryKey = summaryKey;
                    unkeyedSummariesInChunk++;
                }
                if (newSummaryBlock) {
                    while (builder.length() > 0
                            && Character.isWhitespace(builder.charAt(builder.length() - 1))) {
                        builder.setLength(builder.length() - 1);
                    }
                    builder.append(" | ");
                    int segmentStart = 0;
                    while (segmentStart < segment.length()
                            && Character.isWhitespace(segment.charAt(segmentStart))) {
                        segmentStart++;
                    }
                    builder.append(segment, segmentStart, segment.length());
                } else {
                    builder.append(segment);
                }
            }
            return builder.toString();
        }

        private static String summaryKey(JSONObject object) {
            String id = object.optString("id");
            if (id.length() > 0) {
                return "id:" + id;
            }
            if (object.has("index") && !object.isNull("index")) {
                return "index:" + object.opt("index");
            }
            return "";
        }
    }


    private static final class TrailingSummaryWhitespaceNormalizer {
        private final StringBuilder pendingWhitespace = new StringBuilder();

        String append(String value) {
            if (value == null || value.length() == 0) {
                return "";
            }
            StringBuilder output = new StringBuilder(value.length() + pendingWhitespace.length());
            boolean summaryBoundary = value.startsWith(" | ");
            if (!summaryBoundary && pendingWhitespace.length() > 0) {
                output.append(pendingWhitespace);
            }
            pendingWhitespace.setLength(0);

            int end = value.length();
            while (end > 0 && Character.isWhitespace(value.charAt(end - 1))) {
                end--;
            }
            output.append(value, 0, end);
            pendingWhitespace.append(value, end, value.length());
            return output.toString();
        }

        String flush() {
            String trailing = pendingWhitespace.toString();
            pendingWhitespace.setLength(0);
            return trailing;
        }
    }
    private static final class AdjacentBoldSummarySpacer {
        private int pendingAsterisks;
        private boolean hasEmitted;
        private char lastEmitted;
        private boolean boldOpen;

        String append(String value) {
            if (value == null || value.length() == 0) {
                return "";
            }
            StringBuilder output = new StringBuilder(value.length() + 1);
            for (int i = 0; i < value.length(); i++) {
                char current = value.charAt(i);
                if (current == '*') {
                    pendingAsterisks++;
                    continue;
                }
                emitPending(output, current);
                output.append(current);
                hasEmitted = true;
                lastEmitted = current;
            }
            return output.toString();
        }

        String flush() {
            if (pendingAsterisks == 0) {
                boldOpen = false;
                return "";
            }
            StringBuilder output = new StringBuilder(pendingAsterisks);
            appendAsterisks(output, pendingAsterisks);
            pendingAsterisks = 0;
            hasEmitted = true;
            lastEmitted = '*';
            boldOpen = false;
            return output.toString();
        }

        private void emitPending(StringBuilder output, char next) {
            if (pendingAsterisks == 0) {
                return;
            }
            if (pendingAsterisks == 4
                    && boldOpen
                    && hasEmitted
                    && !Character.isWhitespace(lastEmitted)
                    && !Character.isWhitespace(next)) {
                output.append("** | **");
            } else {
                appendAsterisks(output, pendingAsterisks);
                if (pendingAsterisks == 2) {
                    boldOpen = !boldOpen;
                }
            }
            pendingAsterisks = 0;
            hasEmitted = true;
            lastEmitted = '*';
        }

        private static void appendAsterisks(StringBuilder output, int count) {
            for (int i = 0; i < count; i++) {
                output.append('*');
            }
        }
    }
}
