package cn.lineai.tool.media;

import cn.lineai.core.model.ModelConfig;

/**
 * Blocking vision completion used by the image understanding tool.
 *
 * <p>The interface keeps the vision provider behind the tool boundary so the tool layer never
 * touches provider protocols directly.</p>
 */
public interface VisionCaller {

    /**
     * Answers a prompt about one stored image.
     *
     * @param model configuration identifying the vision model and credentials
     * @param prompt question or instruction about the image
     * @param imagePath local path of the image file to analyze
     * @return the model's answer text, or the empty string when nothing is produced
     * @throws Exception on transport, authentication or provider failure
     */
    String describe(ModelConfig model, String prompt, String imagePath) throws Exception;
}
