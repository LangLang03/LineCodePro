package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import cn.lineai.data.api.StorageStats;
import java.io.File;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteStorageStatsStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteStorageStatsStore stats;
    private File home;

    @Before public void create() throws Exception {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "stats.db"));
        home = temporary.newFolder("home");
        stats = new SqliteStorageStatsStore(database, home);
    }

    @After public void close() { database.close(); }

    @Test public void countsChatRowsAndConfigRows() {
        SqliteConversationStore conversations = new SqliteConversationStore(database, Clock.fixed(1));
        SqliteMessageStore messages = new SqliteMessageStore(database);
        String conversationId = conversations.create("t").id;
        messages.append(Message.builder().id("m1").conversationId(conversationId).seq(1)
                .role(Role.USER).addBlock(ContentBlock.text("hello")).build());
        new SqliteSettingsStore(database, Clock.fixed(1)).put("key", "value");
        new SqliteModelStore(database).save(ModelConfig.builder().identity("m", "M")
                .endpoint("p", "id", "https://example.com").build());
        StorageStats value = stats.load();
        assertEquals(1, value.chatCount);
        assertTrue(value.chatBytes >= 5);
        assertTrue(value.configCount >= 2);
        assertTrue(value.configBytes > 0);
    }

    @Test public void clearingChatKeepsConfiguration() {
        SqliteConversationStore conversations = new SqliteConversationStore(database, Clock.fixed(1));
        SqliteMessageStore messages = new SqliteMessageStore(database);
        String id = conversations.create("t").id;
        messages.append(Message.builder().id("m1").conversationId(id).seq(1)
                .role(Role.USER).addBlock(ContentBlock.text("hi")).build());
        new SqliteSettingsStore(database, Clock.fixed(1)).put("key", "value");
        stats.clearChatHistory();
        StorageStats value = stats.load();
        assertEquals(0, value.chatCount);
        assertEquals(0, value.chatBytes);
        assertTrue(value.configCount >= 1);
        assertEquals("value", new SqliteSettingsStore(database, Clock.fixed(1)).get("key", ""));
    }

    @Test public void homeUsageWalksFilesRecursively() throws Exception {
        File nested = new File(home, "a/b");
        assertTrue(nested.mkdirs());
        java.nio.file.Files.write(new File(home, "one.txt").toPath(), new byte[10]);
        java.nio.file.Files.write(new File(nested, "two.txt").toPath(), new byte[20]);
        StorageStats value = stats.load();
        assertEquals(30, value.homeBytes);
        assertEquals(2, value.homeCount);
        assertEquals(value.totalBytes(), value.chatBytes + value.configBytes + value.homeBytes);
    }
}
