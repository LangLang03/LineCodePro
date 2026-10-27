package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;

import cn.lineai.core.model.Project;

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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteProjectStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private SqliteProjectStore store;
    private long now = 100L;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "projects.db"));
        store = new SqliteProjectStore(database, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    @Test public void ensuresAndSelectsDefaultProject() throws Exception {
        File dir = temporary.newFolder("workspace");
        Project project = store.ensureDefault(dir);
        assertEquals(Project.DEFAULT_ID, project.id);
        assertTrue(project.selected);
        assertEquals(dir.getAbsolutePath(), project.path);
        assertEquals(project.id, store.selected().id);
    }

    @Test public void creatingASecondProjectSelectsIt() throws Exception {
        store.ensureDefault(temporary.newFolder("workspace"));
        Project created = store.create("Feature", temporary.newFolder("feature").getAbsolutePath());
        assertTrue(created.selected);
        assertEquals(created.id, store.selected().id);
        assertEquals(2, store.list().size());
        assertFalse(store.list().get(1).selected);
    }

    @Test public void deletingSelectedFallsBackToRemaining() throws Exception {
        store.ensureDefault(temporary.newFolder("workspace"));
        Project created = store.create("Feature", temporary.newFolder("feature").getAbsolutePath());
        store.delete(created.id);
        assertNotNull(store.selected());
        assertEquals(Project.DEFAULT_ID, store.selected().id);
    }

    @Test public void defaultProjectCannotBeDeleted() throws Exception {
        store.ensureDefault(temporary.newFolder("workspace"));
        store.delete(Project.DEFAULT_ID);
        assertEquals(1, store.list().size());
    }
}
