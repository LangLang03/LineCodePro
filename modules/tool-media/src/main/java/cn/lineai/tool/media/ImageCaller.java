package cn.lineai.tool.media;

import cn.lineai.core.model.ModelConfig;

/** Blocking image generation used by the image generation tool. */
public interface ImageCaller {

    byte[] generate(ModelConfig model, String prompt) throws Exception;
}
