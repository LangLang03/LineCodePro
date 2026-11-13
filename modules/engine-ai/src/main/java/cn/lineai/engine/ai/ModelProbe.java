package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.ModelConfig;

/**
 * One-shot non-streaming completion used by the "Test" action in the model form.
 *
 * <p>The probe is intentionally tiny (64 max tokens, no tools, no reasoning) so that it verifies
 * reachability and credentials without generating real content. Transport failures surface as
 * {@link DomainException} with {@link ErrorCode#NETWORK}; parse problems fall back to the raw
 * body so the user always sees something.</p>
 */
public final class ModelProbe {

    /** Canonical probe prompt sent by the single-argument {@link #complete(ModelConfig)}. */
    public static final String PROMPT = "Calculate 1+1 and reply with any result.";

    /**
     * Sends the canonical probe prompt.
     *
     * @param model model configuration to test
     * @return the model's reply text
     * @throws DomainException with {@link ErrorCode#NETWORK} on transport failure
     */
    public String complete(ModelConfig model) {
        return complete(model, PROMPT);
    }

    /**
     * Sends one probe prompt and extracts the reply.
     *
     * @param model model configuration to test
     * @param prompt prompt text to send
     * @return the model's reply text, or the raw body when no known response shape matches
     * @throws DomainException with {@link ErrorCode#NETWORK} on transport failure
     */
    public String complete(ModelConfig model, String prompt) {
        try {
            switch (model.protocol) {
                case ANTHROPIC_MESSAGES:
                    return anthropic(model, prompt);
                case CODEX_RESPONSES:
                    return codex(model, prompt);
                case OPENAI_COMPATIBLE:
                case LOCAL_GGUF:
                default:
                    return openAi(model, prompt);
            }
        } catch (IOException error) {
            throw new DomainException(ErrorCode.NETWORK,
                    error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage(),
                    error);
        }
    }

    /** Sends a non-streaming chat completion and extracts the reply from the choices array. */
    private String openAi(ModelConfig model, String prompt) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model.modelId);
            body.put("stream", false);
            body.put("max_tokens", 64);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", messages);
        } catch (JSONException error) {
            throw new IOException("Cannot build request", error);
        }
        Map<String, String> headers = headers();
        headers.put("Authorization", "Bearer " + model.apiKey);
        String raw = ModelCatalogClient.execute("POST",
                OpenAiChatCodecEndpoint.chatCompletions(model.baseUrl), headers, body.toString());
        try {
            JSONObject root = new JSONObject(raw);
            JSONArray choices = root.optJSONArray("choices");
            if (choices != null && choices.length() > 0) {
                JSONObject message = choices.getJSONObject(0).optJSONObject("message");
                if (message != null) return message.optString("content", "").trim();
                return choices.getJSONObject(0).optString("text", "").trim();
            }
            return raw.trim();
        } catch (JSONException error) {
            return raw.trim();
        }
    }

    /** Sends a minimal Messages API request and joins the reply's text blocks. */
    private String anthropic(ModelConfig model, String prompt) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model.modelId);
            body.put("max_tokens", 64);
            JSONArray messages = new JSONArray();
            messages.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("messages", messages);
        } catch (JSONException error) {
            throw new IOException("Cannot build request", error);
        }
        Map<String, String> headers = headers();
        headers.put("x-api-key", model.apiKey);
        headers.put("anthropic-version", "2023-06-01");
        String url = cn.lineai.engine.protocol.AnthropicCodec.messagesEndpoint(model.baseUrl);
        String raw = ModelCatalogClient.execute("POST", url, headers, body.toString());
        try {
            JSONArray content = new JSONObject(raw).optJSONArray("content");
            StringBuilder text = new StringBuilder();
            if (content != null) {
                for (int i = 0; i < content.length(); i++) {
                    JSONObject block = content.optJSONObject(i);
                    if (block != null && "text".equals(block.optString("type"))) {
                        text.append(block.optString("text", ""));
                    }
                }
            }
            return text.length() > 0 ? text.toString().trim() : raw.trim();
        } catch (JSONException error) {
            return raw.trim();
        }
    }

    /** Sends a minimal Responses API request and extracts the reply from the output items. */
    private String codex(ModelConfig model, String prompt) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("model", model.modelId);
            body.put("stream", false);
            JSONArray input = new JSONArray();
            input.put(new JSONObject().put("role", "user").put("content", prompt));
            body.put("input", input);
        } catch (JSONException error) {
            throw new IOException("Cannot build request", error);
        }
        Map<String, String> headers = headers();
        headers.put("Authorization", "Bearer " + model.apiKey);
        headers.put("version", "0.120.0");
        headers.put("originator", "codex_cli_rs");
        String url = ModelCatalogClient.endpoint(model.baseUrl, "/responses");
        String raw = ModelCatalogClient.execute("POST", url, headers, body.toString());
        try {
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
            return raw.trim();
        } catch (JSONException error) {
            return raw.trim();
        }
    }

    /** Returns the header map shared by all probe requests. */
    private static Map<String, String> headers() {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "application/json");
        return headers;
    }

    /** Resolves the OpenAI-compatible chat completions endpoint for probe requests. */
    private static final class OpenAiChatCodecEndpoint {
        private static String chatCompletions(String baseUrl) {
            return ModelCatalogClient.endpoint(baseUrl, "/chat/completions");
        }
    }
}
