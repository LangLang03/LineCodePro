package cn.lineai.ai;

import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.protocol.ModelProtocol;
import cn.lineai.ai.protocol.ModelProtocolFactory;
import cn.lineai.model.ModelConfig;
import java.util.List;

public final class ModelClient {
    private final ModelProtocolFactory protocolFactory = new ModelProtocolFactory();

    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        ModelProtocol protocol = protocolFactory.create(config.getProtocolType());
        return protocol.complete(config, messages);
    }

    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        return stream(config, messages, callback, cancellationToken, ModelRequestOptions.defaults());
    }

    public ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        ModelProtocol protocol = protocolFactory.create(config.getProtocolType());
        return protocol.stream(config, messages, callback, cancellationToken, options == null ? ModelRequestOptions.defaults() : options);
    }
}
