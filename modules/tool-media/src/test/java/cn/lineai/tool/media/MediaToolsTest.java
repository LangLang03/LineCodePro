package cn.lineai.tool.media;

import org.json.JSONObject;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class MediaToolsTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private ModelConfig model() {
        return ModelConfig.builder().identity("img", "Image model")
                .endpoint("openai", "gpt-image-1", "https://example.com/v1")
                .secret("key").build();
    }

    private ToolContext context(File root) {
        return new ToolContext() {
            public File workspaceRoot() { return root; }
            public String modelId() { return ""; }
            public boolean mutationAllowed() { return true; }
        };
    }

    @Test public void registersOnlyConfiguredImageTools() {
        ToolRegistry base = new ToolRegistry();
        ToolRegistry none = MediaTools.augment(base, () -> null, () -> null,
                (m, p, i) -> "", (m, p) -> new byte[0]);
        assertEquals(0, none.all().size());

        ToolRegistry both = MediaTools.augment(base, this::model, this::model,
                (m, p, i) -> "seen", (m, p) -> new byte[0]);
        assertTrue(both.find("image_understanding") != null);
        assertTrue(both.find("image_generation") != null);
        assertTrue(both.find("image_generation").mutating());
        assertFalse(both.find("image_understanding").mutating());
    }

    @Test public void understandingReadsWorkspaceImageAndCallsVision() throws Exception {
        File root = temporary.newFolder("ws");
        File image = new File(root, "shot.png");
        Files.write(image.toPath(), "png".getBytes(StandardCharsets.UTF_8));
        ToolRegistry registry = MediaTools.augment(new ToolRegistry(), this::model, () -> null,
                (m, prompt, path) -> prompt + "@" + new File(path).getName(),
                (m, p) -> new byte[0]);
        ToolResult result = registry.find("image_understanding").execute(context(root),
                new JSONObject("{\"path\":\"shot.png\",\"prompt\":\"what\"}"));
        assertFalse(result.content, result.error);
        assertEquals("what@shot.png", result.content);
        assertTrue(registry.find("image_understanding").execute(context(root),
                new JSONObject("{\"path\":\"../escape.png\",\"prompt\":\"x\"}")).error);
    }

    @Test public void generationSavesBytesInWorkspace() throws Exception {
        File root = temporary.newFolder("ws");
        ToolRegistry registry = MediaTools.augment(new ToolRegistry(), () -> null, this::model,
                (m, p, i) -> "", (m, p) -> "image-bytes".getBytes(StandardCharsets.UTF_8));
        ToolResult result = registry.find("image_generation")
                .execute(context(root), new JSONObject("{\"prompt\":\"a cat\"}"));
        assertFalse(result.content, result.error);
        File generated = new File(root, "generated");
        File[] files = generated.listFiles();
        assertEquals(1, files.length);
        assertEquals("image-bytes", new String(Files.readAllBytes(files[0].toPath()),
                StandardCharsets.UTF_8));
    }

    @Test public void missingModelFailsCleanlyInDirectUse() throws Exception {
        ToolRegistry registry = MediaTools.augment(new ToolRegistry(), () -> null, () -> null,
                (m, p, i) -> "", (m, p) -> new byte[0]);
        assertNull(registry.find("image_understanding"));
    }
}
