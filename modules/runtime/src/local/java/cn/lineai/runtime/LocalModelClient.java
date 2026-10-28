package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

import cn.lineai.engine.local.NativeLlamaEngine;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.protocol.ChatRequest;
import cn.lineai.engine.protocol.StreamEvent;

/** Runs LOCAL_GGUF models in-process through the bundled llama.cpp JNI library. */
public final class LocalModelClient implements ModelClient {

    private final ModelClient fallback;
    private final Scheduler scheduler;

    public LocalModelClient(ModelClient fallback, Scheduler scheduler) {
        this.fallback = fallback;
        this.scheduler = scheduler;
    }

    @Override public Cancellable stream(ModelConfig model, ChatRequest request, Listener listener) {
        if (model.protocol != ModelConfig.Protocol.LOCAL_GGUF
                || model.localModelPath.isEmpty()) {
            return fallback.stream(model, request, listener);
        }
        final AtomicBoolean cancelled = new AtomicBoolean();
        scheduler.io(() -> run(model, request, listener, cancelled));
        return new Cancellable() {
            @Override public void cancel() { cancelled.set(true); }
            @Override public boolean isCancelled() { return cancelled.get(); }
        };
    }

    private void run(ModelConfig model, ChatRequest request, Listener listener,
                     AtomicBoolean cancelled) {
        try {
            List<String> roles = new ArrayList<>();
            List<String> contents = new ArrayList<>();
            if (!request.systemPrompt.isEmpty()) {
                roles.add("system");
                contents.add(request.systemPrompt);
            }
            for (Message message : request.messages) {
                String text = message.plainText();
                if (!message.toolCalls.isEmpty()) {
                    StringBuilder calls = new StringBuilder(text);
                    for (cn.lineai.core.model.ToolCallRecord call : message.toolCalls) {
                        calls.append("\n[tool_call] ").append(call.name)
                                .append(' ').append(call.arguments);
                    }
                    text = calls.toString();
                }
                if (text.isEmpty()) continue;
                roles.add(message.role.wireName());
                contents.add(text);
            }
            int gpuLayers = "cpu".equals(model.localAcceleration) ? 0 : 99;
            NativeLlamaEngine.nativeGenerate(model.localModelPath,
                    roles.toArray(new String[0]), contents.toArray(new String[0]),
                    Math.max(0, model.maxTokens), model.contextSize, gpuLayers,
                    new NativeLlamaEngine.Callback() {
                        @Override public void onPiece(String piece) {
                            listener.onEvent(StreamEvent.text(piece));
                        }

                        @Override public void onLoadProgress(int percent) { }

                        @Override public boolean isCancelled() { return cancelled.get(); }
                    });
            if (!cancelled.get()) listener.onComplete();
        } catch (Throwable error) {
            if (cancelled.get()) return;
            listener.onError(new DomainException(ErrorCode.UNKNOWN,
                    error.getMessage() == null ? error.getClass().getSimpleName()
                            : error.getMessage()));
        }
    }
}
