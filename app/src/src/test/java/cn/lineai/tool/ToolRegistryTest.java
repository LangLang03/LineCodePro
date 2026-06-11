package cn.lineai.tool;

import org.junit.Assert;
import org.junit.Test;

public final class ToolRegistryTest {
    @Test
    public void registersWebTools() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("web_search"));
        Assert.assertNotNull(registry.get("web_fetch"));
    }

    @Test
    public void registersImageUnderstandingTool() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("image_understanding"));
    }

    @Test
    public void registersImageGenerationTool() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("image_generation"));
    }

    @Test
    public void registersAgentTools() {
        ToolRegistry registry = new ToolRegistry();

        Assert.assertNotNull(registry.get("agent"));
        Assert.assertNotNull(registry.get("agent_pipeline"));
    }
}
