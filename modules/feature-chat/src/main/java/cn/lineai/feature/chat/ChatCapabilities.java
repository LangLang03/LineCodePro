package cn.lineai.feature.chat;

import cn.lineai.core.model.ModelConfig;

/** Model capability switches the composer menu and reasoning picker depend on. */
public final class ChatCapabilities {

    /** True when the model supports explicit reasoning output. */
    public final boolean reasoning;
    /** True when the model accepts image attachments. */
    public final boolean vision;
    /** True when the model accepts video attachments. */
    public final boolean video;

    /** @param reasoning  reasoning support flag
     * @param vision     image attachment support flag
     * @param video      video attachment support flag */
    public ChatCapabilities(boolean reasoning, boolean vision, boolean video) {
        this.reasoning = reasoning;
        this.vision = vision;
        this.video = video;
    }

    /** Reads the flags from the model config; a missing model defaults to reasoning only. */
    public static ChatCapabilities of(ModelConfig model) {
        if (model == null) {
            return new ChatCapabilities(true, false, false);
        }
        return new ChatCapabilities(model.supportsReasoning, model.supportsVision,
                model.supportsVideo);
    }
}
