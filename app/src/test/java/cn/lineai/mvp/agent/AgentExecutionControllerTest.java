package cn.lineai.mvp.agent;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.McpSettingsState;
import cn.lineai.model.McpToolConfig;
import cn.lineai.model.WebSearchConfig;
import cn.lineai.tool.PermissionResult;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolInfo;
import cn.lineai.tool.ToolCategory;
import cn.lineai.tool.ToolContext;
import cn.lineai.tool.ToolExecutor;
import cn.lineai.tool.ToolRegistry;
import cn.lineai.tool.builtin.AgentTool;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;

public final class AgentExecutionControllerTest {

    @Test
    public void subCodingAllowsShellExecute() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        BaseTool shell = new FakeTool("shell_execute", ToolCategory.SYSTEM);

        assertTrue(controller.isAgentToolAllowed(
                shell,
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void localExploreRejectsShellExecute() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        BaseTool shell = new FakeTool("shell_execute", ToolCategory.SYSTEM);

        assertFalse(controller.isAgentToolAllowed(
                shell,
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingStillRejectsAgentAndPipelineAndDelete() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("agent", ToolCategory.SYSTEM),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("agent_pipeline", ToolCategory.SYSTEM),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("file_delete", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingKeepsReadWriteAndHttpServer() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_read", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_write", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_edit", ToolCategory.WRITE),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingRejectsCustomToolNotInWhitelist() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        HashSet<String> customToolNames = new HashSet<>();
        customToolNames.add("agentx_demo");

        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("web_search", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                customToolNames,
                Collections.emptySet()));
    }

    @Test
    public void subCodingAllowsCustomToolInWhitelist() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        HashSet<String> customToolNames = new HashSet<>();
        customToolNames.add("agentx_demo");

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("agentx_demo", ToolCategory.READ),
                AgentTool.TYPE_SUB_CODING,
                customToolNames,
                Collections.emptySet()));
    }

    @Test
    public void localExploreAllowsReadToolsOnly() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        assertTrue(controller.isAgentToolAllowed(
                new FakeTool("file_read", ToolCategory.READ),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("file_write", ToolCategory.WRITE),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
        assertFalse(controller.isAgentToolAllowed(
                new FakeTool("shell_execute", ToolCategory.SYSTEM),
                AgentTool.TYPE_EXPLORE,
                Collections.emptySet(),
                Collections.emptySet()));
    }

    @Test
    public void subCodingRolePromptMentionsShellExecuteAndWriteScope() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        String prompt = controller.agentRolePrompt(AgentTool.TYPE_SUB_CODING);

        assertTrue(prompt.contains("shell_execute"));
        assertTrue(prompt.contains("write_scope"));
    }

    @Test
    public void exploreRolePromptRemainsReadOnly() {
        AgentExecutionController controller = new AgentExecutionController(
                null, null, null, null, null, null, null);

        String prompt = controller.agentRolePrompt(AgentTool.TYPE_EXPLORE);

        assertTrue(prompt.contains("shell_execute"));
        assertTrue(prompt.contains("read-only commands"));
        assertTrue(prompt.contains("Only read code"));
    }

    @Test
    public void shellExecuteWaitsForReviewAndResumesOnAccept() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ConfirmTool shell = new ConfirmTool("shell_execute");
        registry.register(shell);
        ConfirmingSettings settings = new ConfirmingSettings();
        ToolExecutor executor = new ToolExecutor(registry, settings, null, null, null, null, null);
        AgentExecutionController controller = new AgentExecutionController(null, null, settings, executor, registry, null, null);
        AgentProgressSession progress = new AgentProgressSession(1, "agent_call", "agent", AgentTool.TYPE_SUB_CODING, "run shell");
        controller.setToolReviewAwaiter((displayToolCallId, call, cancellationToken) -> {
            assertEquals("agent_call_agent_0", displayToolCallId);
            return "accepted";
        });

        ToolResult result = controller.executeAgentToolCall(
                new ToolCall("shell_1", "shell_execute", "{\"command\":\"pwd\"}"),
                Collections.singleton("shell_execute"),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptyList(),
                "",
                progress,
                new FakeHost(),
                null);

        assertFalse(result.isError());
        assertEquals("accepted", result.getReviewState());
        assertEquals("ran shell_execute", result.getContent());
        assertEquals(1, shell.runCount);
    }

    @Test
    public void shellExecuteAutoConfirmedSkipsReviewAndExecutesConfirmed() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        ConfirmTool shell = new ConfirmTool("shell_execute");
        registry.register(shell);
        ConfirmingSettings settings = new ConfirmingSettings();
        ToolExecutor executor = new ToolExecutor(registry, settings, null, null, null, null, null);
        AgentExecutionController controller = new AgentExecutionController(null, null, settings, executor, registry, null, null);
        AgentProgressSession progress = new AgentProgressSession(1, "agent_call", "agent", AgentTool.TYPE_SUB_CODING, "run shell");
        controller.setToolReviewAwaiter(new AgentExecutionController.ToolReviewAwaiter() {
            @Override
            public String awaitReview(String displayToolCallId, ToolCall call, cn.lineai.ai.ModelCancellationToken cancellationToken) {
                throw new AssertionError("awaitReview should not be called when session auto confirmed");
            }

            @Override
            public boolean isAutoConfirmed(ToolCall call) {
                return "shell_execute".equals(call.getName());
            }
        });
        RecordingHost host = new RecordingHost();

        ToolResult result = controller.executeAgentToolCall(
                new ToolCall("shell_1", "shell_execute", "{\"command\":\"pwd\"}"),
                Collections.singleton("shell_execute"),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptyList(),
                "",
                progress,
                host,
                null);

        assertFalse(result.isError());
        assertEquals(1, shell.runCount);
        assertTrue(host.requestedReviews.isEmpty());
        assertTrue(host.clearedReviews.isEmpty());
    }

    @Test
    public void agentRolePromptSwitchesToShellInRemoteMode() {
        AgentExecutionController controller = new AgentExecutionController(null, null, null, null, null, null, null);
        String localRole = controller.agentRolePrompt(AgentTool.TYPE_SUB_CODING, false);
        String remoteRole = controller.agentRolePrompt(AgentTool.TYPE_SUB_CODING, true);
        assertTrue("local role should mention file tools", localRole.contains("file_read"));
        assertFalse("local role should not advertise remote-only mode", localRole.contains("remote Shell"));
        assertTrue("remote role should advertise remote mode", remoteRole.contains("remote Shell"));
        assertTrue("remote role should recommend shell_execute first", remoteRole.contains("shell_execute"));
        assertTrue("remote role should mark file tools as unavailable", remoteRole.contains("may not be available"));
    }

    @Test
    public void agentRolePromptExploreInRemoteModeRecommendsShell() {
        AgentExecutionController controller = new AgentExecutionController(null, null, null, null, null, null, null);
        String remoteExplore = controller.agentRolePrompt(AgentTool.TYPE_EXPLORE, true);
        assertTrue(remoteExplore.contains("remote Shell"));
        assertTrue(remoteExplore.contains("shell_execute"));
    }

    @Test
    public void subAgentToolReviewNotifiesMainFlowBeforeAwaiting() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ConfirmTool("shell_execute"));
        ConfirmingSettings settings = new ConfirmingSettings();
        ToolExecutor executor = new ToolExecutor(registry, settings, null, null, null, null, null);
        AgentExecutionController controller = new AgentExecutionController(null, null, settings, executor, registry, null, null);
        AgentProgressSession progress = new AgentProgressSession(1, "agent_call", "agent", AgentTool.TYPE_SUB_CODING, "run shell");
        controller.setToolReviewAwaiter((displayToolCallId, call, cancellationToken) -> "accepted");
        RecordingHost host = new RecordingHost();

        controller.executeAgentToolCall(
                new ToolCall("shell_1", "shell_execute", "{\"command\":\"pwd\"}"),
                Collections.singleton("shell_execute"),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptyList(),
                "",
                progress,
                host,
                null);

        assertEquals(1, host.requestedReviews.size());
        assertEquals("agent_call_agent_0", host.requestedReviews.get(0).displayToolCallId);
        assertEquals("shell_execute", host.requestedReviews.get(0).call.getName());
        assertEquals("pending", host.requestedReviews.get(0).pending.getReviewState());
        assertTrue(host.clearedReviews.contains("agent_call_agent_0"));
    }

    @Test
    public void subAgentToolReviewClearsMainFlowAfterReject() throws Exception {
        ToolRegistry registry = new ToolRegistry();
        registry.register(new ConfirmTool("shell_execute"));
        ConfirmingSettings settings = new ConfirmingSettings();
        ToolExecutor executor = new ToolExecutor(registry, settings, null, null, null, null, null);
        AgentExecutionController controller = new AgentExecutionController(null, null, settings, executor, registry, null, null);
        AgentProgressSession progress = new AgentProgressSession(1, "agent_call", "agent", AgentTool.TYPE_SUB_CODING, "run shell");
        controller.setToolReviewAwaiter((displayToolCallId, call, cancellationToken) -> "rejected");
        RecordingHost host = new RecordingHost();

        ToolResult result = controller.executeAgentToolCall(
                new ToolCall("shell_1", "shell_execute", "{\"command\":\"pwd\"}"),
                Collections.singleton("shell_execute"),
                AgentTool.TYPE_SUB_CODING,
                Collections.emptyList(),
                "",
                progress,
                host,
                null);

        assertTrue(result.isError());
        assertEquals("rejected", result.getReviewState());
        assertTrue(result.getContent().contains("User rejected"));
        assertTrue(host.clearedReviews.contains("agent_call_agent_0"));
    }

    @Test
    public void snapshotResultPropagatesStatusToOuterReviewState() {
        AgentProgressSession progress = new AgentProgressSession(1, "agent_call", "agent", AgentTool.TYPE_SUB_CODING, "run shell");
        progress.markStatus("pending", false);
        ToolResult pending = progress.snapshotResult();
        assertEquals("pending", pending.getReviewState());

        progress.markStatus("running", false);
        ToolResult running = progress.snapshotResult();
        assertEquals("running", running.getReviewState());

        progress.markFinished("done", false, "");
        ToolResult done = progress.snapshotResult();
        assertEquals("done", done.getReviewState());

        progress.markFinished("error", true, "");
        ToolResult error = progress.snapshotResult();
        assertEquals("error", error.getReviewState());
    }

    private static final class FakeTool extends BaseTool {
        private final String name;
        private final ToolCategory category;

        FakeTool(String name, ToolCategory category) {
            this.name = name;
            this.category = category;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "fake " + name;
        }

        @Override
        public ToolCategory getCategory() {
            return category;
        }

        @Override
        public cn.lineai.tool.ToolDisplayCategory getDisplayCategory() {
            if ("file_delete".equals(name)) return cn.lineai.tool.ToolDisplayCategory.DELETE;
            if ("shell_execute".equals(name)) return cn.lineai.tool.ToolDisplayCategory.SHELL;
            if ("agent".equals(name)) return cn.lineai.tool.ToolDisplayCategory.AGENT;
            if ("agent_pipeline".equals(name)) return cn.lineai.tool.ToolDisplayCategory.AGENT_PIPELINE;
            if ("file_read".equals(name) || "web_search".equals(name)) return cn.lineai.tool.ToolDisplayCategory.READ;
            if ("file_write".equals(name) || "file_edit".equals(name)) return cn.lineai.tool.ToolDisplayCategory.WRITE;
            return cn.lineai.tool.ToolDisplayCategory.GENERIC;
        }

        @Override
        public boolean needsConfirmation() {
            return "file_delete".equals(name) || "shell_execute".equals(name);
        }

        @Override
        public boolean isAllowedInReadonlyMode() {
            return "shell_execute".equals(name) || "agent".equals(name) || "agent_pipeline".equals(name);
        }

        @Override
        public JSONObject getParameters() {
            return new JSONObject();
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            return ToolResult.withReview("", name, "", false, "", "", "");
        }
    }

    private static final class ConfirmTool extends BaseTool {
        private final String name;
        private int runCount;

        ConfirmTool(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public String getDescription() {
            return "confirm " + name;
        }

        @Override
        public ToolCategory getCategory() {
            return ToolCategory.SYSTEM;
        }

        @Override
        public JSONObject getParameters() {
            return new JSONObject();
        }

        @Override
        public boolean needsConfirmation() {
            return true;
        }

        @Override
        public ToolResult execute(JSONObject input, ToolContext context) {
            runCount++;
            return ToolResult.withReview("", getName(), "ran " + getName(), false, "", "", "");
        }
    }

    private static class FakeHost implements AgentExecutionController.Host {
        @Override
        public String projectPath() {
            return "";
        }

        @Override
        public String projectSource() {
            return "";
        }

        @Override
        public void syncModePermission() {
        }

        @Override
        public void addOrReplaceToolResult(ToolResult result) {
        }

        @Override
        public void render() {
        }

        @Override
        public void scheduleAgentProgressRender(AgentProgressSession session) {
        }

        @Override
        public void postToolProgress(int generationId, cn.lineai.ai.ModelCancellationToken cancellationToken, String toolCallId, String toolName, String content, boolean error) {
        }

        @Override
        public void requestAgentToolReview(String displayToolCallId, ToolCall call, ToolResult pendingToolResult) {
        }

        @Override
        public void clearAgentToolReview(String displayToolCallId) {
        }

        @Override
        public boolean isSshExecutionMode() {
            return false;
        }

        @Override
        public boolean isTerminalProviderExecutionMode() {
            return false;
        }
    }

    private static final class RecordingHost extends FakeHost {
        final java.util.ArrayList<RequestEntry> requestedReviews = new java.util.ArrayList<>();
        final java.util.ArrayList<String> clearedReviews = new java.util.ArrayList<>();

        @Override
        public void requestAgentToolReview(String displayToolCallId, ToolCall call, ToolResult pendingToolResult) {
            requestedReviews.add(new RequestEntry(displayToolCallId, call, pendingToolResult));
        }

        @Override
        public void clearAgentToolReview(String displayToolCallId) {
            clearedReviews.add(displayToolCallId);
        }
    }

    private static final class RequestEntry {
        final String displayToolCallId;
        final ToolCall call;
        final ToolResult pending;

        RequestEntry(String displayToolCallId, ToolCall call, ToolResult pending) {
            this.displayToolCallId = displayToolCallId;
            this.call = call;
            this.pending = pending;
        }
    }

    private static final class ConfirmingSettings implements ToolSettingsStore {
        @Override
        public String getPermissionMode() {
            return PERMISSION_CONFIRM;
        }

        @Override
        public void setPermissionMode(String mode) {
        }

        @Override
        public String getExecutionMode() {
            return EXECUTION_SSH;
        }

        @Override
        public void setExecutionMode(String mode) {
        }

        @Override
        public List<McpToolConfig> getConfigs() {
            return Collections.emptyList();
        }

        @Override
        public McpSettingsState getMcpSettingsState() {
            return null;
        }

        @Override
        public WebSearchConfig getWebSearchConfig() {
            return null;
        }

        @Override
        public void setWebSearchConfig(WebSearchConfig config) {
        }

        @Override
        public String getImageUnderstandingModelId() {
            return "";
        }

        @Override
        public void setImageUnderstandingModelId(String modelId) {
        }

        @Override
        public String getImageGenerationModelId() {
            return "";
        }

        @Override
        public void setImageGenerationModelId(String modelId) {
        }

        @Override
        public void setMcpEnabled(String id, boolean enabled) {
        }

        @Override
        public Set<String> getEnabledToolNames() {
            return new HashSet<>(Collections.singletonList("shell_execute"));
        }

        @Override
        public Set<String> getEnabledToolNames(Collection<ToolInfo> implementedTools) {
            return getEnabledToolNames();
        }

        @Override
        public PermissionResult canExecuteTool(String toolName, ToolCategory category) {
            return PermissionResult.allowed();
        }

        @Override
        public boolean needsConfirmation(String toolName) {
            return true;
        }

        @Override
        public String buildToolPrompt(Set<String> implementedToolNames) {
            return "";
        }

        @Override
        public String buildToolPrompt(Set<String> implementedToolNames, boolean nativeToolProtocol) {
            return "";
        }

        @Override
        public String buildToolPrompt(Collection<ToolInfo> implementedTools, boolean nativeToolProtocol) {
            return "";
        }
    }
}
