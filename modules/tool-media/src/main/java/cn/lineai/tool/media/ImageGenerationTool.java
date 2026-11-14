package cn.lineai.tool.media;

import org.json.JSONObject;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.tool.api.ToolResult;

/**
 * Generates an image with the configured image model and saves it in the workspace.
 *
 * <p>Output always lands under {@code generated/} with a timestamped name, so repeated
 * generations cannot collide and the model can reference the file by its workspace-relative
 * path in later calls.</p>
 */
final class ImageGenerationTool implements Tool {

    private final java.util.function.Supplier<ModelConfig> model;
    private final ImageCaller generator;

    /**
     * Creates the tool.
     *
     * @param model supplier of the image model configuration, evaluated per call
     * @param generator blocking transport used to produce the image bytes
     */
    ImageGenerationTool(java.util.function.Supplier<ModelConfig> model, ImageCaller generator) {
        this.model = model;
        this.generator = generator;
    }

    /** {@inheritDoc} */
    @Override public String name() { return "image_generation"; }

    /** {@inheritDoc} */
    @Override public String description() {
        return "Generate an image from a prompt and save it into the workspace.";
    }

    /** {@inheritDoc} */
    @Override public String parametersJson() {
        return cn.lineai.tool.api.ToolSchemaBuilder.object()
                .string("prompt", "Image description")
                .required("prompt").build();
    }

    /** {@inheritDoc} */
    @Override public boolean needsLocalWorkspace() { return true; }

    /** {@inheritDoc} */
    @Override public boolean mutating() { return true; }

    /** {@inheritDoc} */
    @Override public ToolPresentation presentation(JSONObject arguments) {
        return new ToolPresentation("Image generation", ToolIcon.SPARKLES,
                arguments.optString("prompt", ""), ToolPresentation.reason(arguments));
    }

    /**
     * {@inheritDoc}
     *
     * <p>Fails when the model slot is unconfigured, the prompt is empty, or the model returns
     * no data; on success the result carries both the workspace-relative path and the local
     * image path for inline display.</p>
     */
    @Override public ToolResult execute(ToolContext context, JSONObject arguments) {
        ModelConfig config = model == null ? null : model.get();
        if (config == null || config.modelId.isEmpty()) {
            return ToolResult.failure("", "Image generation model is not configured");
        }
        String prompt = arguments.optString("prompt", "").trim();
        if (prompt.isEmpty()) return ToolResult.failure("", "prompt must not be empty");
        try {
            byte[] bytes = generator.generate(config, prompt);
            if (bytes == null || bytes.length == 0) {
                return ToolResult.failure("", "Image model returned no data");
            }
            File dir = new File(context.workspaceRoot(), "generated");
            if (!dir.exists() && !dir.mkdirs()) {
                return ToolResult.failure("", "Cannot create generated directory");
            }
            String name = "image-" + System.currentTimeMillis() + ".png";
            File target = new File(dir, name);
            Files.write(target.toPath(), bytes);
            return ToolResult.successWithImage("", "Image saved to generated/" + name,
                    target.getAbsolutePath());
        } catch (IOException error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        } catch (Exception error) {
            return ToolResult.failure("", error.getMessage() == null
                    ? error.getClass().getSimpleName() : error.getMessage());
        }
    }
}
