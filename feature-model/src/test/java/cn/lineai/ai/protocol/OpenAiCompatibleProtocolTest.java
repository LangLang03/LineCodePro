package cn.lineai.ai.protocol;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.protocol.reasoning.KimiReasoningStrategy;
import cn.lineai.ai.protocol.reasoning.MoonshotReasoningStrategy;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class OpenAiCompatibleProtocolTest {
    @Test
    public void serializesVisionRawInputAsContentParts() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new UserModelMessage("fallback", ImageInputPayload.rawInputJson("看图", "image/jpeg", "abc123")));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject user = json.getJSONObject(0);
        assertEquals("user", user.getString("role"));
        JSONArray content = user.getJSONArray("content");
        assertEquals("text", content.getJSONObject(0).getString("type"));
        assertEquals("看图", content.getJSONObject(0).getString("text"));
        assertEquals("image_url", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/jpeg;base64,abc123",
                content.getJSONObject(1).getJSONObject("image_url").getString("url"));
    }

    @Test
    public void serializesToolErrorWithFailurePrefix() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new ToolModelMessage("No matching text found", "call_1", "file_edit", true));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject tool = json.getJSONObject(0);
        assertEquals("tool", tool.getString("role"));
        assertEquals("call_1", tool.getString("tool_call_id"));
        assertEquals("Tool file_edit failed:\nNo matching text found", tool.getString("content"));
    }

    @Test
    public void serializesToolSuccessWithoutPrefix() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new ToolModelMessage("Successfully edited demo.txt", "call_1", "file_edit", false));

        JSONArray json = new OpenAiMessageSerializer().messagesJsonForTest(messages);

        JSONObject tool = json.getJSONObject(0);
        assertEquals("Successfully edited demo.txt", tool.getString("content"));
    }

    @Test
    public void streamParsesToolCallChunksWithEmptyMetadataDeltas() throws Exception {
        LocalSseServer server = new LocalSseServer(
                data(chunk(toolDelta("call_00_abc", "shell_execute", ""), ""))
                        + data(chunk(toolDelta("", "", "{"), ""))
                        + data(chunk(toolDelta("", "", "\"command\""), ""))
                        + data(chunk(toolDelta("", "", ": "), ""))
                        + data(chunk(toolDelta("", "", "\"which git\""), ""))
                        + data(chunk(toolDelta("", "", "}"), ""))
                        + data(new JSONObject()
                        .put("choices", new JSONArray().put(new JSONObject()
                                .put("index", 0)
                                .put("delta", new JSONObject())
                                .put("finish_reason", "tool_calls"))))
                        + "data: [DONE]\n\n");
        server.start();
        try {
            ModelConfig config = ModelConfig.builder(
                    "m1",
                    "OpenAI compatible",
                    ModelProtocolType.OPENAI_COMPATIBLE,
                    "OpenAI",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test",
                    "deepseek-v4-flash").build();
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new UserModelMessage("check tools"));

            ModelCompletionResponse response = new OpenAiCompatibleProtocol().stream(
                    config,
                    messages,
                    new NoopCallback(),
                    null,
                    new ModelRequestOptions(
                            AiBehaviorSettings.REASONING_OFF,
                            false,
                            Collections.singletonList(new DummyShellTool())
                    )
            );

            JSONObject body = new JSONObject(server.requestBody());
            assertEquals("auto", body.getString("tool_choice"));
            assertEquals("shell_execute", body.getJSONArray("tools")
                    .getJSONObject(0)
                    .getJSONObject("function")
                    .getString("name"));
            assertEquals(1, response.getToolCalls().size());
            ToolCall call = response.getToolCalls().get(0);
            assertEquals("call_00_abc", call.getId());
            assertEquals("shell_execute", call.getName());
            assertEquals("{\"command\": \"which git\"}", call.getArguments());
        } finally {
            server.close();
        }
    }

    @Test
    public void streamParsesAndSeparatesReasoningSummaries() throws Exception {
        JSONObject first = new JSONObject()
                .put("type", "reasoning.summary")
                .put("index", 0)
                .put("summary", "**First step**\n");
        JSONObject second = new JSONObject()
                .put("type", "reasoning.summary")
                .put("index", 1)
                .put("summary", "  **Second step**");
        LocalSseServer server = new LocalSseServer(
                data(chunk(new JSONObject().put("reasoning_details", new JSONArray().put(first)), ""))
                        + data(chunk(new JSONObject().put("reasoning_details", new JSONArray().put(second)), ""))
                        + "data: [DONE]\n\n");
        server.start();
        try {
            ModelConfig config = ModelConfig.builder(
                    "m1", "GPT reasoning", ModelProtocolType.OPENAI_COMPATIBLE, "OpenAI",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test", "gpt-5.6-sol").build();

            ModelCompletionResponse response = new OpenAiCompatibleProtocol().stream(
                    config,
                    Collections.<ModelMessage>singletonList(new UserModelMessage("test")),
                    new NoopCallback(),
                    null,
                    new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false, Collections.emptyList())
            );

            assertEquals("**First step** | **Second step**", response.getReasoningContent());
        } finally {
            server.close();
        }
    }

    @Test
    public void streamPreservesFourAsterisksInOrdinaryReasoningContent() throws Exception {
        LocalSseServer server = new LocalSseServer(
                data(chunk(new JSONObject().put("reasoning_content", "redacted****token"), ""))
                        + "data: [DONE]\n\n");
        server.start();
        try {
            ModelConfig config = ModelConfig.builder(
                    "m1", "GPT reasoning", ModelProtocolType.OPENAI_COMPATIBLE, "OpenAI",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test", "gpt-5.6-sol").build();

            ModelCompletionResponse response = new OpenAiCompatibleProtocol().stream(
                    config,
                    Collections.<ModelMessage>singletonList(new UserModelMessage("test")),
                    new NoopCallback(),
                    null,
                    new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false, Collections.emptyList())
            );

            assertEquals("redacted****token", response.getReasoningContent());
        } finally {
            server.close();
        }
    }

    @Test
    public void gptChatUsesTopLevelReasoningEffort() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "gpt-sol", "GPT Sol", ModelProtocolType.OPENAI_COMPATIBLE, "OpenAI",
                "https://api.openai.com/v1", "sk-test", "gpt-5.6-sol").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false)
        );

        assertEquals("high", body.getString("reasoning_effort"));
        assertFalse(body.has("reasoning"));
    }

    @Test
    public void nvidiaGatewayDoesNotSendUnsupportedThinkingParameters() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "nvidia-qwen",
                "NVIDIA Qwen",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "NVIDIA",
                "https://integrate.api.nvidia.com/v1",
                "sk-test",
                "qwen/qwen3-coder").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, true)
        );

        assertFalse(body.has("enable_thinking"));
        assertFalse(body.has("thinking_budget"));
        assertFalse(body.has("preserve_thinking"));
        assertFalse(body.has("thinking"));
        assertFalse(body.has("reasoning"));
    }

    @Test
    public void kimiGatewayClampsTemperatureAndEnablesThinking() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "kimi-k2",
                "Kimi K2",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Moonshot",
                "https://api.moonshot.cn/v1",
                "sk-test",
                "kimi-k2-0711-preview").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, false)
        );

        assertEquals(1.0, body.optDouble("temperature", 0.2), 0.0001);
        assertTrue("enabled".equals(body.getJSONObject("thinking").getString("type")));
    }

    @Test
    public void kimiPreserveReasoningKeepsThinking() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "kimi-k2",
                "Kimi K2",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Moonshot",
                "https://api.moonshot.cn/v1",
                "sk-test",
                "kimi-k2-0711-preview").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, true)
        );

        assertTrue("all".equals(body.getJSONObject("thinking").getString("keep")));
    }

    @Test
    public void kimiStrategyClampsPreExistingTemperature() throws Exception {
        JSONObject body = new JSONObject().put("temperature", 0.2);
        new KimiReasoningStrategy().apply(
                body,
                new ReasoningRequestContext(true, "high", true, "api.moonshot.cn", "kimi-k2", 0)
        );

        assertEquals(1.0, body.getDouble("temperature"), 0.0001);
        assertTrue("all".equals(body.getJSONObject("thinking").getString("keep")));
    }

    @Test
    public void glmConfigStillHandledByMoonshotStrategy() throws Exception {
        String baseUrl = "https://open.bigmodel.cn/api/paas/v4";
        String modelId = "glm-4-plus";
        ModelConfig config = ModelConfig.builder(
                "glm-4",
                "GLM-4 Plus",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "Zhipu",
                baseUrl,
                "sk-test",
                modelId).build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_HIGH, true)
        );

        assertTrue("enabled".equals(body.getJSONObject("thinking").getString("type")));
        assertEquals("high", body.getString("reasoning_effort"));
        assertTrue(body.has("clear_thinking"));
        assertFalse(body.getBoolean("clear_thinking"));
        assertFalse(new KimiReasoningStrategy().matches(baseUrl, modelId));
        assertTrue(new MoonshotReasoningStrategy().matches(baseUrl, modelId));
    }

    @Test
    public void glmOffIsAdaptedToEnabledLowReasoning() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "glm-5.2", "GLM-5.2", ModelProtocolType.OPENAI_COMPATIBLE, "Zhipu",
                "https://open.bigmodel.cn/api/paas/v4", "sk-test", "glm-5.2").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false)
        );

        assertEquals("enabled", body.getJSONObject("thinking").getString("type"));
        assertEquals("low", body.getString("reasoning_effort"));
    }

    @Test
    public void deepseekTemperatureRemainsUnclamped() throws Exception {
        ModelConfig config = ModelConfig.builder(
                "deepseek",
                "DeepSeek Chat",
                ModelProtocolType.OPENAI_COMPATIBLE,
                "DeepSeek",
                "https://api.deepseek.com",
                "sk-test",
                "deepseek-chat").build();

        JSONObject body = new OpenAiCompatibleProtocol().reasoningRequestBodyForTest(
                config,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false)
        );

        assertEquals(0.2, body.optDouble("temperature", 0.2), 0.0001);
    }

    private static JSONObject chunk(JSONObject delta, String finishReason) throws Exception {
        return new JSONObject()
                .put("id", "chunk_1")
                .put("object", "chat.completion.chunk")
                .put("choices", new JSONArray().put(new JSONObject()
                        .put("index", 0)
                        .put("delta", delta)
                        .put("finish_reason", finishReason)));
    }

    private static JSONObject toolDelta(String id, String name, String arguments) throws Exception {
        return new JSONObject()
                .put("tool_calls", new JSONArray().put(new JSONObject()
                        .put("index", 0)
                        .put("id", id)
                        .put("type", "function")
                        .put("function", new JSONObject()
                                .put("name", name)
                                .put("arguments", arguments))));
    }

    private static String data(JSONObject object) {
        return "data: " + object.toString() + "\n\n";
    }

    private static final class LocalSseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final String responseBody;
        private Thread thread;
        private String requestBody = "";

        LocalSseServer(String responseBody) throws Exception {
            serverSocket = new ServerSocket(0);
            this.responseBody = responseBody == null ? "" : responseBody;
        }

        void start() {
            thread = new Thread(() -> {
                try {
                    handle(serverSocket.accept());
                } catch (Exception ignored) {
                }
            }, "openai-compatible-test-sse-server");
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String requestBody() {
            return requestBody;
        }

        private void handle(Socket socket) throws Exception {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                reader.readLine();
                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && line.length() > 0) {
                    String lower = line.toLowerCase(java.util.Locale.US);
                    if (lower.startsWith("content-length:")) {
                        contentLength = Integer.parseInt(line.substring("content-length:".length()).trim());
                    }
                }
                char[] body = new char[contentLength];
                int offset = 0;
                while (offset < contentLength) {
                    int read = reader.read(body, offset, contentLength - offset);
                    if (read < 0) {
                        break;
                    }
                    offset += read;
                }
                requestBody = new String(body, 0, offset);
                byte[] response = responseBody.getBytes(StandardCharsets.UTF_8);
                OutputStream output = socket.getOutputStream();
                String headers = "HTTP/1.1 200 OK\r\n"
                        + "Content-Type: text/event-stream\r\n"
                        + "Content-Length: " + response.length + "\r\n"
                        + "Connection: close\r\n"
                        + "\r\n";
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(response);
                output.flush();
            } finally {
                socket.close();
            }
        }

        @Override
        public void close() throws Exception {
            serverSocket.close();
            if (thread != null) {
                thread.join(1000);
            }
        }
    }

    private static final class NoopCallback implements ModelStreamCallback {
        @Override
        public void onTextDelta(String delta) {
        }

        @Override
        public void onReasoningDelta(String delta) {
        }
    }

    private static final class DummyShellTool extends BaseTool {
        @Override
        public String getName() {
            return "shell_execute";
        }

        @Override
        public String getDescription() {
            return "Execute shell command.";
        }

        @Override
        public ToolCategory getCategory() {
            return ToolCategory.SYSTEM;
        }

        @Override
        public JSONObject getParameters() throws org.json.JSONException {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                            .put("command", new JSONObject().put("type", "string")))
                    .put("required", new JSONArray().put("command"));
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return ToolResult.withReview("", getName(), "", false, "", "", "");
        }
    }
}
