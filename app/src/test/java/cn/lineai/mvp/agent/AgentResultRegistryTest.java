package cn.lineai.mvp.agent;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.json.JSONObject;
import org.junit.Test;

public final class AgentResultRegistryTest {

    @Test
    public void allocateIdIsUniqueAndPrefixed() {
        AgentResultRegistry registry = new AgentResultRegistry();
        String a = registry.allocateId();
        String b = registry.allocateId();
        assertTrue(a.startsWith("ag_"));
        assertTrue(b.startsWith("ag_"));
        assertNotEquals(a, b);
    }

    @Test
    public void putGetRoundTrip() {
        AgentResultRegistry registry = new AgentResultRegistry();
        String id = registry.allocateId();
        AgentResultRecord record = AgentResultRecord.running(
                id, "tc1", "agent", "explore", "scan modules", false, 1);
        registry.put(record);

        AgentResultRecord loaded = registry.getRecord(id);
        assertNotNull(loaded);
        assertEquals(id, loaded.getAgentId());
        assertEquals("tc1", loaded.getToolCallId());
        assertEquals("agent", loaded.getToolName());
        assertEquals("running", loaded.getStatus());
        assertEquals("explore", loaded.getType());
        assertEquals("scan modules", loaded.getDescription());
        assertFalse(loaded.isError());
        assertFalse(loaded.isAsync());
        assertEquals(1, loaded.getGenerationId());
        assertNotNull(registry.get(id));
        assertEquals("running", registry.get(id).getStatus());
    }

    @Test
    public void getUnknownReturnsNull() {
        assertNull(new AgentResultRegistry().getRecord("ag_missing"));
        assertNull(new AgentResultRegistry().get("ag_missing"));
    }

    @Test
    public void compactJsonRoundTripPreservesRefMarkerAndFields() throws Exception {
        AgentResultRegistry registry = new AgentResultRegistry();
        String id = registry.allocateId();
        AgentResultRecord record = AgentResultRecord.running(
                id, "tc9", "agent", "explore", "find callers", true, 3);
        record = record.withPreview("partial line of output that should be clamped if too long");
        registry.put(record);

        String compact = AgentResultRegistry.toCompactJson(registry.getRecord(id));
        JSONObject object = new JSONObject(compact);
        assertTrue(object.getBoolean("linecode_agent_ref"));
        assertEquals(id, object.getString("agent_id"));
        assertEquals("running", object.getString("status"));
        assertEquals("explore", object.getString("type"));
        assertEquals("find callers", object.getString("description"));
        assertTrue(object.getBoolean("async"));
        assertEquals("tc9", object.getString("tool_call_id"));

        AgentResultRecord parsed = AgentResultRegistry.parseCompact(compact);
        assertNotNull(parsed);
        assertEquals(id, parsed.getAgentId());
        assertEquals("running", parsed.getStatus());
        assertTrue(parsed.isAsync());
    }

    @Test
    public void parseCompactRejectsNonRefPayload() {
        assertNull(AgentResultRegistry.parseCompact("{\"linecode_agent_progress\":true}"));
        assertNull(AgentResultRegistry.parseCompact("not json"));
        assertNull(AgentResultRegistry.parseCompact(null));
    }

    @Test
    public void previewClampAt240Chars() {
        StringBuilder longPreview = new StringBuilder();
        for (int i = 0; i < 300; i++) {
            longPreview.append('x');
        }
        AgentResultRecord record = AgentResultRecord.running(
                "ag_test", "tc", "agent", "explore", "desc", false, 0)
                .withPreview(longPreview.toString());
        assertEquals(240, record.getPreview().length());

        String compact = AgentResultRegistry.toCompactJson(record);
        AgentResultRecord parsed = AgentResultRegistry.parseCompact(compact);
        assertNotNull(parsed);
        assertEquals(240, parsed.getPreview().length());
    }

    @Test
    public void updateFullOutputMarksDoneAndStoresBody() {
        AgentResultRegistry registry = new AgentResultRegistry();
        String id = registry.allocateId();
        registry.put(AgentResultRecord.running(id, "tc", "agent", "explore", "job", false, 1));
        registry.updateFullOutput(id, "full agent answer body", "think", "{\"progress\":true}", 4);

        AgentResultRecord loaded = registry.getRecord(id);
        assertNotNull(loaded);
        assertEquals("done", loaded.getStatus());
        assertEquals("full agent answer body", loaded.getFullOutput());
        assertEquals("think", loaded.getThinking());
        assertEquals("{\"progress\":true}", loaded.getProgressJson());
        assertEquals(4, loaded.getToolCallCount());
        assertFalse(loaded.isError());
        assertTrue(loaded.getPreview().length() > 0);
        assertTrue(loaded.getPreview().length() <= 240);
        assertEquals("full agent answer body", registry.get(id).getFullOutput());
    }

    @Test
    public void updateStatusError() {
        AgentResultRegistry registry = new AgentResultRegistry();
        String id = registry.allocateId();
        registry.put(AgentResultRecord.running(id, "tc", "agent", "explore", "job", false, 1));
        registry.updateStatus(id, "error", true, "boom");

        AgentResultRecord loaded = registry.getRecord(id);
        assertNotNull(loaded);
        assertEquals("error", loaded.getStatus());
        assertTrue(loaded.isError());
        assertEquals("boom", loaded.getPreview());
    }

    @Test
    public void clearGenerationRemovesOnlyMatching() {
        AgentResultRegistry registry = new AgentResultRegistry();
        String keep = registry.allocateId();
        String drop = registry.allocateId();
        registry.put(AgentResultRecord.running(keep, "a", "agent", "explore", "k", false, 1));
        registry.put(AgentResultRecord.running(drop, "b", "agent", "explore", "d", false, 2));
        registry.clearGeneration(2);
        assertNotNull(registry.getRecord(keep));
        assertNull(registry.getRecord(drop));
    }
}
