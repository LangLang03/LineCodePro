package cn.lineai.model;

import org.json.JSONObject;

public final class MessageContentSanitizer {
    private MessageContentSanitizer() {
    }

    public static String forModel(ChatMessage message) {
        if (message == null) {
            return "";
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return toolContentForModel(message);
        }
        return stripInlineDataImages(message.getContent());
    }

    public static String toolContentForModel(ChatMessage message) {
        if (message == null) {
            return "";
        }
        String content = message.getContent();
        if (content == null || content.trim().length() == 0) {
            return "";
        }
        if ("image_generation".equals(message.getToolName()) && !message.isError()) {
            try {
                JSONObject object = new JSONObject(content);
                if (object.optBoolean("linecode_image_generation")) {
                    String modelContent = object.optString("model_content");
                    return modelContent.trim().length() > 0 ? modelContent : "图片已生成并已在对话中显示。";
                }
            } catch (Exception ignored) {
                return "图片已生成并已在对话中显示。";
            }
        }
        try {
            JSONObject object = new JSONObject(content);
            if (!object.optBoolean("linecode_agent_progress")) {
                return stripInlineDataImages(content);
            }
            String modelContent = object.optString("model_content");
            if (modelContent.trim().length() > 0) {
                return modelContent;
            }
            String output = object.optString("output");
            return output.trim().length() > 0 ? output : "Agent 仍在运行，尚未生成最终结果。";
        } catch (Exception ignored) {
            return stripInlineDataImages(content);
        }
    }

    public static String imageGenerationDisplayMarkdown(String content) {
        try {
            JSONObject object = new JSONObject(content == null ? "" : content);
            if (object.optBoolean("linecode_image_generation")) {
                return object.optString("display_markdown").trim();
            }
        } catch (Exception ignored) {
        }
        return "";
    }

    public static String stripInlineDataImages(String content) {
        String text = content == null ? "" : content;
        StringBuilder builder = new StringBuilder(text.length());
        int cursor = 0;
        while (cursor < text.length()) {
            int start = text.indexOf("data:image/", cursor);
            if (start < 0) {
                builder.append(text.substring(cursor));
                break;
            }
            builder.append(text, cursor, start);
            int end = endOfDataUrl(text, start);
            builder.append("linecode-inline-image");
            cursor = end;
        }
        return builder.toString();
    }

    private static int endOfDataUrl(String text, int start) {
        int end = start;
        while (end < text.length()) {
            char c = text.charAt(end);
            if (c == ')' || c == '"' || c == '\'' || Character.isWhitespace(c)) {
                break;
            }
            end++;
        }
        return end;
    }
}
