package cn.lineai.engine.ai;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;

/**
 * Streaming completion client. Implementations own their transport, not their threading policy.
 *
 * <p>Events are delivered on whatever thread the implementation chooses, so listeners that touch
 * UI state must hop threads themselves. Exactly one terminal signal ({@code onComplete} or
 * {@code onError}) follows the event sequence.</p>
 */
public interface ModelClient {

    /** Callbacks for one streaming request; invoked in order and terminated exactly once. */
    interface Listener {
        /** Receives one decoded stream event; empty events are not delivered. */
        void onEvent(StreamEvent event);

        /** Signals successful end of stream; no events or callbacks follow it. */
        void onComplete();

        /** Signals terminal failure; no events or callbacks follow it. */
        void onError(cn.lineai.core.kernel.error.DomainException error);
    }

    /**
     * Starts a streaming completion.
     *
     * @param model model configuration selecting protocol, endpoint and credentials
     * @param request normalized completion request
     * @param listener callback sink for events and the terminal signal
     * @return handle that cancels the in-flight request
     */
    Cancellable stream(ModelConfig model, ChatRequest request, Listener listener);
}
