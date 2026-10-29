package cn.lineai.engine.ai;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HttpModelClientProtocolTest {

    private HttpServer server;
    private final AtomicReference<String> path = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();
    private final AtomicReference<String> apiKeyHeader = new AtomicReference<>();
    private final AtomicReference<String> anthropicVersion = new AtomicReference<>();
    private final AtomicReference<String> originator = new AtomicReference<>();
    private final List<String> failures = new ArrayList<>();
    private String sse;
    private int errorStatus = 200;
    private String errorBody = "";

    @Before public void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> handle(exchange));
        server.start();
    }

    @After public void stop() {
        server.stop(0);
    }

    private void handle(HttpExchange exchange) throws IOException {
        path.set(exchange.getRequestURI().getPath());
        apiKeyHeader.set(exchange.getRequestHeaders().getFirst("x-api-key"));
        anthropicVersion.set(exchange.getRequestHeaders().getFirst("anthropic-version"));
        originator.set(exchange.getRequestHeaders().getFirst("originator"));
        try (InputStream input = exchange.getRequestBody()) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) output.write(buffer, 0, read);
            body.set(output.toString(StandardCharsets.UTF_8.name()));
        }
        if (errorStatus != 200) {
            byte[] failure = errorBody.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(errorStatus, failure.length);
            try (OutputStream output = exchange.getResponseBody()) {
                output.write(failure);
            }
            return;
        }
        byte[] payload = sse.getBytes(StandardCharsets.UTF_8);
        exchange.getResponseHeaders().add("Content-Type", "text/event-stream");
        exchange.sendResponseHeaders(200, payload.length);
        try (OutputStream output = exchange.getResponseBody()) {
            output.write(payload);
        }
    }

    @Test public void httpFailureKeepsTheProviderResponseBody() {
        errorStatus = 500;
        errorBody = "{\"error\":{\"message\":\"quota exceeded\"}}";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "k");
        assertEquals(1, result.failures.size());
        assertTrue(result.detail.contains("HTTP 500"));
        assertTrue(result.detail.contains("{\"error\":{\"message\":\"quota exceeded\"}}"));
    }

    @Test public void openAiProtocolPostsToChatCompletions() {
        sse = "data: {\"choices\":[{\"delta\":{\"content\":\"hi\"}}]}\n\ndata: [DONE]\n\n";
        Result result = stream(ModelConfig.Protocol.OPENAI_COMPATIBLE, "sk-openai");
        assertEquals("/v1/chat/completions", path.get());
        assertTrue(body.get().contains("\"messages\""));
        assertEquals("hi", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    @Test public void anthropicProtocolUsesMessagesEndpointAndHeaders() {
        sse = "event: message_start\n"
                + "data: {\"type\":\"message_start\",\"message\":{\"usage\":{\"input_tokens\":7}}}\n\n"
                + "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"text_delta\",\"text\":\"hello\"}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        Result result = stream(ModelConfig.Protocol.ANTHROPIC_MESSAGES, "sk-ant");
        assertEquals("/v1/messages", path.get());
        assertEquals("sk-ant", apiKeyHeader.get());
        assertEquals("2023-06-01", anthropicVersion.get());
        assertTrue(body.get().contains("\"system\":\"sys\""));
        assertTrue(body.get().contains("\"stream\":true"));
        assertEquals("hello", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    @Test public void anthropicThinkingDeltaGoesToReasoning() {
        sse = "data: {\"type\":\"content_block_delta\",\"delta\":{\"type\":\"thinking_delta\",\"thinking\":\"think\"}}\n\n"
                + "data: {\"type\":\"message_stop\"}\n\n";
        Result result = stream(ModelConfig.Protocol.ANTHROPIC_MESSAGES, "k");
        assertEquals("think", result.reasoning.toString());
        assertTrue(result.text.length() == 0);
    }

    @Test public void codexProtocolUsesResponsesEndpoint() {
        sse = "data: {\"type\":\"response.output_text.delta\",\"delta\":\"hey\"}\n\n"
                + "data: {\"type\":\"response.completed\",\"response\":{\"usage\":{\"input_tokens\":2,\"output_tokens\":3}}}\n\n";
        Result result = stream(ModelConfig.Protocol.CODEX_RESPONSES, "sk-codex");
        assertEquals("/v1/responses", path.get());
        assertEquals("codex_cli_rs", originator.get());
        assertTrue(body.get().contains("\"instructions\":\"sys\""));
        assertEquals("hey", result.text.toString());
        assertTrue(result.failures.isEmpty());
    }

    private Result stream(ModelConfig.Protocol protocol, String apiKey) {
        ModelConfig model = ModelConfig.builder()
                .identity("m", "M")
                .endpoint("p", "model", "http://127.0.0.1:" + server.getAddress().getPort() + "/v1")
                .secret(apiKey)
                .protocol(protocol)
                .build();
        ChatRequest request = ChatRequest.builder()
                .model("model")
                .system("sys")
                .messages(java.util.Collections.singletonList(
                        Message.builder().id("m1").conversationId("c").seq(1).role(Role.USER)
                                .addBlock(cn.lineai.core.model.ContentBlock.text("hello")).build()))
                .build();
        Result result = new Result();
        new HttpModelClient(Scheduler.immediate()).stream(model, request, new ModelClient.Listener() {
            public void onEvent(StreamEvent event) {
                result.text.append(event.text);
                result.reasoning.append(event.reasoning);
            }
            public void onComplete() { result.completed = true; }
            public void onError(DomainException error) {
                result.failures.add(error.code().name());
                result.detail = error.detail();
            }
        });
        return result;
    }

    private static final class Result {
        final StringBuilder text = new StringBuilder();
        final StringBuilder reasoning = new StringBuilder();
        final List<String> failures = new ArrayList<>();
        String detail = "";
        boolean completed;
    }
}
