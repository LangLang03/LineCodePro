package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelContextParser;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

public final class AnthropicMessagesProtocol extends AbstractHttpModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        try {
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("max_tokens", 4096);
            body.put("messages", messagesJson(messages));

            String system = systemPrompt(messages);
            if (system.length() > 0) {
                body.put("system", system);
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("x-api-key", config.getApiKey());
            headers.put("anthropic-version", "2023-06-01");
            String raw = postJson(endpoint(config.getBaseUrl(), "/v1/messages"), body, headers);
            JSONObject response = new JSONObject(raw);
            return extractResponse(response.optJSONArray("content"));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Anthropic Messages 协议解析失败: " + e.getMessage(), e);
        }
    }

    @Override
    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        try {
            ModelRequestOptions requestOptions = options == null ? ModelRequestOptions.defaults() : options;
            String effort = requestOptions.getReasoningEffort();
            boolean thinkingEnabled = !AiBehaviorSettings.REASONING_OFF.equals(effort);
            int thinkingBudget = thinkingEnabled ? thinkingBudget(effort) : 0;
            JSONObject body = new JSONObject();
            body.put("model", ModelContextParser.apiModelId(config.getModelId()));
            body.put("max_tokens", thinkingEnabled ? Math.max(4096, thinkingBudget + 1024) : 4096);
            body.put("messages", messagesJson(messages));
            body.put("stream", true);
            if (!requestOptions.getTools().isEmpty()) {
                body.put("tools", toolsJson(requestOptions.getTools()));
            }
            if (thinkingEnabled) {
                body.put("thinking", new JSONObject()
                        .put("type", "enabled")
                        .put("budget_tokens", thinkingBudget));
            }

            String system = systemPrompt(messages);
            if (system.length() > 0) {
                body.put("system", system);
            }

            HashMap<String, String> headers = new HashMap<>();
            headers.put("x-api-key", config.getApiKey());
            headers.put("anthropic-version", "2023-06-01");

            StringBuilder text = new StringBuilder();
            StringBuilder reasoning = new StringBuilder();
            HashMap<Integer, ToolUseBuilder> toolUseBuilders = new HashMap<>();

            postJsonSse(endpoint(config.getBaseUrl(), "/v1/messages"), body, headers, cancellationToken, (eventType, data) -> {
                if ("[DONE]".equals(data.trim())) {
                    return;
                }
                JSONObject event = new JSONObject(data);
                if (event.has("error")) {
                    throw new ModelCompletionException("Anthropic 流式错误: " + event.opt("error"));
                }
                String type = event.optString("type");

                if ("content_block_start".equals(type)) {
                    JSONObject block = event.optJSONObject("content_block");
                    if (block != null) {
                        String blockType = block.optString("type");
                        if ("redacted_thinking".equals(blockType)) {
                            appendDelta(reasoning, "[redacted thinking]", true, callback);
                        } else if ("tool_use".equals(blockType)) {
                            startToolUse(toolUseBuilders, event.optInt("index", toolUseBuilders.size()), block);
                        }
                    }
                    return;
                }

                if (!"content_block_delta".equals(type)) {
                    return;
                }

                JSONObject delta = event.optJSONObject("delta");
                if (delta == null) {
                    return;
                }
                String deltaType = delta.optString("type");
                if ("thinking_delta".equals(deltaType)) {
                    appendDelta(reasoning, delta.optString("thinking"), true, callback);
                } else if ("text_delta".equals(deltaType)) {
                    appendDelta(text, delta.optString("text"), false, callback);
                } else if ("input_json_delta".equals(deltaType)) {
                    appendToolUseInput(toolUseBuilders, event.optInt("index", toolUseBuilders.size()), delta.optString("partial_json"));
                }
            });

            return new ModelCompletionResponse(text.toString(), reasoning.toString(), buildToolCalls(toolUseBuilders));
        } catch (ModelCompletionException e) {
            throw e;
        } catch (Exception e) {
            throw new ModelCompletionException("Anthropic Messages 协议流式解析失败: " + e.getMessage(), e);
        }
    }

    private JSONArray messagesJson(List<ModelMessage> messages) throws Exception {
        JSONArray array = new JSONArray();
        for (int i = 0; i < messages.size(); i++) {
            ModelMessage message = messages.get(i);
            if ("system".equals(message.getRole())) {
                continue;
            }
            if ("tool".equals(message.getRole())) {
                JSONArray content = new JSONArray();
                while (i < messages.size() && "tool".equals(messages.get(i).getRole())) {
                    content.put(toolResultBlock(messages.get(i)));
                    i++;
                }
                i--;
                array.put(new JSONObject()
                        .put("role", "user")
                        .put("content", content));
                continue;
            }
            JSONObject object = new JSONObject();
            object.put("role", message.getRole());
            if ("assistant".equals(message.getRole()) && !message.getToolCalls().isEmpty()) {
                object.put("content", assistantContentBlocks(message));
            } else if ("user".equals(message.getRole()) && ImageInputPayload.fromRawInputJson(message.getRawInputJson()) != null) {
                object.put("content", imageContentBlocks(message));
            } else {
                object.put("content", message.getContent());
            }
            array.put(object);
        }
        return array;
    }

    JSONArray messagesJsonForTest(List<ModelMessage> messages) throws Exception {
        return messagesJson(messages);
    }

    private JSONArray imageContentBlocks(ModelMessage message) throws Exception {
        ImageInputPayload.Payload payload = ImageInputPayload.fromRawInputJson(message.getRawInputJson());
        if (payload == null) {
            return new JSONArray().put(new JSONObject()
                    .put("type", "text")
                    .put("text", message.getContent()));
        }
        String prompt = payload.getPrompt().length() == 0 ? message.getContent() : payload.getPrompt();
        return new JSONArray()
                .put(new JSONObject()
                        .put("type", "text")
                        .put("text", prompt == null ? "" : prompt))
                .put(new JSONObject()
                        .put("type", "image")
                        .put("source", new JSONObject()
                                .put("type", "base64")
                                .put("media_type", payload.getMimeType())
                                .put("data", payload.getDataBase64())));
    }

    private JSONArray assistantContentBlocks(ModelMessage message) throws Exception {
        JSONArray blocks = new JSONArray();
        if (message.getContent().trim().length() > 0) {
            blocks.put(new JSONObject()
                    .put("type", "text")
                    .put("text", message.getContent()));
        }
        for (ToolCall call : message.getToolCalls()) {
            blocks.put(new JSONObject()
                    .put("type", "tool_use")
                    .put("id", call.getId())
                    .put("name", call.getName())
                    .put("input", toolInputJson(call.getArguments())));
        }
        return blocks;
    }

    private JSONObject toolResultBlock(ModelMessage message) throws Exception {
        JSONObject block = new JSONObject()
                .put("type", "tool_result")
                .put("tool_use_id", message.getToolCallId())
                .put("content", message.getContent());
        if (message.isToolError()) {
            block.put("is_error", true);
        }
        return block;
    }

    private JSONArray toolsJson(List<BaseTool> tools) throws Exception {
        JSONArray array = new JSONArray();
        for (BaseTool tool : tools) {
            array.put(new JSONObject()
                    .put("name", tool.getName())
                    .put("description", tool.getDescription())
                    .put("input_schema", tool.getParameters()));
        }
        return array;
    }

    private String systemPrompt(List<ModelMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (ModelMessage message : messages) {
            if ("system".equals(message.getRole())) {
                if (builder.length() > 0) {
                    builder.append('\n');
                }
                builder.append(message.getContent());
            }
        }
        return builder.toString();
    }

    private ModelCompletionResponse extractResponse(JSONArray content) {
        if (content == null) {
            return new ModelCompletionResponse("");
        }
        StringBuilder builder = new StringBuilder();
        ArrayList<ToolCall> toolCalls = new ArrayList<>();
        for (int i = 0; i < content.length(); i++) {
            JSONObject block = content.optJSONObject(i);
            if (block == null) {
                continue;
            }
            String type = block.optString("type");
            if ("text".equals(type)) {
                builder.append(block.optString("text"));
            } else if ("tool_use".equals(type)) {
                JSONObject input = block.optJSONObject("input");
                toolCalls.add(new ToolCall(
                        block.optString("id"),
                        block.optString("name"),
                        input == null ? "{}" : input.toString()
                ));
            }
        }
        return new ModelCompletionResponse(builder.toString(), "", toolCalls);
    }

    private void appendDelta(
            StringBuilder target,
            String delta,
            boolean thinking,
            ModelStreamCallback callback
    ) {
        if (delta == null || delta.length() == 0) {
            return;
        }
        target.append(delta);
        if (callback == null) {
            return;
        }
        if (thinking) {
            callback.onReasoningDelta(delta);
        } else {
            callback.onTextDelta(delta);
        }
    }

    private int thinkingBudget(String effort) {
        if (AiBehaviorSettings.REASONING_LOW.equals(effort)) {
            return 1024;
        }
        if (AiBehaviorSettings.REASONING_HIGH.equals(effort)) {
            return 8192;
        }
        if (AiBehaviorSettings.REASONING_MAX.equals(effort)) {
            return 16000;
        }
        return 4096;
    }

    private JSONObject toolInputJson(String arguments) throws Exception {
        if (arguments == null || arguments.trim().length() == 0) {
            return new JSONObject();
        }
        String value = arguments.trim();
        if (value.startsWith("{")) {
            return new JSONObject(value);
        }
        JSONObject object = new JSONObject();
        object.put("value", value);
        return object;
    }

    private void startToolUse(Map<Integer, ToolUseBuilder> builders, int index, JSONObject block) {
        ToolUseBuilder builder = builders.get(index);
        if (builder == null) {
            builder = new ToolUseBuilder();
            builders.put(index, builder);
        }
        builder.id = block.optString("id");
        builder.name = block.optString("name");
        JSONObject input = block.optJSONObject("input");
        if (input != null && input.length() > 0 && builder.input.length() == 0) {
            builder.input.append(input.toString());
        }
    }

    private void appendToolUseInput(Map<Integer, ToolUseBuilder> builders, int index, String partialJson) {
        if (partialJson == null || partialJson.length() == 0) {
            return;
        }
        ToolUseBuilder builder = builders.get(index);
        if (builder == null) {
            builder = new ToolUseBuilder();
            builders.put(index, builder);
        }
        builder.input.append(partialJson);
    }

    private List<ToolCall> buildToolCalls(Map<Integer, ToolUseBuilder> builders) {
        ArrayList<Integer> indexes = new ArrayList<>(builders.keySet());
        indexes.sort(Integer::compareTo);
        ArrayList<ToolCall> calls = new ArrayList<>();
        for (Integer index : indexes) {
            ToolUseBuilder builder = builders.get(index);
            if (builder == null || builder.name.length() == 0) {
                continue;
            }
            String id = builder.id.length() == 0 ? "toolu_" + index + "_" + System.currentTimeMillis() : builder.id;
            String arguments = builder.input.length() == 0 ? "{}" : builder.input.toString();
            calls.add(new ToolCall(id, builder.name, arguments));
        }
        return calls;
    }

    private static final class ToolUseBuilder {
        String id = "";
        String name = "";
        final StringBuilder input = new StringBuilder();
    }
}
