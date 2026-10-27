package cn.lineai.tool.phone;

import org.json.JSONObject;

import java.io.File;
import java.nio.file.Files;

import cn.lineai.tool.api.PhoneControl;
import cn.lineai.tool.api.ToolContext;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PhoneToolsTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private final StringBuilder calls = new StringBuilder();

    private PhoneControl control() {
        return new PhoneControl() {
            public boolean click(int x, int y) { calls.append("click:").append(x).append(',').append(y); return true; }
            public boolean longPress(int x, int y, int d) { calls.append("long:").append(d); return true; }
            public boolean swipe(int a, int b, int c, int d, int e) { calls.append("swipe"); return true; }
            public String viewHierarchy() { return "root > child"; }
            public boolean action(String action) { calls.append("action:").append(action); return true; }
            public byte[] screenshot() { return new byte[] {1, 2, 3}; }
        };
    }

    private ToolContext context(File root) {
        return new ToolContext() {
            public File workspaceRoot() { return root; }
            public String modelId() { return ""; }
            public boolean mutationAllowed() { return true; }
        };
    }

    @Test public void registersAllPhoneTools() {
        ToolRegistry registry = PhoneTools.augment(new ToolRegistry(), this::control);
        assertEquals(6, registry.all().size());
        assertFalse(registry.find("phone_view_hierarchy").mutating());
        assertTrue(registry.find("phone_click").mutating());
    }

    @Test public void hierarchyAndClickDelegate() throws Exception {
        ToolRegistry registry = PhoneTools.augment(new ToolRegistry(), this::control);
        File root = temporary.newFolder("ws");
        ToolResult hierarchy = registry.find("phone_view_hierarchy")
                .execute(context(root), new JSONObject("{}"));
        assertEquals("root > child", hierarchy.content);
        ToolResult click = registry.find("phone_click")
                .execute(context(root), new JSONObject("{\"x\":10,\"y\":20}"));
        assertFalse(click.error);
        assertEquals("click:10,20", calls.toString());
    }

    @Test public void screenshotSavesPngInWorkspace() throws Exception {
        ToolRegistry registry = PhoneTools.augment(new ToolRegistry(), this::control);
        File root = temporary.newFolder("ws");
        ToolResult result = registry.find("phone_screenshot")
                .execute(context(root), new JSONObject("{}"));
        assertFalse(result.content, result.error);
        File[] files = new File(root, "screenshots").listFiles();
        assertEquals(1, files.length);
        assertTrue(new String(Files.readAllBytes(files[0].toPath())).length() > 0);
    }

    @Test public void missingServiceFailsCleanly() throws Exception {
        ToolRegistry registry = PhoneTools.augment(new ToolRegistry(), () -> null);
        assertTrue(registry.find("phone_click")
                .execute(context(temporary.newFolder("ws")), new JSONObject("{\"x\":1,\"y\":2}")).error);
    }
}
