package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.engine.protocol.AnthropicCodec;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.CodexCodec;
import cn.lineai.engine.protocol.OpenAiChatCodec;

/** One-shot non-streaming vision completion for the image understanding tool. */
public final class VisionClient {

    public String describe(ModelConfig model, String prompt, String imagePath) throws IOException {
        ChatRequest request = ChatRequest.builder()
                .model(model.modelId)
                .messages(java.util.Collections.singletonList(
                        Message.builder().id("vision").conversationId("vision").seq(1)
                                .role(Role.USER)
                                .addBlock(ContentBlock.text(prompt))
                                .addBlock(ContentBlock.image(imagePath))
                                .build()))
                .sampling(0.2f, 1024)
                .stream(false)
                .build();
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        String body;
        String url;
        switch (model.protocol) {
            case ANTHROPIC_MESSAGES:
                body = AnthropicCodec.requestBody(request);
                headers.put("x-api-key", model.apiKey);
                headers.put("anthropic-version", "2023-06-01");
                url = AnthropicCodec.messagesEndpoint(model.baseUrl);
                break;
            case CODEX_RESPONSES:
                body = CodexCodec.requestBody(request, model.baseUrl);
                headers.put("Authorization", "Bearer " + model.apiKey);
                headers.put("version", "0.120.0");
                headers.put("originator", "codex_cli_rs");
                url = ModelCatalogClient.endpoint(model.baseUrl, "/responses");
                break;
            case OPENAI_COMPATIBLE:
            case LOCAL_GGUF:
            default:
                body = OpenAiChatCodec.requestBody(request);
                headers.put("Authorization", "Bearer " + model.apiKey);
                url = ModelCatalogClient.endpoint(model.baseUrl, "/chat/completions");
                break;
        }
        String raw = ModelCatalogClient.execute("POST", url, headers, body);
        return parse(model, raw);
    }

    static String parse(ModelConfig model, String raw) throws IOException {
        try {
            if (model.protocol == ModelConfig.Protocol.ANTHROPIC_MESSAGES) {
                StringBuilder text = new StringBuilder();
                JSONArray content = new JSONObject(raw).optJSONArray("content");
                if (content != null) {
                    for (int i = 0; i < content.length(); i++) {
                        JSONObject block = content.optJSONObject(i);
                        if (block != null && "text".equals(block.optString("type"))) {
                            text.append(block.optString("text", ""));
                        }
                    }
                }
                if (text.length() > 0) return text.toString().trim();
            } else if (model.protocol == ModelConfig.Protocol.CODEX_RESPONSES) {
                JSONObject root = new JSONObject(raw);
                String text = root.optString("output_text", "");
                if (!text.isEmpty()) return text.trim();
                JSONArray output = root.optJSONArray("output");
                if (output != null) {
                    for (int i = 0; i < output.length(); i++) {
                        JSONArray content = output.optJSONObject(i).optJSONArray("content");
                        if (content == null) continue;
                        for (int j = 0; j < content.length(); j++) {
                            String value = content.optJSONObject(j).optString("text", "");
                            if (!value.isEmpty()) return value.trim();
                        }
                    }
                }
            } else {
                JSONArray choices = new JSONObject(raw).optJSONArray("choices");
                if (choices != null && choices.length() > 0) {
                    JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                    if (message != null) return message.optString("content", "").trim();
                    return choices.getJSONObject(0).optString("text", "").trim();
                }
            }
            return raw.trim();
        } catch (Exception error) {
            throw new IOException("Cannot parse vision response", error);
        }
    }
}
