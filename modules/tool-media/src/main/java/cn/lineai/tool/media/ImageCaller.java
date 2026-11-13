package cn.lineai.tool.media;

import cn.lineai.core.model.ModelConfig;

/**
 * Blocking image generation used by the image generation tool.
 *
 * <p>The call is synchronous by design: image generation happens inside a tool invocation,
 * which the agent loop already runs off the UI thread.</p>
 */
public interface ImageCaller {

    /**
     * Generates one image from a prompt.
     *
     * @param model configuration identifying the image model and credentials
     * @param prompt textual description of the wanted image
     * @return encoded image bytes; may be {@code null} or empty when the model yields nothing
     * @throws Exception on transport, authentication or provider failure
     */
    byte[] generate(ModelConfig model, String prompt) throws Exception;
}
