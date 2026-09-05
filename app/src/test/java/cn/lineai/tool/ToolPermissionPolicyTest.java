package cn.lineai.tool;

import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.mvp.ToolRunController;
import cn.lineai.mvp.agent.AgentExecutionController;
import cn.lineai.mvp.agent.AgentProgressSession;
import cn.lineai.tool.builtin.AgentTool;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.json.JSONObject;
import org.junit.Test;
import static org.junit.Assert.*;

public final class ToolPermissionPolicyTest {
    @Test public void automaticModeRunsEnabledToolsWithoutMainOrAgentReview() {
        for (String target : Arrays.asList(ToolSettingsStore.EXECUTION_LOCAL,
                ToolSettingsStore.EXECUTION_SSH, ToolSettingsStore.EXECUTION_TERMINAL_PROVIDER)) {
            Fixture f = new Fixture(target);
            for (String name : Arrays.asList(ToolNames.SHELL_EXECUTE, ToolNames.FILE_DELETE)) {
                ToolCall call = new ToolCall("auto-" + name, name, "{}");
                assertFalse(f.controller.shouldPauseForConfirmation(call));
                assertFalse(f.executor.execute(call, null).isError());
            }
            AgentExecutionController agent = new AgentExecutionController(null, null, f.settings,
                    f.executor, f.registry, null, null);
            agent.setToolReviewAwaiter((id, call, cancellation) -> {
                throw new AssertionError("Automatic mode must not request review");
            });
            AgentExecutionController.Host host = (AgentExecutionController.Host) Proxy.newProxyInstance(
                    getClass().getClassLoader(), new Class<?>[]{AgentExecutionController.Host.class},
                    (proxy, method, args) -> null);
            for (String name : Arrays.asList(ToolNames.SHELL_EXECUTE, ToolNames.FILE_DELETE)) {
                ToolResult result = agent.executeAgentToolCall(
                        new ToolCall("agent-" + name, name, "{}"), f.enabled,
                        AgentTool.TYPE_SUB_CODING, Collections.singletonList("/workspace"), "",
                        new AgentProgressSession(1, "agent", "agent", AgentTool.TYPE_SUB_CODING, "test"), host, null);
                assertFalse(result.getContent(), result.isError());
            }
            assertEquals(4, f.executions);
        }
    }

    @Test public void confirmationModeStillPausesAndExecutorRequiresApproval() {
        Fixture f = new Fixture(ToolSettingsStore.EXECUTION_LOCAL);
        f.mode = ToolSettingsStore.PERMISSION_CONFIRM;
        for (String name : Arrays.asList(ToolNames.SHELL_EXECUTE, ToolNames.FILE_DELETE)) {
            ToolCall call = new ToolCall("confirm-" + name, name, "{}");
            assertTrue(f.controller.shouldPauseForConfirmation(call));
            assertTrue(f.executor.execute(call, null).isError());
        }
        assertEquals(0, f.executions);
        assertFalse(f.executor.executeConfirmed(new ToolCall("approved", ToolNames.SHELL_EXECUTE, "{}"), null).isError());
        assertEquals(1, f.executions);
        f.mode = ToolSettingsStore.PERMISSION_AUTO;
        assertFalse(f.controller.shouldPauseForConfirmation(new ToolCall("switched", ToolNames.SHELL_EXECUTE, "{}")));
    }

    @Test public void automaticModeDoesNotEnableDisabledToolsAndReadonlyStillBlocksLocalWrites() {
        Fixture f = new Fixture(ToolSettingsStore.EXECUTION_LOCAL);
        f.enabled.remove(ToolNames.SHELL_EXECUTE);
        assertTrue(f.executor.execute(new ToolCall("disabled", ToolNames.SHELL_EXECUTE, "{}"), null).isError());
        f.mode = ToolSettingsStore.PERMISSION_READONLY;
        assertTrue(f.executor.executeConfirmed(new ToolCall("readonly", ToolNames.FILE_DELETE, "{}"), null).isError());
        assertEquals(0, f.executions);
    }

    @Test public void automaticPolicyAlsoWorksWithoutRegistryMetadata() {
        Fixture f = new Fixture(ToolSettingsStore.EXECUTION_LOCAL);
        ToolPermissionService policy = new ToolPermissionService(f.settings, null);
        assertFalse(policy.needsConfirmation(ToolNames.SHELL_EXECUTE));
        assertFalse(policy.needsConfirmation(ToolNames.FILE_DELETE));
        f.mode = ToolSettingsStore.PERMISSION_CONFIRM;
        assertTrue(policy.needsConfirmation(ToolNames.SHELL_EXECUTE));
    }

    @Test public void modelPromptTracksTheSelectedPermissionMode() {
        Fixture f = new Fixture(ToolSettingsStore.EXECUTION_LOCAL);
        cn.lineai.ai.prompt.ToolPromptService prompts = new cn.lineai.ai.prompt.ToolPromptService(
                f.settings, f.registry, (tools, nativeProtocol) -> "Available tools");
        assertTrue(prompts.buildToolPrompt(f.enabled, true).contains("Permission mode: automatic"));
        f.mode = ToolSettingsStore.PERMISSION_CONFIRM;
        String prompt = prompts.buildToolPrompt(Collections.<ToolInfo>singletonList(f.registry.get(ToolNames.SHELL_EXECUTE)), true);
        assertTrue(prompt.contains("Permission mode: confirmation"));
        assertFalse(prompt.contains("Permission mode: automatic"));
    }

    private static final class Fixture {
        String mode = ToolSettingsStore.PERMISSION_AUTO;
        int executions;
        final Set<String> enabled = new HashSet<>(Arrays.asList(ToolNames.SHELL_EXECUTE, ToolNames.FILE_DELETE));
        final ToolRegistry registry = new ToolRegistry();
        final ToolSettingsStore settings;
        ToolPermissionService policy;
        final ToolRunController controller;
        final ToolExecutor executor;
        Fixture(String target) {
            registry.register(tool(ToolNames.SHELL_EXECUTE, ToolCategory.SYSTEM));
            registry.register(tool(ToolNames.FILE_DELETE, ToolCategory.WRITE));
            settings = (ToolSettingsStore) Proxy.newProxyInstance(getClass().getClassLoader(),
                    new Class<?>[]{ToolSettingsStore.class}, (proxy, method, args) -> {
                        switch (method.getName()) {
                            case "getPermissionMode": return mode;
                            case "getExecutionMode": return target;
                            case "getEnabledToolNames": return new HashSet<>(enabled);
                            case "canExecuteTool": return policy.canExecuteTool((String) args[0], (ToolCategory) args[1]);
                            case "needsConfirmation": return policy.needsConfirmation((String) args[0]);
                            default: return method.getReturnType() == boolean.class ? false : null;
                        }
                    });
            policy = new ToolPermissionService(settings, registry);
            controller = new ToolRunController(new ToolExecutionCoordinator(registry), registry, settings);
            executor = new ToolExecutor(registry, settings, null, null, null, null, null);
        }
        private BaseTool tool(String name, ToolCategory category) {
            return new BaseTool() {
                public String getName() { return name; }
                public String getDescription() { return "Permission policy test"; }
                public ToolCategory getCategory() { return category; }
                public boolean needsConfirmation() { return true; }
                public JSONObject getParameters() { return new JSONObject(); }
                public ToolResult execute(JSONObject input, ToolContext context) {
                    executions++;
                    return ToolResult.of(context.getToolCallId(), name, "Executed", false);
                }
            };
        }
    }
}
