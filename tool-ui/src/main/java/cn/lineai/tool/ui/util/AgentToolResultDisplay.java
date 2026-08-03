package cn.lineai.tool.ui;

import org.json.JSONArray;
import org.json.JSONObject;

/**
 * Pure display helpers for agent / agent-pipeline tool results.
 * Avoids dumping raw {@code linecode_agent_progress} JSON into Markdown when
 * the structured payload can be parsed (including after interrupt sanitization).
 */
public final class AgentToolResultDisplay {
    private AgentToolResultDisplay() {
    }

    public static JSONObject progressPayload(String content) {
        if (content == null || content.trim().length() == 0) {
            return null;
        }
        try {
            JSONObject object = new JSONObject(content);
            if (object.optBoolean("linecode_agent_ref")
                    || object.optBoolean("linecode_agent_progress")
                    || object.optBoolean("linecode_agent_pipeline_progress")) {
                return object;
            }
            return null;
        } catch (Exception ignored) {
            return null;
        }
    }

    public static boolean isAgentRef(String content) {
        JSONObject object = progressPayload(content);
        return object != null && object.optBoolean("linecode_agent_ref", false);
    }

    public static String agentId(String content) {
        JSONObject object = progressPayload(content);
        return object == null ? "" : object.optString("agent_id", "").trim();
    }

    public static String preview(String content) {
        JSONObject object = progressPayload(content);
        if (object == null) {
            return "";
        }
        String preview = object.optString("preview", "").trim();
        if (preview.length() > 0) {
            return preview;
        }
        return object.optString("output", "").trim();
    }

    public static String displayOutput(String content) {
        JSONObject progress = progressPayload(content);
        if (progress != null) {
            if (progress.optBoolean("linecode_agent_ref", false)) {
                String preview = progress.optString("preview", "").trim();
                if (preview.length() > 0) {
                    return preview;
                }
                return "";
            }
            String output = progress.optString("output", "").trim();
            if (output.length() > 0) {
                return output;
            }
            String modelContent = progress.optString("model_content", "").trim();
            if (modelContent.length() > 0) {
                return modelContent;
            }
            return "";
        }
        if (content == null) {
            return "";
        }
        String trimmed = content.trim();
        if (looksLikeJsonObjectOrArray(trimmed)) {
            // Unknown JSON (not our progress envelope) — still avoid raw dump as "output".
            return "";
        }
        int index = trimmed.indexOf("输出:\n");
        if (index >= 0) {
            return trimmed.substring(index + "输出:\n".length()).trim();
        }
        return trimmed;
    }

    public static String progressStatus(String content) {
        JSONObject progress = progressPayload(content);
        return progress == null ? "" : progress.optString("status", "");
    }

    public static JSONArray nestedToolCalls(String content) {
        JSONObject progress = progressPayload(content);
        return progress == null ? null : progress.optJSONArray("tool_calls");
    }

    public static int toolCallCount(String content) {
        JSONObject progress = progressPayload(content);
        if (progress == null) {
            return 0;
        }
        JSONArray calls = progress.optJSONArray("tool_calls");
        return progress.optInt("tool_call_count", calls == null ? 0 : calls.length());
    }

    public static String description(String content, String fallback) {
        JSONObject progress = progressPayload(content);
        if (progress != null) {
            String value = progress.optString("description", "").trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public static String type(String content, String fallback) {
        JSONObject progress = progressPayload(content);
        if (progress != null) {
            String value = progress.optString("type", "").trim();
            if (value.length() > 0) {
                return value;
            }
        }
        return fallback == null ? "" : fallback;
    }

    public static String thinking(String content) {
        JSONObject progress = progressPayload(content);
        return progress == null ? "" : progress.optString("thinking", "");
    }

    private static boolean looksLikeJsonObjectOrArray(String trimmed) {
        return (trimmed.startsWith("{") && trimmed.endsWith("}"))
                || (trimmed.startsWith("[") && trimmed.endsWith("]"));
    }
}
