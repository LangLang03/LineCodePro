package cn.lineai.ai.protocol;

import cn.lineai.model.ModelProtocolType;

public final class ModelProtocolFactory {
    public ModelProtocol create(ModelProtocolType type) {
        if (type == ModelProtocolType.CODEX_RESPONSES) {
            return new CodexResponsesProtocol();
        }
        if (type == ModelProtocolType.ANTHROPIC_MESSAGES) {
            return new AnthropicMessagesProtocol();
        }
        if (type == ModelProtocolType.LOCAL_GGUF) {
            return new LocalGgufProtocol();
        }
        return new OpenAiCompatibleProtocol();
    }
}
