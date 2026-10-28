package cn.lineai.app;

import java.util.concurrent.atomic.AtomicInteger;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.runtime.LocalModelClient;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.protocol.ChatRequest;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class LocalModelClientTest {

    private ModelConfig model(ModelConfig.Protocol protocol, String path) {
        return ModelConfig.builder().identity("m", "M")
                .endpoint("local", "model", path.isEmpty() ? "" : "file")
                .protocol(protocol).local(path, "automatic").build();
    }

    @Test public void delegatesNonLocalAndMissingFilesToHttpClient() {
        AtomicInteger calls = new AtomicInteger();
        ModelClient fallback = (model, request, listener) -> {
            calls.incrementAndGet();
            return Cancellable.NONE;
        };
        LocalModelClient client = new LocalModelClient(fallback, Scheduler.immediate());
        ChatRequest request = ChatRequest.builder().model("m").build();
        client.stream(model(ModelConfig.Protocol.OPENAI_COMPATIBLE, ""), request,
                new NoopListener());
        client.stream(model(ModelConfig.Protocol.LOCAL_GGUF, ""), request, new NoopListener());
        assertEquals(2, calls.get());
    }

    private static final class NoopListener implements ModelClient.Listener {
        public void onEvent(cn.lineai.engine.protocol.StreamEvent event) { }
        public void onComplete() { }
        public void onError(cn.lineai.core.kernel.error.DomainException error) { }
    }
}
