package cn.lineai.ai.protocol;

import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.model.ModelConfig;
import java.util.List;

public final class LocalGgufProtocol implements ModelProtocol {
    @Override
    public ModelCompletionResponse complete(ModelConfig config, List<ModelMessage> messages) throws ModelCompletionException {
        throw new ModelCompletionException("本地 GGUF 推理接口还没有接入。请先使用 OpenAI 兼容、Codex 或 Anthropic 模型。");
    }
}
