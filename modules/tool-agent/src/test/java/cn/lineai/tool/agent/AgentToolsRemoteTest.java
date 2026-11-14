package cn.lineai.tool.agent;

import org.junit.Test;

import java.util.Arrays;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.AgentEntry;
import cn.lineai.tool.api.AgentStatus;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

/**
 * Agent tools must stay visible in remote workspace modes (SSH and terminal provider):
 * the workspace policy filters on {@code needsLocalWorkspace}, and a remote dispatch passes no
 * workspace binding because those modes cannot bind a local directory, so the sub agent
 * session inherits the parent remote workspace.
 */
public class AgentToolsRemoteTest {

    private static final AgentRuntime RUNTIME = new AgentRuntime() {
        public AgentEntry start(AgentExtension agent, String task, boolean persistent,
                                String workspace) {
            return new AgentEntry("sa", agent.name, "agent", "conv", AgentStatus.RUNNING);
        }
        public java.util.List<AgentEntry> list() { return java.util.Collections.emptyList(); }
        public String send(String agentId, String message) { return ""; }
        public java.util.List<AgentEntry> waitFor(long timeoutMs) {
            return java.util.Collections.emptyList();
        }
    };

    private static AgentExtension agent() {
        return AgentExtension.builder().name("Fixer").slug("fixer").prompt("p")
                .enabled(true).build();
    }

    @Test public void everyAgentToolIsWorkspaceAgnostic() {
        ToolRegistry registry = AgentTools.augment(new ToolRegistry(),
                () -> Arrays.asList(agent()), RUNTIME);
        for (Tool tool : registry.all()) {
            assertFalse(tool.name(), tool.needsLocalWorkspace());
        }
        assertEquals(4, registry.all().size());
    }
}
