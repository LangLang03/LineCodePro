package cn.lineai.feature.chat;

import cn.lineai.core.model.ModelConfig;

/** Model capability switches the composer menu and reasoning picker depend on. */
public final class ChatCapabilities {

    public final boolean reasoning;
    public final boolean vision;
    public final boolean video;

    public ChatCapabilities(boolean reasoning, boolean vision, boolean video) {
        this.reasoning = reasoning;
        this.vision = vision;
        this.video = video;
    }

    public static ChatCapabilities of(ModelConfig model) {
        if (model == null) {
            return new ChatCapabilities(true, false, false);
        }
        return new ChatCapabilities(model.supportsReasoning, model.supportsVision,
                model.supportsVideo);
    }
}
