package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;

import cn.lineai.core.model.FileDiff;

import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteDiffStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteDiffStore store;
    private File root;
    private long now = 1L;

    @Before public void create() throws Exception {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "diffs.db"));
        store = new SqliteDiffStore(database, () -> now++);
        root = temporary.newFolder("workspace");
    }

    @After public void close() {
        database.close();
    }

    @Test public void revertRestoresPreviousContent() throws Exception {
        File file = new File(root, "Main.java");
        Files.write(file.toPath(), "after".getBytes(StandardCharsets.UTF_8));
        store.record(root.getAbsolutePath(), "Main.java", "before", "after", true);
        List<FileDiff> diffs = store.list();
        assertEquals(1, diffs.size());
        assertTrue(store.revert(diffs.get(0).id));
        assertEquals("before", new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8));
        assertTrue(store.list().get(0).reverted);
        assertFalse(store.revert(diffs.get(0).id));
    }

    @Test public void revertDeletesFilesThatDidNotExistBefore() throws Exception {
        File file = new File(root, "New.java");
        Files.write(file.toPath(), "new".getBytes(StandardCharsets.UTF_8));
        store.record(root.getAbsolutePath(), "New.java", "", "new", false);
        assertTrue(store.revert(store.list().get(0).id));
        assertFalse(file.exists());
    }

    @Test public void revertRefusesPathsOutsideTheRecordedRoot() throws Exception {
        File outside = new File(temporary.getRoot(), "outside.txt");
        Files.write(outside.toPath(), "safe".getBytes(StandardCharsets.UTF_8));
        store.record(root.getAbsolutePath(), "../outside.txt", "changed", "x", true);
        assertFalse(store.revert(store.list().get(0).id));
        assertEquals("safe", new String(Files.readAllBytes(outside.toPath()), StandardCharsets.UTF_8));
    }

    @Test public void clearRemovesAllDiffs() throws Exception {
        store.record(root.getAbsolutePath(), "a.txt", "", "a", false);
        store.clear();
        assertTrue(store.list().isEmpty());
    }
}
