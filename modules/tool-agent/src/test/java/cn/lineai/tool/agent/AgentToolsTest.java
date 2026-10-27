package cn.lineai.tool.agent;

import org.json.JSONObject;

import java.util.Arrays;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentToolsTest {

    private AgentExtension agent(boolean enabled) {
        return AgentExtension.builder()
                .name("Test Fixer")
                .slug("test-fixer")
                .prompt("Fix failing tests.")
                .trigger("tests fail")
                .enabled(enabled)
                .build();
    }

    @Test public void registersEnabledAgentsUnderPrefixedNames() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true), agent(false)),
                (agent, task) -> "ok");
        assertEquals(1, registry.all().size());
        Tool tool = registry.find("agent_test_fixer");
        assertTrue(tool != null);
        assertTrue(tool.description().contains("tests fail"));
        assertTrue(tool.parametersJson().contains("task"));
    }

    @Test public void executionDelegatesToTheRunner() {
        final StringBuilder seen = new StringBuilder();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)),
                (agent, task) -> {
                    seen.append(agent.name).append(':').append(task);
                    return "agent result";
                });
        ToolResult result = registry.find("agent_test_fixer")
                .execute(null, new JSONObject("{\"task\":\"fix it\"}"));
        assertEquals("agent result", result.content);
        assertEquals("Test Fixer:fix it", seen.toString());
    }

    @Test public void emptyTaskFailsWithoutRunning() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)),
                (agent, task) -> "never");
        assertTrue(registry.find("agent_test_fixer")
                .execute(null, new JSONObject("{}")).error);
    }

    @Test public void keepsBaseToolsAndAvoidsCollisions() {
        AgentExtension one = AgentExtension.builder().name("One").slug("same")
                .prompt("p").enabled(true).build();
        AgentExtension two = AgentExtension.builder().name("Two").slug("same")
                .prompt("p").enabled(true).build();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(one, two), (agent, task) -> "ok");
        assertTrue(registry.find("agent_same") != null);
        assertTrue(registry.find("agent_same_2") != null);
        assertNull(registry.find("missing"));
    }
}
