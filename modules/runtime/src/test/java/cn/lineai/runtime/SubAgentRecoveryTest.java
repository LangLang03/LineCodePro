package cn.lineai.runtime;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.AgentSessionRecord;
import cn.lineai.core.model.AgentSessionStatus;

import org.junit.Test;

/**
 * Stale-session recovery contract: a record that was RUNNING or WAITING_INPUT when the
 * process died must load as INTERRUPTED, while terminal records stay untouched.
 */
public class SubAgentRecoveryTest {

    private static AgentSessionRecord record(String id, AgentSessionStatus status) {
        return new AgentSessionRecord(id, id, "root", "conv-" + id, "fixer", "/workspace",
                status, true, 1L, 1L, "");
    }

    @Test public void midTurnRecordsBecomeInterrupted() {
        List<AgentSessionRecord> loaded = Arrays.asList(
                record("running", AgentSessionStatus.RUNNING),
                record("waiting", AgentSessionStatus.WAITING_INPUT),
                record("done", AgentSessionStatus.COMPLETED),
                record("failed", AgentSessionStatus.FAILED),
                record("interrupted", AgentSessionStatus.INTERRUPTED));

        List<AgentSessionRecord> changed = SubAgentRegistry.interruptedOnLoad(loaded, 99L);

        assertEquals(2, changed.size());
        List<String> ids = new ArrayList<>();
        for (AgentSessionRecord updated : changed) {
            ids.add(updated.id);
            assertEquals(AgentSessionStatus.INTERRUPTED, updated.status);
            assertEquals(99L, updated.updatedAt);
            assertEquals("conv-" + updated.id, updated.conversationId);
        }
        assertTrue(ids.contains("running"));
        assertTrue(ids.contains("waiting"));
    }

    @Test public void terminalRecordsNeedNoRewrite() {
        List<AgentSessionRecord> loaded = Arrays.asList(
                record("done", AgentSessionStatus.COMPLETED),
                record("failed", AgentSessionStatus.FAILED));
        assertTrue(SubAgentRegistry.interruptedOnLoad(loaded, 99L).isEmpty());
    }
}
