package cn.lineai.model;

import org.json.JSONObject;

public final class MessageContentSanitizer {
    private static final String IMAGE_GENERATED_FALLBACK = "Image generated and displayed in conversation.";

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
        if (cn.lineai.tool.ToolNames.IMAGE_GENERATION.equals(message.getToolName()) && !message.isError()) {
            if (!looksLikeJsonObject(content)) {
                // 旧行为：非 JSON 的图片工具输出回退到固定说明文本。
                return IMAGE_GENERATED_FALLBACK;
            }
            try {
                JSONObject object = new JSONObject(content);
                if (object.optBoolean("linecode_image_generation")) {
                    String modelContent = object.optString("model_content");
                    return modelContent.trim().length() > 0 ? modelContent : IMAGE_GENERATED_FALLBACK;
                }
            } catch (Exception ignored) {
                return IMAGE_GENERATED_FALLBACK;
            }
        }
        // 渲染热路径会对每条工具消息调用本方法：普通工具输出（shell/文件文本）既不是 JSON
        // 也不含 linecode_ 标记，直接走快速路径，避免每次 render 全量解析大段 JSON。
        if (!looksLikeJsonObject(content) || content.indexOf("linecode_") < 0) {
            return stripInlineDataImages(content);
        }
        try {
            JSONObject object = new JSONObject(content);
            if (object.optBoolean("linecode_agent_ref")) {
                return stripInlineDataImages(content);
            }
            if (!object.optBoolean("linecode_agent_progress")) {
                return stripInlineDataImages(content);
            }
            String modelContent = object.optString("model_content");
            if (modelContent.trim().length() > 0) {
                return modelContent;
            }
            String output = object.optString("output");
            return output.trim().length() > 0 ? output : "Agent is still running, final result not yet generated.";
        } catch (Exception ignored) {
            return stripInlineDataImages(content);
        }
    }

    private static boolean looksLikeJsonObject(String content) {
        for (int i = 0; i < content.length(); i++) {
            char c = content.charAt(i);
            if (Character.isWhitespace(c)) {
                continue;
            }
            return c == '{';
        }
        return false;
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
        int first = text.indexOf("data:image/");
        if (first < 0) {
            // 绝大多数消息不含内联图片：直接返回原实例，避免每次 render 拷贝整段对话正文。
            return text;
        }
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
