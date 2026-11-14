package cn.lineai.tool.agent;

import org.json.JSONObject;

import java.io.File;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.AgentStatus;
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

/**
 * Dispatch contract of the agent tool family: the creation tool requires a workspace binding,
 * resolves and validates it before a session starts, and refuses bindings that SSH and
 * terminal-provider workspaces cannot support.
 */
public class AgentToolsTest {

    @Rule public TemporaryFolder folder = new TemporaryFolder();

    /** Scripted runtime: records started tasks, bindings and fixed entries. */
    private static final class FakeRuntime implements AgentRuntime {
        final List<String> started = new ArrayList<>();
        final List<Boolean> persistentFlags = new ArrayList<>();
        final List<String> workspaces = new ArrayList<>();
        final List<AgentEntry> entries = new ArrayList<>();
        String sendResult = "delivered";
        final List<String> sent = new ArrayList<>();

        @Override public AgentEntry start(AgentExtension agent, String task, boolean persistent,
                                          String workspace) {
            started.add(agent.slug + ":" + task);
            persistentFlags.add(persistent);
            workspaces.add(workspace);
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

    /** Minimal tool context over a local root, optionally reporting a remote workspace. */
    private static final class Context implements ToolContext {
        private final File root;
        private final boolean remote;

        Context(File root, boolean remote) {
            this.root = root;
            this.remote = remote;
        }

        @Override public File workspaceRoot() { return root; }

        @Override public String modelId() { return ""; }

        @Override public boolean mutationAllowed() { return true; }

        @Override public boolean remoteWorkspace() { return remote; }
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

    private ToolRegistry registry(FakeRuntime runtime) {
        return AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true)), runtime);
    }

    private Tool agentTool(FakeRuntime runtime) {
        return registry(runtime).find("agent_test_fixer");
    }

    @Test public void registersManagementToolsAndEnabledAgents() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent(true), agent(false)), new FakeRuntime());
        assertTrue(registry.find("agent_list") != null);
        assertTrue(registry.find("agent_send") != null);
        assertTrue(registry.find("agent_wait") != null);
        assertTrue(registry.find("agent_test_fixer") != null);
        assertNull(registry.find("missing"));
        JSONObject schema = new JSONObject(registry.find("agent_test_fixer").parametersJson());
        assertTrue(schema.getJSONObject("properties").has("workspace"));
        assertTrue(schema.getJSONArray("required").toString().contains("\"workspace\""));
        assertTrue(registry.find("agent_test_fixer").parametersJson().contains("persistent"));
    }

    @Test public void executionStartsAnAsyncSessionBoundToTheGivenWorkspace() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        File root = folder.getRoot();
        ToolResult result = agentTool(runtime).execute(new Context(root, false),
                new JSONObject("{\"task\":\"fix it\",\"persistent\":true,"
                        + "\"workspace\":\".\"}"));
        assertTrue(result.content.contains("Started sub agent"));
        assertTrue(result.content.contains("persistent"));
        assertTrue(result.content.contains(root.getCanonicalPath()));
        assertEquals(1, runtime.started.size());
        assertTrue(runtime.persistentFlags.get(0));
        assertEquals("test-fixer:fix it", runtime.started.get(0));
        assertEquals(root.getCanonicalPath(), runtime.workspaces.get(0));
    }

    @Test public void transientIsTheDefaultMode() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"look\",\"workspace\":\".\"}"));
        assertFalse(runtime.persistentFlags.get(0));
    }

    @Test public void emptyTaskFailsWithoutRunning() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{}"));
        assertTrue(result.error);
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void emptyWorkspaceFailsWithoutRunning() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"go\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("workspace must not be empty"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void subdirectoryBindsInsideTheWorkspace() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        File work1 = folder.newFolder("work1");
        agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"go\",\"workspace\":\"work1/\"}"));
        assertEquals(work1.getCanonicalPath(), runtime.workspaces.get(0));
    }

    @Test public void absoluteWorkspaceIsRejected() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"go\",\"workspace\":\"/tmp\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("Absolute paths are not allowed"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void escapingWorkspaceIsRejected() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"go\",\"workspace\":\"../escape\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("Path escapes the workspace"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void missingWorkspaceDirectoryIsRejected() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), false),
                new JSONObject("{\"task\":\"go\",\"workspace\":\"missing\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("not an existing directory"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void headlessCallWithoutContextIsRejected() {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(null,
                new JSONObject("{\"task\":\"go\",\"workspace\":\".\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("tool context"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void remoteWorkspaceRejectsDirectoryBinding() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        folder.newFolder("work1");
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), true),
                new JSONObject("{\"task\":\"go\",\"workspace\":\"work1\"}"));
        assertTrue(result.error);
        assertTrue(result.content.contains("not supported in SSH"));
        assertTrue(runtime.started.isEmpty());
    }

    @Test public void remoteWorkspaceInheritsTheParentWithDot() throws Exception {
        FakeRuntime runtime = new FakeRuntime();
        ToolResult result = agentTool(runtime).execute(new Context(folder.getRoot(), true),
                new JSONObject("{\"task\":\"go\",\"workspace\":\".\"}"));
        assertFalse(result.error);
        assertEquals("", runtime.workspaces.get(0));
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
        ToolRegistry registry = registry(runtime);
        ToolResult result = registry.find("agent_send").execute(null, new JSONObject(
                "{\"agent_id\":\"sa-1\",\"message\":\"status?\"}"));
        assertTrue(result.content.contains("delivered"));
        assertEquals("sa-1:status?", runtime.sent.get(0));
    }

    @Test public void waitReturnsTheStatusSnapshot() {
        FakeRuntime runtime = new FakeRuntime();
        ToolRegistry registry = registry(runtime);
        JSONObject args = new JSONObject("{\"task\":\"go\",\"workspace\":\".\"}");
        registry.find("agent_test_fixer").execute(new Context(folder.getRoot(), false), args);
        ToolResult result = registry.find("agent_wait").execute(null, new JSONObject("{}"));
        assertTrue(result.content.contains("status=running"));
        assertTrue(result.content.contains("id=\"sa-1\""));
    }
}
