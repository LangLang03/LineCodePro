package cn.lineai.data.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.AgentSessionRecord;
import cn.lineai.core.model.AgentSessionStatus;
import java.io.File;
import java.util.List;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

/** Round-trip and reopen persistence of the sub agent session records (schema v15). */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class SqliteAgentSessionStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteAgentSessionStore store;
    private File dbFile;

    @Before public void create() {
        dbFile = new File(temporary.getRoot(), "test.db");
        database = new LineCodeDatabase(dbFile);
        store = new SqliteAgentSessionStore(database, Clock.fixed(1));
    }

    @After public void close() {
        database.close();
    }

    private static AgentSessionRecord record(String id, AgentSessionStatus status) {
        return new AgentSessionRecord(id, "Fixer", "root-conv", "conv-" + id, "fixer",
                "/workspace/project", status, true, 10L, 20L, "m-1");
    }

    @Test public void roundTripsEveryField() {
        store.save(record("sa-1", AgentSessionStatus.RUNNING));

        AgentSessionRecord loaded = store.find("sa-1");
        assertEquals("Fixer", loaded.name);
        assertEquals("root-conv", loaded.parentConversationId);
        assertEquals("conv-sa-1", loaded.conversationId);
        assertEquals("fixer", loaded.agentDefinitionId);
        assertEquals("/workspace/project", loaded.workspaceBinding);
        assertEquals(AgentSessionStatus.RUNNING, loaded.status);
        assertEquals(true, loaded.persistent);
        assertEquals(10L, loaded.createdAt);
        assertEquals(20L, loaded.updatedAt);
        assertEquals("m-1", loaded.lastTurnId);
    }

    @Test public void statusUpdatesReplaceTheRecord() {
        store.save(record("sa-1", AgentSessionStatus.RUNNING));
        store.save(record("sa-1", AgentSessionStatus.COMPLETED).withStatus(
                AgentSessionStatus.FAILED, 30L, "m-2"));

        List<AgentSessionRecord> all = store.list();
        assertEquals(1, all.size());
        assertEquals(AgentSessionStatus.FAILED, all.get(0).status);
        assertEquals(30L, all.get(0).updatedAt);
        assertEquals("m-2", all.get(0).lastTurnId);
    }

    @Test public void recordsSurviveReopen() {
        store.save(record("sa-1", AgentSessionStatus.COMPLETED));
        database.close();

        LineCodeDatabase reopened = new LineCodeDatabase(dbFile);
        SqliteAgentSessionStore reopenedStore = new SqliteAgentSessionStore(reopened,
                Clock.fixed(1));
        assertEquals(1, reopenedStore.list().size());
        assertEquals("Fixer", reopenedStore.find("sa-1").name);
        reopened.close();
    }

    @Test public void deleteRemovesOnlyTheRecord() {
        store.save(record("sa-1", AgentSessionStatus.COMPLETED));
        store.delete("sa-1");
        assertNull(store.find("sa-1"));
        assertEquals(0, store.list().size());
    }

    @Test public void unknownIdsReturnNull() {
        assertNull(store.find("missing"));
    }
}
