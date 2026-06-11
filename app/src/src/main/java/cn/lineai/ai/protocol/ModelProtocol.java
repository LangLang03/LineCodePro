package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.ModelStreamCallback;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import java.util.List;

public interface ModelProtocol {
    ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException;

    default ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ModelCompletionResponse("");
        }
        ModelCompletionResponse response = complete(config, messages);
        if (cancellationToken == null || !cancellationToken.isCancelled()) {
            if (callback != null && response.getReasoningContent().length() > 0) {
                callback.onReasoningDelta(response.getReasoningContent());
            }
            if (callback != null && response.getText().length() > 0) {
                callback.onTextDelta(response.getText());
            }
        }
        return response;
    }

    default ModelCompletionResponse stream(
            ModelConfig config,
            List<ModelMessage> messages,
            ModelStreamCallback callback,
            ModelCancellationToken cancellationToken,
            ModelRequestOptions options
    ) throws ModelCompletionException {
        return stream(config, messages, callback, cancellationToken);
    }
}
