package cn.lineai.data.sqlite;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.Conversation;
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

/**
 * Kind semantics of the conversation store: user conversations stay separated from sub-agent
 * conversations, and opening a v13-shaped database migrates it to the kind/parent columns.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = android.app.Application.class)
public class SqliteConversationStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteConversationStore store;
    private File dbFile;

    @Before public void create() {
        dbFile = new File(temporary.getRoot(), "test.db");
        database = new LineCodeDatabase(dbFile);
        store = new SqliteConversationStore(database, Clock.fixed(1));
    }

    @After public void close() {
        database.close();
    }

    @Test public void agentConversationsStayOutOfTheUserList() {
        store.create("Root chat");
        Conversation agent = store.create("Explore the tree", Conversation.KIND_AGENT, "root");
        store.create("Quick scan", Conversation.KIND_AGENT_TRANSIENT, "root");

        List<Conversation> user = store.list();
        assertEquals(1, user.size());
        assertEquals("Root chat", user.get(0).title);

        List<Conversation> persistent = store.listByKind(Conversation.KIND_AGENT);
        assertEquals(1, persistent.size());
        assertEquals(agent.id, persistent.get(0).id);
        assertEquals("root", persistent.get(0).parentId);

        List<Conversation> transientAgents = store.listByKind(Conversation.KIND_AGENT_TRANSIENT);
        assertEquals(1, transientAgents.size());
    }

    @Test public void agentConversationsSurviveReopenAndMigrate() {
        store.create("Root chat");
        store.create("Explore", Conversation.KIND_AGENT, "root");
        database.close();

        // Reopening replays the migration (idempotent) and must not lose the kinds.
        LineCodeDatabase reopened = new LineCodeDatabase(dbFile);
        SqliteConversationStore reopenedStore =
                new SqliteConversationStore(reopened, Clock.fixed(1));
        assertEquals(1, reopenedStore.list().size());
        List<Conversation> agents = reopenedStore.listByKind(Conversation.KIND_AGENT);
        assertEquals(1, agents.size());
        assertEquals("Explore", agents.get(0).title);
        assertEquals(Conversation.KIND_AGENT, agents.get(0).kind);
        reopened.close();
    }

    @Test public void legacyRowsWithoutKindReadAsUserConversations() {
        store.create("Old chat");
        database.close();

        // Strip the v14 columns to rebuild a v13-shaped database; reopening replays the
        // column migration and the rows must read back as user conversations.
        android.database.sqlite.SQLiteDatabase raw =
                android.database.sqlite.SQLiteDatabase.openOrCreateDatabase(dbFile, null);
        raw.execSQL("CREATE TABLE legacy_rebuild (id TEXT PRIMARY KEY)");
        raw.execSQL("BEGIN");
        raw.execSQL("CREATE TABLE conversations_new AS SELECT id, title, project_id, created_at,"
                + " updated_at, message_count FROM conversations");
        raw.execSQL("DROP TABLE conversations");
        raw.execSQL("ALTER TABLE conversations_new RENAME TO conversations");
        raw.execSQL("COMMIT");
        // Mark the database as v13 so reopening replays the column migration.
        raw.execSQL("PRAGMA user_version=13");
        raw.close();

        LineCodeDatabase reopened = new LineCodeDatabase(dbFile);
        SqliteConversationStore reopenedStore =
                new SqliteConversationStore(reopened, Clock.fixed(1));
        List<Conversation> user = reopenedStore.list();
        assertEquals(1, user.size());
        assertFalse(user.get(0).isAgentConversation());
        reopened.close();
    }
}
