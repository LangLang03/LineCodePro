package cn.lineai.engine.ai;

import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.protocol.AnthropicCodec;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.CodexCodec;
import cn.lineai.engine.protocol.OpenAiChatCodec;
import cn.lineai.engine.protocol.SseParser;
import cn.lineai.engine.protocol.StreamDecoder;
import cn.lineai.engine.protocol.StreamEvent;

/**
 * Blocking HTTP/SSE transport executed on the injected IO scheduler. The wire format is chosen by
 * the model protocol (OpenAI chat, Anthropic Messages, Codex Responses).
 */
public final class HttpModelClient implements ModelClient {

    private static final class Plan {
        final String url;
        final String body;
        final Map<String, String> headers;
        final StreamDecoder decoder;

        Plan(String url, String body, Map<String, String> headers,
             StreamDecoder decoder) {
            this.url = url;
            this.body = body;
            this.headers = headers;
            this.decoder = decoder;
        }
    }

    private final Scheduler scheduler;

    public HttpModelClient(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    @Override
    public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
        final Connection connection = new Connection();
        scheduler.io(() -> run(model, request, listener, connection));
        return connection;
    }

    private void run(ModelConfig model, ChatRequest request, Listener listener, Connection holder) {
        if (!isAllowed(model.baseUrl)) {
            listener.onError(new DomainException(ErrorCode.INVALID_INPUT, "unsupported endpoint"));
            return;
        }
        Plan plan = plan(model, request);
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(plan.url).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20_000);
            connection.setReadTimeout(180_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json");
            for (Map.Entry<String, String> header : plan.headers.entrySet()) {
                connection.setRequestProperty(header.getKey(), header.getValue());
            }
            holder.attach(connection);
            byte[] body = plan.body.getBytes(StandardCharsets.UTF_8);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(body);
            }
            int status = connection.getResponseCode();
            if (status < 200 || status >= 300) {
                String errorPayload = readError(connection);
                String detail = errorPayload.isEmpty()
                        ? "HTTP " + status : "HTTP " + status + "\n" + errorPayload;
                listener.onError(new DomainException(status == 401 || status == 403
                        ? ErrorCode.AUTH
                        : ErrorCode.NETWORK, detail));
                return;
            }
            if (!read(connection.getInputStream(), listener, holder, plan.decoder)) {
                return;
            }
            listener.onComplete();
        } catch (IOException error) {
            if (holder.isCancelled()) {
                listener.onError(new DomainException(ErrorCode.CANCELLED, "connection closed"));
            } else {
                listener.onError(new DomainException(ErrorCode.NETWORK,
                        error.getMessage() == null ? error.getClass().getSimpleName()
                                : error.getMessage(), error));
            }
        } finally {
            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static Plan plan(ModelConfig model, ChatRequest request) {
        Map<String, String> headers = new LinkedHashMap<>();
        headers.put("Accept", "text/event-stream");
        switch (model.protocol) {
            case ANTHROPIC_MESSAGES:
                headers.put("x-api-key", model.apiKey);
                headers.put("anthropic-version", "2023-06-01");
                return new Plan(AnthropicCodec.messagesEndpoint(model.baseUrl),
                        AnthropicCodec.requestBody(request), headers, AnthropicCodec.decoder());
            case CODEX_RESPONSES:
                headers.put("Authorization", "Bearer " + model.apiKey);
                headers.put("version", CodexCodec.PROTOCOL_VERSION);
                headers.put("originator", CodexCodec.ORIGINATOR);
                headers.put("User-Agent", CodexCodec.ORIGINATOR + "/" + CodexCodec.PROTOCOL_VERSION
                        + " (Android; LineCode)");
                return new Plan(CodexCodec.responsesEndpoint(model.baseUrl),
                        CodexCodec.requestBody(request, model.baseUrl), headers, CodexCodec.decoder());
            case OPENAI_COMPATIBLE:
            case LOCAL_GGUF:
            default:
                headers.put("Authorization", "Bearer " + model.apiKey);
                return new Plan(OpenAiChatCodec.endpoint(model.baseUrl),
                        OpenAiChatCodec.requestBody(request), headers, OpenAiChatCodec.decoder());
        }
    }

    /** Reads the provider's error payload; the status code alone hides the actual reason. */
    private static String readError(HttpURLConnection connection) {
        InputStream stream = connection.getErrorStream();
        if (stream == null) {
            return "";
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        try (InputStream input = stream) {
            byte[] buffer = new byte[4096];
            int count;
            while ((count = input.read(buffer)) >= 0 && out.size() < 32768) {
                out.write(buffer, 0, Math.min(count, 32768 - out.size()));
            }
        } catch (IOException error) {
            return "";
        }
        return new String(out.toByteArray(), StandardCharsets.UTF_8).trim();
    }

    private boolean read(InputStream input, Listener listener, Connection holder,
                         StreamDecoder decoder)
            throws IOException {
        SseParser parser = new SseParser();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(input, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (holder.isCancelled()) {
                    return false;
                }
                if (!dispatch(parser.feed(line), decoder, listener)) return false;
            }
        }
        return dispatch(parser.finish(), decoder, listener);
    }

    private static boolean dispatch(List<String> payloads, StreamDecoder decoder, Listener listener) {
        try {
            for (String payload : payloads) {
                StreamEvent event = decoder.decode(payload);
                if (event != null && !event.isEmpty()) listener.onEvent(event);
            }
            return true;
        } catch (IllegalStateException error) {
            listener.onError(new DomainException(ErrorCode.NETWORK,
                    error.getMessage() == null ? "stream error" : error.getMessage()));
            return false;
        }
    }

    private static boolean isAllowed(String baseUrl) {
        String value = baseUrl == null ? "" : baseUrl.trim();
        if (value.startsWith("https://")) {
            return true;
        }
        if (!value.startsWith("http://")) {
            return false;
        }
        String host = value.substring(7);
        int slash = host.indexOf('/');
        if (slash >= 0) {
            host = host.substring(0, slash);
        }
        return host.startsWith("localhost") || host.startsWith("127.0.0.1")
                || host.startsWith("10.0.2.2");
    }

    private static final class Connection implements Cancellable {
        private volatile boolean cancelled;
        private volatile HttpURLConnection connection;

        void attach(HttpURLConnection value) {
            connection = value;
            if (cancelled) {
                value.disconnect();
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            HttpURLConnection local = connection;
            if (local != null) {
                local.disconnect();
            }
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
