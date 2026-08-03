package cn.lineai.tool.ui;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class AgentToolResultDisplayTest {

    @Test
    public void parsesAgentRefMarkerAndFields() throws Exception {
        String content = new JSONObject()
                .put("linecode_agent_ref", true)
                .put("agent_id", "ag_abc")
                .put("status", "done")
                .put("type", "explore")
                .put("description", "scan modules")
                .put("preview", "short preview")
                .toString();
        assertTrue(AgentToolResultDisplay.isAgentRef(content));
        assertEquals("ag_abc", AgentToolResultDisplay.agentId(content));
        assertEquals("done", AgentToolResultDisplay.progressStatus(content));
        assertEquals("scan modules", AgentToolResultDisplay.description(content, "fallback"));
        assertEquals("short preview", AgentToolResultDisplay.preview(content));
        assertEquals("short preview", AgentToolResultDisplay.displayOutput(content));
    }

    @Test
    public void nonRefProgressStillParsed() throws Exception {
        String content = new JSONObject()
                .put("linecode_agent_progress", true)
                .put("status", "running")
                .put("output", "streaming...")
                .put("description", "old style")
                .toString();
        assertFalse(AgentToolResultDisplay.isAgentRef(content));
        assertEquals("running", AgentToolResultDisplay.progressStatus(content));
        assertEquals("streaming...", AgentToolResultDisplay.displayOutput(content));
        assertEquals("old style", AgentToolResultDisplay.description(content, "fb"));
    }

    @Test
    public void unknownJsonNotDumpedAsOutput() {
        assertEquals("", AgentToolResultDisplay.displayOutput("{\"foo\":1}"));
    }
}
