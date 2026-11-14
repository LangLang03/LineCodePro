package cn.lineai.feature.chat.toolcall;

import static org.junit.Assert.assertSame;

import org.junit.Test;

import cn.lineai.tool.api.ToolIcon;

/** Locked routing: legacy read-style icons never fall through to the MCP/generic card. */
public final class ToolRenderersTest {

    @Test
    public void readStyleIconsUseReadRenderer() {
        assertSame(ReadToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.READ));
        assertSame(ReadToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.LIST));
        assertSame(ReadToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.SEARCH));
    }

    @Test
    public void writeStyleIconsUseWriteRenderer() {
        assertSame(WriteToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.WRITE));
        assertSame(WriteToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.EDIT));
        assertSame(WriteToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.DIFF));
    }

    @Test
    public void iconFamiliesRouteToTheirRenderer() {
        assertSame(ShellToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.TERMINAL));
        assertSame(DeleteToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.DELETE));
        assertSame(TodoToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.TODO));
        assertSame(AgentToolRenderers.SPAWN, ToolRenderers.renderer(ToolIcon.AGENT));
        assertSame(AgentToolRenderers.SPAWN, ToolRenderers.renderer(ToolIcon.AGENT_SPAWN));
        assertSame(AgentToolRenderers.SEND, ToolRenderers.renderer(ToolIcon.AGENT_SEND));
        assertSame(AgentToolRenderers.WAIT, ToolRenderers.renderer(ToolIcon.AGENT_WAIT));
        assertSame(AgentToolRenderers.LIST, ToolRenderers.renderer(ToolIcon.AGENT_LIST));
        assertSame(MemoryToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.MEMORY));
        assertSame(ImageToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.IMAGE));
        assertSame(ImageToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.SPARKLES));
        assertSame(GenericToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.MCP));
        assertSame(GenericToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.PHONE));
        assertSame(GenericToolRenderer.INSTANCE, ToolRenderers.renderer(ToolIcon.GENERIC));
    }
}
