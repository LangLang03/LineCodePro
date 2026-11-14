package cn.lineai.tool.agent;

import org.json.JSONObject;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.AgentStatus;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;
import cn.lineai.tool.api.ToolResult;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentToolsTest {

    /** Scripted runtime: records started tasks and returns fixed entries. */
    private static final class FakeRuntime implements AgentRuntime {
        final List<String> started = new ArrayList<>();
        final List<Boolean> persistentFlags = new ArrayList<>();
        final List<AgentEntry> entries = new ArrayList<>();
        String sendResult = "delivered";
        final List<String> sent = new ArrayList<>();

        @Override public AgentEntry start(AgentExtension agent, String task, boolean persistent) {
            started.add(agent.slug + ":" + task);
            persistentFlags.add(persistent);
            AgentEntry entry = new AgentEntry("sa-" + started.size(),
                    agent.name.isEmpty() ? agent.slug : agent.name,
                    persistent ? "agent" : "agent_transient", "conv-" + started.size(),
                    AgentStatus.RUNNING);
            entries.add(entry);
            return entry;
        }

        @Override public List<AgentEntry> list() {
            return entries;
        }

        @Override public String send(String agentId, String message) {
            sent.add(agentId + ":" + message);
            return sendResult;
        }

        @Override public List<AgentEntry> waitFor(long timeoutMs) {
            return entries;
        }
    }

    private AgentExtension agent(boolean enabled) {
        return AgentExtension.builder()
                .name("Test Fixer")
                .slug("test-fixer")
                .prompt("Fix failing tests.")
                .trigger("tests fail")
                .enabled(enabled)
                .build();
    }

    @Test public void registersManagementToolsAndEnabledAgents() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true), agent(false)), new FakeRuntime());
        assertTrue(registry.find("agent_list") != null);
        assertTrue(registry.find("agent_send") != null);
        assertTrue(registry.find("agent_wait") != null);
        assertTrue(registry.find("agent_test_fixer") != null);
        assertNull(registry.find("missing"));
        assertTrue(registry.find("agent_test_fixer").parametersJson().contains("persistent"));
    }

    @Test public void executionStartsAnAsyncSession() {
        FakeRuntime runtime = new FakeRuntime();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), runtime);
        ToolResult result = registry.find("agent_test_fixer")
                .execute(null, new JSONObject("{\"task\":\"fix it\",\"persistent\":true}"));
        assertTrue(result.content.contains("Started sub agent"));
        assertTrue(result.content.contains("persistent"));
        assertEquals(1, runtime.started.size());
        assertTrue(runtime.persistentFlags.get(0));
        assertEquals("test-fixer:fix it", runtime.started.get(0));
    }

    @Test public void transientIsTheDefaultMode() {
        FakeRuntime runtime = new FakeRuntime();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), runtime);
        registry.find("agent_test_fixer").execute(null, new JSONObject("{\"task\":\"look\"}"));
        assertFalse(runtime.persistentFlags.get(0));
    }

    @Test public void emptyTaskFailsWithoutRunning() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), new FakeRuntime());
        assertTrue(registry.find("agent_test_fixer")
                .execute(null, new JSONObject("{}")).error);
    }

    @Test public void keepsBaseToolsAndAvoidsCollisions() {
        AgentExtension one = AgentExtension.builder().name("One").slug("same")
                .prompt("p").enabled(true).build();
        AgentExtension two = AgentExtension.builder().name("Two").slug("same")
                .prompt("p").enabled(true).build();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(one, two), new FakeRuntime());
        assertTrue(registry.find("agent_same") != null);
        assertTrue(registry.find("agent_same_2") != null);
        assertNull(registry.find("missing"));
    }

    @Test public void sendRoutesThroughTheRuntime() {
        FakeRuntime runtime = new FakeRuntime();
        runtime.sendResult = "Message delivered to sub agent \"Fixer\".";
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), runtime);
        ToolResult result = registry.find("agent_send").execute(null, new JSONObject(
                "{\"agent_id\":\"sa-1\",\"message\":\"status?\"}"));
        assertTrue(result.content.contains("delivered"));
        assertEquals("sa-1:status?", runtime.sent.get(0));
    }

    @Test public void waitReturnsTheStatusSnapshot() {
        FakeRuntime runtime = new FakeRuntime();
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), runtime);
        registry.find("agent_test_fixer").execute(null, new JSONObject("{\"task\":\"go\"}"));
        ToolResult result = registry.find("agent_wait").execute(null, new JSONObject("{}"));
        assertTrue(result.content.contains("status=running"));
        assertTrue(result.content.contains("id=\"sa-1\""));
    }
}
