package cn.lineai.ui.component.toolcall;

import org.junit.Assert;
import org.junit.Test;

public final class ToolCallUtilsTest {
    @Test
    public void workspaceDisplayPathShowsRelativePathInsideWorkspace() {
        Assert.assertEquals(
                "app/src/Main.java",
                ToolCallUtils.workspaceDisplayPath("/home/user/project", "/home/user/project/app/src/Main.java"));
    }

    @Test
    public void workspaceDisplayPathShowsDotForWorkspaceRoot() {
        Assert.assertEquals(
                ".",
                ToolCallUtils.workspaceDisplayPath("/home/user/project", "/home/user/project"));
    }

    @Test
    public void workspaceDisplayPathKeepsFullPathOutsideWorkspace() {
        Assert.assertEquals(
                "/home/user/project-other/app/src/Main.java",
                ToolCallUtils.workspaceDisplayPath("/home/user/project", "/home/user/project-other/app/src/Main.java"));
    }

    @Test
    public void workspaceDisplayPathKeepsRelativeInputRelative() {
        Assert.assertEquals(
                "app/src/Main.java",
                ToolCallUtils.workspaceDisplayPath("/home/user/project", "./app/src/Main.java"));
    }

    @Test
    public void workspaceDisplayPathTrimsFileSchemeBeforeFormatting() {
        Assert.assertEquals(
                "app/src/Main.java",
                ToolCallUtils.workspaceDisplayPath("/home/user/project", "file:///home/user/project/app/src/Main.java"));
    }
}
