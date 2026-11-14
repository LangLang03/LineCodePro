package cn.lineai.tool.media;

import java.util.function.Supplier;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Adds image tools to the catalog when their model slots are configured.
 *
 * <p>A tool whose model slot is empty is simply not registered, so the model never sees a
 * call it cannot successfully execute.</p>
 */
public final class MediaTools {

    /** Utility class; not instantiable. */
    private MediaTools() {
    }

    /**
     * Copies the base registry and appends the image tools whose model slots are configured.
     *
     * @param base registry to extend; may be {@code null} to start empty
     * @param understandingModel supplier of the vision model configuration
     * @param generationModel supplier of the image generation model configuration
     * @param vision blocking transport for vision completions
     * @param generator blocking transport for image generation
     * @return a new registry containing the base tools plus the configured media tools
     */
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

    /**
     * Whether a model slot is configured with a usable model identifier.
     *
     * @param supplier model configuration supplier; may be {@code null}
     * @return {@code true} when the supplier yields a model with a non-empty identifier
     */
    private static boolean configured(Supplier<ModelConfig> supplier) {
        ModelConfig model = supplier == null ? null : supplier.get();
        return model != null && !model.modelId.isEmpty();
    }
}
