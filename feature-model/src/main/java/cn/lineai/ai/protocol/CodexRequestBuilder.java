package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.tool.ToolInfo;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.UUID;
import org.json.JSONArray;
import org.json.JSONObject;

final class CodexRequestBuilder {

    static final String CODEX_PROTOCOL_VERSION = "0.120.0";
    static final String CODEX_ORIGINATOR = "codex_cli_rs";
    private static final String CODEX_INSTALLATION_ID = UUID.nameUUIDFromBytes(
            "cn.lineai.linecode.codex".getBytes(StandardCharsets.UTF_8)
    ).toString();
    private static final String CODEX_WINDOW_ID = CODEX_INSTALLATION_ID + ":0";

    JSONObject buildRequestBody(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelRequestOptions requestOptions
    ) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", ModelContextParser.apiModelId(config));
        body.put("input", ResponsesInputBuilder.inputJson(messages));
        body.put("stream", true);
        body.put("parallel_tool_calls", true);
        body.put("store", isAzureResponsesEndpoint(config.getBaseUrl()));
        JSONArray include = new JSONArray();
        putInstructions(body, messages);
        JSONArray tools = toolsJson(requestOptions.getTools());
        body.put("tools", tools);
        body.put("tool_choice", "auto");
        if (AiBehaviorSettings.isReasoningEnabled(requestOptions.getReasoningEffort())) {
            String effort = AiBehaviorSettings.concreteReasoningEffort(requestOptions.getReasoningEffort());
            body.put("reasoning", new JSONObject()
                    .put("effort", AiBehaviorSettings.REASONING_MAX.equals(effort) ? "high" : effort)
                    .put("summary", "auto"));
            include.put("reasoning.encrypted_content");
        }
        body.put("include", include);
        putCodexClientFields(body, config);
        return body;
    }

    JSONObject buildCompleteBody(ModelConfig config, List<ModelMessage> messages) throws Exception {
        JSONObject body = new JSONObject();
        body.put("model", ModelContextParser.apiModelId(config));
        body.put("input", ResponsesInputBuilder.inputJson(messages));
        body.put("tools", new JSONArray());
        body.put("tool_choice", "auto");
        body.put("parallel_tool_calls", true);
        body.put("store", isAzureResponsesEndpoint(config.getBaseUrl()));
        body.put("include", new JSONArray());
        putInstructions(body, messages);
        putCodexClientFields(body, config);
        return body;
    }

    HashMap<String, String> codexHeaders(String apiKey) {
        HashMap<String, String> headers = new HashMap<>();
        headers.put("Authorization", "Bearer " + apiKey);
        headers.put("version", CODEX_PROTOCOL_VERSION);
        headers.put("originator", CODEX_ORIGINATOR);
        headers.put("User-Agent", codexUserAgent());
        return headers;
    }

    static String codexUserAgent() {
        return CODEX_ORIGINATOR + "/" + CODEX_PROTOCOL_VERSION + " (Android; LineCode)";
    }

    static boolean isAzureResponsesEndpoint(String baseUrl) {
        if (baseUrl == null) {
            return false;
        }
        String normalized = baseUrl.toLowerCase(java.util.Locale.US);
        return normalized.contains("openai.azure.")
                || normalized.contains("cognitiveservices.azure.")
                || normalized.contains("aoai.azure.")
                || normalized.contains("azure-api.")
                || normalized.contains("azurefd.")
                || normalized.contains("windows.net/openai");
    }

    String responsesEndpoint(String baseUrl) {
        String base = baseUrl == null ? "" : baseUrl.trim();
        while (base.endsWith("/")) {
            base = base.substring(0, base.length() - 1);
        }
        if (base.endsWith("/chat/completions")) {
            return base.substring(0, base.length() - "/chat/completions".length()) + "/responses";
        }
        if (base.endsWith("/responses")) {
            return base;
        }
        return base + "/responses";
    }

    private void putCodexClientFields(JSONObject body, ModelConfig config) throws Exception {
        body.put("prompt_cache_key", promptCacheKey(config));
        body.put("client_metadata", new JSONObject()
                .put("x-codex-installation-id", CODEX_INSTALLATION_ID)
                .put("x-codex-window-id", CODEX_WINDOW_ID));
    }

    private String promptCacheKey(ModelConfig config) {
        String model = config == null ? "" : ModelContextParser.apiModelId(config);
        return "linecode-codex-" + Integer.toHexString(model.hashCode());
    }

    private void putInstructions(JSONObject body, List<ModelMessage> messages) throws Exception {
        String instructions = ResponsesInputBuilder.instructions(messages);
        if (instructions.length() > 0) {
            body.put("instructions", instructions);
        }
    }

    private JSONArray toolsJson(List<ToolInfo> tools) throws Exception {
        JSONArray array = new JSONArray();
        JSONArray openAiTools = ToolInfo.toJsonArray(tools);
        for (int i = 0; i < openAiTools.length(); i++) {
            JSONObject tool = openAiTools.optJSONObject(i);
            if (tool == null) {
                continue;
            }
            JSONObject function = tool.optJSONObject("function");
            if ("function".equals(tool.optString("type")) && function != null) {
                array.put(new JSONObject()
                        .put("type", "function")
                        .put("name", function.optString("name"))
                        .put("description", function.optString("description"))
                        .put("strict", false)
                        .put("parameters", function.optJSONObject("parameters") == null
                                ? new JSONObject().put("type", "object").put("properties", new JSONObject())
                                : function.optJSONObject("parameters")));
            } else {
                array.put(tool);
            }
        }
        return array;
    }
}
