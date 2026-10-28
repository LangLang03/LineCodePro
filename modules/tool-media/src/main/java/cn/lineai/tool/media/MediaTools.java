package cn.lineai.tool.media;

import java.util.function.Supplier;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/** Adds image tools to the catalog when their model slots are configured. */
public final class MediaTools {

    private MediaTools() {
    }

    public static ToolRegistry augment(ToolRegistry base, Supplier<ModelConfig> understandingModel,
                                       Supplier<ModelConfig> generationModel,
                                       VisionCaller vision, ImageCaller generator) {
        ToolRegistry registry = new ToolRegistry();
        if (base != null) {
            for (Tool tool : base.all()) registry.register(tool);
        }
        if (configured(understandingModel)) {
            registry.register(new ImageUnderstandingTool(understandingModel, vision));
        }
        if (configured(generationModel)) {
            registry.register(new ImageGenerationTool(generationModel, generator));
        }
        return registry;
    }

    private static boolean configured(Supplier<ModelConfig> supplier) {
        ModelConfig model = supplier == null ? null : supplier.get();
        return model != null && !model.modelId.isEmpty();
    }
}
