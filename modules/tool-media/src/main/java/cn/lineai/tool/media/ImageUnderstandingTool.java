package cn.lineai.tool.media;

import org.json.JSONObject;

import java.io.File;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.tool.api.PathPolicy;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/** Reads a workspace image with the configured vision model. */
final class ImageUnderstandingTool implements Tool {

    private final java.util.function.Supplier<ModelConfig> model;
    private final VisionCaller vision;

    ImageUnderstandingTool(java.util.function.Supplier<ModelConfig> model, VisionCaller vision) {
        this.model = model;
        this.vision = vision;
    }

    @Override public String name() { return "image_understanding"; }

    @Override public String description() {
        return "Look at a workspace image with the configured vision model and answer a prompt.";
    }

    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("path", "Workspace-relative image path")
                .string("prompt", "What to ask about the image")
                .required("path").required("prompt").build();
    }

    @Override public boolean mutating() { return false; }

    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Image understanding", ToolIcon.IMAGE,
                arguments.optString("path", ""), ToolPresentation.reason(arguments));
    }

    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        ModelConfig config = model == null ? null : model.get();
        if (config == null || config.modelId.isEmpty()) {
            return ToolResult.failure("", "Image understanding model is not configured");
        }
        try {
            File file = context.resolvePath(arguments.getString("path"));
            if (!file.isFile()) return ToolResult.failure("", "Not a file: " + arguments.getString("path"));
            String text = vision.describe(config, arguments.optString("prompt", "Describe the image."),
                    file.getAbsolutePath());
            return text == null || text.isEmpty()
                    ? ToolResult.failure("", "Vision model returned no text")
                    : ToolResult.success("", text);
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
