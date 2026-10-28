package cn.lineai.engine.ai;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;

/** Streaming completion client. Implementations own their transport, not their threading policy. */
public interface ModelClient {

    interface Listener {
        void onEvent(StreamEvent event);

        void onComplete();

        void onError(cn.lineai.core.kernel.error.DomainException error);
    }

    Cancellable stream(ModelConfig model, ChatRequest request, Listener listener);
}
