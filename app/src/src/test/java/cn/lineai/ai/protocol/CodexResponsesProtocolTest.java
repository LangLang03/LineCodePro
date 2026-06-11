package cn.lineai.ai.protocol;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolResult;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.ServerSocket;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class CodexResponsesProtocolTest {
    @Test
    public void responsesInputBuilderFormatsFunctionCallHistory() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new SystemModelMessage("system prompt"));
        messages.add(new UserModelMessage("read a file"));
        messages.add(new AssistantModelMessage(
                "",
                "",
                Collections.singletonList(new ToolCall("call_1", "file_read", "{\"file_path\":\"README.md\"}"))
        ));
        messages.add(new ToolModelMessage("README content", "call_1", "file_read"));

        JSONArray input = ResponsesInputBuilder.inputJson(messages);

        assertEquals("system prompt", ResponsesInputBuilder.instructions(messages));
        assertEquals(3, input.length());
        assertEquals("message", input.getJSONObject(0).getString("type"));
        assertEquals("user", input.getJSONObject(0).getString("role"));
        assertEquals("input_text", input.getJSONObject(0).getJSONArray("content").getJSONObject(0).getString("type"));
        assertEquals("function_call", input.getJSONObject(1).getString("type"));
        assertEquals("file_read", input.getJSONObject(1).getString("name"));
        assertEquals("call_1", input.getJSONObject(1).getString("call_id"));
        assertEquals("function_call_output", input.getJSONObject(2).getString("type"));
        assertEquals("call_1", input.getJSONObject(2).getString("call_id"));
        assertEquals("README content", input.getJSONObject(2).getString("output"));
    }

    @Test
    public void responsesInputBuilderFormatsVisionRawInput() throws Exception {
        ArrayList<ModelMessage> messages = new ArrayList<>();
        messages.add(new UserModelMessage("fallback", ImageInputPayload.rawInputJson("描述图片", "image/png", "abc123")));

        JSONArray input = ResponsesInputBuilder.inputJson(messages);

        JSONObject message = input.getJSONObject(0);
        assertEquals("message", message.getString("type"));
        assertEquals("user", message.getString("role"));
        JSONArray content = message.getJSONArray("content");
        assertEquals("input_text", content.getJSONObject(0).getString("type"));
        assertEquals("描述图片", content.getJSONObject(0).getString("text"));
        assertEquals("input_image", content.getJSONObject(1).getString("type"));
        assertEquals("data:image/png;base64,abc123", content.getJSONObject(1).getString("image_url"));
    }

    @Test
    public void codexStreamParsesFunctionCallAndUsesResponsesEndpoint() throws Exception {
        LocalSseServer server = new LocalSseServer(sse("response.output_item.done", new JSONObject()
                .put("type", "response.output_item.done")
                .put("item", new JSONObject()
                        .put("type", "function_call")
                        .put("call_id", "call_1")
                        .put("name", "file_read")
                        .put("arguments", "{\"file_path\":\"README.md\"}"))
                .toString()));
        server.start();
        try {
            ModelConfig config = new ModelConfig(
                    "m1",
                    "Codex",
                    ModelProtocolType.CODEX_RESPONSES,
                    "Codex",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test",
                    "gpt-5-codex"
            );
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new SystemModelMessage("system prompt"));
            messages.add(new UserModelMessage("read README"));

            ModelCompletionResponse response = new CodexResponsesProtocol().stream(
                    config,
                    messages,
                    new NoopCallback(),
                    null,
                    new ModelRequestOptions(
                            AiBehaviorSettings.REASONING_MEDIUM,
                            false,
                            Collections.singletonList(new DummyTool())
                    )
            );

            assertEquals("/v1/responses", server.requestPath());
            assertEquals(CodexResponsesProtocol.CODEX_PROTOCOL_VERSION, server.requestHeader("version"));
            assertEquals(CodexResponsesProtocol.CODEX_ORIGINATOR, server.requestHeader("originator"));
            assertEquals(CodexResponsesProtocol.codexUserAgent(), server.requestHeader("user-agent"));
            JSONObject body = new JSONObject(server.requestBody());
            assertEquals("system prompt", body.getString("instructions"));
            assertTrue(body.getBoolean("parallel_tool_calls"));
            assertTrue(!body.getBoolean("store"));
            assertEquals("reasoning.encrypted_content", body.getJSONArray("include").getString(0));
            assertEquals("medium", body.getJSONObject("reasoning").getString("effort"));
            assertTrue(body.getString("prompt_cache_key").startsWith("linecode-codex-"));
            assertTrue(body.getJSONObject("client_metadata").getString("x-codex-installation-id").length() > 0);
            assertTrue(body.getJSONObject("client_metadata").getString("x-codex-window-id").length() > 0);
            assertEquals("auto", body.getString("tool_choice"));
            assertEquals("file_read", body.getJSONArray("tools").getJSONObject(0).getString("name"));
            assertEquals(1, response.getToolCalls().size());
            assertEquals("call_1", response.getToolCalls().get(0).getId());
            assertEquals("file_read", response.getToolCalls().get(0).getName());
            assertEquals("{\"file_path\":\"README.md\"}", response.getToolCalls().get(0).getArguments());
        } finally {
            server.close();
        }
    }

    @Test(timeout = 4000)
    public void codexStreamUsesJsonTypeAndStopsAfterCompleted() throws Exception {
        LocalSseServer server = new LocalSseServer(
                sse("message", new JSONObject()
                        .put("type", "response.output_text.delta")
                        .put("delta", "hello")
                        .toString())
                        + sse("message", new JSONObject()
                        .put("type", "response.completed")
                        .put("response", new JSONObject().put("id", "resp_1"))
                        .toString()),
                true);
        server.start();
        try {
            ModelConfig config = new ModelConfig(
                    "m1",
                    "Codex",
                    ModelProtocolType.CODEX_RESPONSES,
                    "Codex",
                    "http://127.0.0.1:" + server.port() + "/v1/chat/completions",
                    "sk-test",
                    "gpt-5-codex"
            );
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new UserModelMessage("say hello"));
            RecordingCallback callback = new RecordingCallback();

            ModelCompletionResponse response = new CodexResponsesProtocol().stream(
                    config,
                    messages,
                    callback,
                    null,
                    new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, Collections.emptyList())
            );

            assertEquals("hello", response.getText());
            assertTrue(callback.awaitText());
            assertEquals("hello", callback.text());
        } finally {
            server.close();
        }
    }

    @Test
    public void codexStreamUsesOutputTextDoneWhenDeltaIsMissing() throws Exception {
        LocalSseServer server = new LocalSseServer(
                sse("message", new JSONObject()
                        .put("type", "response.output_text.done")
                        .put("text", "hello")
                        .toString())
                        + sse("message", new JSONObject()
                        .put("type", "response.completed")
                        .put("response", new JSONObject().put("id", "resp_1"))
                        .toString()));
        server.start();
        try {
            ModelConfig config = codexConfig(server.port());
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new UserModelMessage("say hello"));
            RecordingCallback callback = new RecordingCallback();

            ModelCompletionResponse response = new CodexResponsesProtocol().stream(
                    config,
                    messages,
                    callback,
                    null,
                    new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, Collections.emptyList())
            );

            assertEquals("hello", response.getText());
            assertTrue(callback.awaitText());
            assertEquals("hello", callback.text());
        } finally {
            server.close();
        }
    }

    @Test
    public void codexStreamDoesNotDuplicateCompletedOutputAfterDelta() throws Exception {
        JSONObject outputItem = new JSONObject()
                .put("type", "message")
                .put("content", new JSONArray().put(new JSONObject()
                        .put("type", "output_text")
                        .put("text", "hello")));
        LocalSseServer server = new LocalSseServer(
                sse("message", new JSONObject()
                        .put("type", "response.output_text.delta")
                        .put("delta", "hello")
                        .toString())
                        + sse("message", new JSONObject()
                        .put("type", "response.completed")
                        .put("response", new JSONObject()
                                .put("id", "resp_1")
                                .put("output", new JSONArray().put(outputItem)))
                        .toString()));
        server.start();
        try {
            ModelConfig config = codexConfig(server.port());
            ArrayList<ModelMessage> messages = new ArrayList<>();
            messages.add(new UserModelMessage("say hello"));
            RecordingCallback callback = new RecordingCallback();

            ModelCompletionResponse response = new CodexResponsesProtocol().stream(
                    config,
                    messages,
                    callback,
                    null,
                    new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, Collections.emptyList())
            );

            assertEquals("hello", response.getText());
            assertTrue(callback.awaitText());
            assertEquals("hello", callback.text());
        } finally {
            server.close();
        }
    }

    @Test
    public void codexModelCatalogUsesClientVersionAndCodexHeaders() throws Exception {
        LocalSseServer server = new LocalSseServer(new JSONObject()
                .put("data", new JSONArray()
                        .put(new JSONObject().put("id", "gpt-5-codex"))
                        .put(new JSONObject().put("id", "gpt-5.1-codex")))
                .toString());
        server.start();
        try {
            List<String> ids = new ModelCatalogClient().fetch(
                    ModelProtocolType.CODEX_RESPONSES,
                    "http://127.0.0.1:" + server.port() + "/v1",
                    "sk-test"
            );

            assertEquals("/v1/models?client_version=" + CodexResponsesProtocol.CODEX_PROTOCOL_VERSION, server.requestPath());
            assertEquals(CodexResponsesProtocol.CODEX_PROTOCOL_VERSION, server.requestHeader("version"));
            assertEquals(CodexResponsesProtocol.CODEX_ORIGINATOR, server.requestHeader("originator"));
            assertEquals(CodexResponsesProtocol.codexUserAgent(), server.requestHeader("user-agent"));
            assertEquals(2, ids.size());
            assertEquals("gpt-5-codex", ids.get(0));
        } finally {
            server.close();
        }
    }

    private ModelConfig codexConfig(int port) {
        return new ModelConfig(
                "m1",
                "Codex",
                ModelProtocolType.CODEX_RESPONSES,
                "Codex",
                "http://127.0.0.1:" + port + "/v1/chat/completions",
                "sk-test",
                "gpt-5-codex"
        );
    }

    private String sse(String event, String data) {
        return "event: " + event + "\n"
                + "data: " + data + "\n\n";
    }

    private static final class LocalSseServer implements AutoCloseable {
        private final ServerSocket serverSocket;
        private final String responseBody;
        private final boolean holdOpenAfterResponse;
        private final CountDownLatch holdRelease = new CountDownLatch(1);
        private Thread thread;
        private String requestPath = "";
        private String requestBody = "";
        private final Map<String, String> requestHeaders = new HashMap<>();

        LocalSseServer(String responseBody) throws Exception {
            this(responseBody, false);
        }

        LocalSseServer(String responseBody, boolean holdOpenAfterResponse) throws Exception {
            serverSocket = new ServerSocket(0);
            this.responseBody = responseBody == null ? "" : responseBody;
            this.holdOpenAfterResponse = holdOpenAfterResponse;
        }

        void start() {
            thread = new Thread(() -> {
                try {
                    handle(serverSocket.accept());
                } catch (Exception ignored) {
                }
            }, "codex-test-sse-server");
            thread.start();
        }

        int port() {
            return serverSocket.getLocalPort();
        }

        String requestPath() {
            return requestPath;
        }

        String requestBody() {
            return requestBody;
        }

        String requestHeader(String name) {
            return requestHeaders.get(name.toLowerCase(java.util.Locale.US));
        }

        private void handle(Socket socket) throws Exception {
            try {
                BufferedReader reader = new BufferedReader(new InputStreamReader(socket.getInputStream(), StandardCharsets.UTF_8));
                String requestLine = reader.readLine();
                if (requestLine != null) {
                    String[] parts = requestLine.split(" ");
                    if (parts.length > 1) {
                        requestPath = parts[1];
                    }
                }
                int contentLength = 0;
                String line;
                while ((line = reader.readLine()) != null && line.length() > 0) {
                    String lower = line.toLowerCase(java.util.Locale.US);
                    int colon = line.indexOf(':');
                    if (colon > 0) {
                        requestHeaders.put(lower.substring(0, colon).trim(), line.substring(colon + 1).trim());
                    }
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
                        + (holdOpenAfterResponse ? "Connection: keep-alive\r\n" : "Content-Length: " + response.length + "\r\nConnection: close\r\n")
                        + "\r\n";
                output.write(headers.getBytes(StandardCharsets.UTF_8));
                output.write(response);
                output.flush();
                if (holdOpenAfterResponse) {
                    holdRelease.await(3, TimeUnit.SECONDS);
                }
            } finally {
                socket.close();
            }
        }

        @Override
        public void close() throws Exception {
            holdRelease.countDown();
            serverSocket.close();
            if (thread != null) {
                thread.join(1000);
            }
        }
    }

    private static final class RecordingCallback implements ModelStreamCallback {
        private final CountDownLatch textLatch = new CountDownLatch(1);
        private final StringBuilder text = new StringBuilder();

        @Override
        public void onTextDelta(String delta) {
            text.append(delta);
            textLatch.countDown();
        }

        @Override
        public void onReasoningDelta(String delta) {
        }

        boolean awaitText() throws InterruptedException {
            return textLatch.await(1, TimeUnit.SECONDS);
        }

        String text() {
            return text.toString();
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

    private static final class DummyTool extends BaseTool {
        @Override
        public String getName() {
            return "file_read";
        }

        @Override
        public String getDescription() {
            return "Read a file.";
        }

        @Override
        public ToolCategory getCategory() {
            return ToolCategory.READ;
        }

        @Override
        public JSONObject getParameters() throws org.json.JSONException {
            return new JSONObject()
                    .put("type", "object")
                    .put("properties", new JSONObject()
                            .put("file_path", new JSONObject().put("type", "string")));
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return new ToolResult("", getName(), "", false);
        }
    }
}
