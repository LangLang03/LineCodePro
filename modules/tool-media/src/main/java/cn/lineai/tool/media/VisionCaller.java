package cn.lineai.tool.media;

import cn.lineai.core.model.ModelConfig;

/** Blocking vision completion used by the image understanding tool. */
public interface VisionCaller {

    String describe(ModelConfig model, String prompt, String imagePath) throws Exception;
}
