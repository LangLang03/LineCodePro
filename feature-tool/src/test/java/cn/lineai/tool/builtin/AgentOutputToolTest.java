package cn.lineai.tool.builtin;
import cn.lineai.model.tool.ToolResult;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.tool.StoredAgentResult;
import cn.lineai.tool.ToolContext;
import java.util.HashMap;
import java.util.Map;
import org.json.JSONObject;
import org.junit.Test;

public final class AgentOutputToolTest {

    @Test
    public void missingIdIsError() {
        AgentOutputTool tool = new AgentOutputTool();
        ToolResult result = tool.execute(new JSONObject(), context(null));
        assertTrue(result.isError());
    }

    @Test
    public void unknownIdIsError() throws Exception {
        AgentOutputTool tool = new AgentOutputTool();
        MapStore store = new MapStore();
        ToolResult result = tool.execute(new JSONObject().put("agent_id", "ag_missing"), context(store));
        assertTrue(result.isError());
        assertTrue(result.getContent().contains("ag_missing"));
    }

    @Test
    public void runningReturnsStatusPayload() throws Exception {
        AgentOutputTool tool = new AgentOutputTool();
        MapStore store = new MapStore();
        store.put(new StoredAgentResult(
                "ag_1", "running", "explore", "scan", "partial", "", false, true, 1));
        ToolResult result = tool.execute(new JSONObject().put("agent_id", "ag_1"), context(store));
        assertFalse(result.isError());
        JSONObject object = new JSONObject(result.getContent());
        assertEquals("running", object.getString("status"));
        assertEquals("ag_1", object.getString("agent_id"));
        assertTrue(object.getString("message").length() > 0);
    }

    @Test
    public void doneReturnsFullOutput() throws Exception {
        AgentOutputTool tool = new AgentOutputTool();
        MapStore store = new MapStore();
        store.put(new StoredAgentResult(
                "ag_2", "done", "explore", "scan", "prev", "FULL BODY OUTPUT", false, false, 3));
        ToolResult result = tool.execute(new JSONObject().put("agent_id", "ag_2"), context(store));
        assertFalse(result.isError());
        assertEquals("FULL BODY OUTPUT", result.getContent());
    }

    @Test
    public void errorRecordReturnsErrorResult() throws Exception {
        AgentOutputTool tool = new AgentOutputTool();
        MapStore store = new MapStore();
        store.put(new StoredAgentResult(
                "ag_3", "error", "explore", "scan", "boom", "failed detail", true, false, 0));
        ToolResult result = tool.execute(new JSONObject().put("agent_id", "ag_3"), context(store));
        assertTrue(result.isError());
        assertEquals("failed detail", result.getContent());
    }

    @Test
    public void metaIncludeReturnsJsonFields() throws Exception {
        AgentOutputTool tool = new AgentOutputTool();
        MapStore store = new MapStore();
        store.put(new StoredAgentResult(
                "ag_4", "done", "explore", "scan", "prev", "FULL", false, false, 2));
        ToolResult result = tool.execute(
                new JSONObject().put("agent_id", "ag_4").put("include", "meta"), context(store));
        assertFalse(result.isError());
        JSONObject object = new JSONObject(result.getContent());
        assertEquals("done", object.getString("status"));
        assertEquals(2, object.getInt("tool_call_count"));
    }

    private static ToolContext context(ToolContext.AgentResultStore store) {
        return ToolContext.builder()
                .agentResultStore(store)
                .stringResolver(new ToolContext.StringResolver() {
                    @Override
                    public String getString(int resId) {
                        return "";
                    }

                    @Override
                    public String getString(int resId, Object... formatArgs) {
                        if (formatArgs == null || formatArgs.length == 0) {
                            return "";
                        }
                        return String.valueOf(formatArgs[0]);
                    }
                })
                .build();
    }

    private static final class MapStore implements ToolContext.AgentResultStore {
        private final Map<String, StoredAgentResult> map = new HashMap<>();

        void put(StoredAgentResult result) {
            map.put(result.getAgentId(), result);
        }

        @Override
        public StoredAgentResult get(String agentId) {
            return map.get(agentId);
        }
    }
}
