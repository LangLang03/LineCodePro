package cn.lineai.runtime;

import org.junit.Test;

import cn.lineai.core.model.Project;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SshWorkspaceTest {

    private static Project project(String id, String path) {
        return new Project(id, "SSH", path, true, 0L, 0L);
    }

    @Test public void detectsLegacyAndUrlSshProjects() {
        assertTrue(SshWorkspace.isSsh(project("ssh:default", "/data/data/com.termux/files/home")));
        assertTrue(SshWorkspace.isSsh(project("x", "ssh://user@host:8022/srv/app")));
        assertFalse(SshWorkspace.isSsh(project("default", "/data/user/0/cn.lineai/files")));
        assertFalse(SshWorkspace.isSsh(null));
    }

    @Test public void remoteDirectoryStripsTheUrlHost() {
        assertEquals("/data/data/com.termux/files/home",
                SshWorkspace.directory(project("ssh:default",
                        "/data/data/com.termux/files/home")));
        assertEquals("/srv/app",
                SshWorkspace.directory(project("x", "ssh://user@host:8022/srv/app")));
        assertEquals("", SshWorkspace.directory(project("ssh:default", "relative/dir")));
        assertEquals("", SshWorkspace.directory(null));
    }

    @Test public void remoteCatalogKeepsOnlyRemoteCapableTools() {
        ToolRegistry source = new ToolRegistry()
                .register(tool("read_file", true))
                .register(tool("shell_execute", false));
        ToolRegistry filtered = SshWorkspace.remoteOnly(source);
        assertEquals(1, filtered.all().size());
        assertTrue(filtered.find("shell_execute") != null);
        assertTrue(filtered.find("read_file") == null);
    }

    private static Tool tool(String name, boolean local) {
        return new Tool() {
            @Override public String name() { return name; }
            @Override public String description() { return name; }
            @Override public String parametersJson() { return "{}"; }
            @Override public boolean mutating() { return true; }
            @Override public boolean needsLocalWorkspace() { return local; }
            @Override public ToolResult execute(
                    cn.lineai.tool.api.ToolContext context, org.json.JSONObject arguments) {
                return ToolResult.success("", "");
            }
        };
    }
}
