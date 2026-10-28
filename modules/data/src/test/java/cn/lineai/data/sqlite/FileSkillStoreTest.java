package cn.lineai.data.sqlite;

import android.app.Application;

import java.io.File;
import java.util.List;

import cn.lineai.core.model.Skill;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

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
public class FileSkillStoreTest {

    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    private LineCodeDatabase database;
    private FileSkillStore store;
    private long now = 1_000L;

    @Before public void create() throws Exception {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "settings.db"));
        SqliteSettingsStore settings = new SqliteSettingsStore(database, () -> now++);
        store = new FileSkillStore(temporary.newFolder("skills"), settings, () -> now++);
    }

    @After public void close() {
        database.close();
    }

    @Test public void createsAndListsSkillMarkdown() {
        Skill saved = store.save(Skill.builder()
                .name("Android review")
                .description("Reviews native Android changes")
                .content("## Steps\n- read the diff")
                .build());
        assertFalse(saved.id.isEmpty());
        assertEquals("Android review", saved.name);
        List<Skill> all = store.list();
        assertEquals(1, all.size());
        assertEquals("Reviews native Android changes", all.get(0).description);
        assertTrue(all.get(0).content.contains("read the diff"));
        assertTrue(all.get(0).enabled);
    }

    @Test public void updateWritesSameDirectory() {
        Skill saved = store.save(Skill.builder().name("one").content("v1").build());
        store.save(saved.toBuilder().name("one").description("d").content("v2").build());
        List<Skill> all = store.list();
        assertEquals(1, all.size());
        assertTrue(all.get(0).content.contains("v2"));
        assertFalse(all.get(0).content, all.get(0).content.contains("v1"));
    }

    @Test public void enableFlagLivesInSettings() {
        Skill saved = store.save(Skill.builder().name("two").content("x").build());
        store.setEnabled(saved.id, false);
        assertTrue(store.enabled().isEmpty());
        assertFalse(store.list().get(0).enabled);
        store.setEnabled(saved.id, true);
        assertEquals(1, store.enabled().size());
    }

    private byte[] zip(String entry, String content) throws Exception {
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(bytes)) {
            zip.putNextEntry(new ZipEntry(entry));
            zip.write(content.getBytes("UTF-8"));
            zip.closeEntry();
        }
        return bytes.toByteArray();
    }

    @Test public void importsSkillZip() throws Exception {
        Skill imported = store.importSkill(new ByteArrayInputStream(zip("pack/SKILL.md",
                "---\nname: Imported\ndescription: From zip\n---\n\n# Imported\n\nbody")), "pack.zip");
        assertEquals("Imported", imported.name);
        assertEquals(1, store.list().size());
        assertEquals("From zip", store.list().get(0).description);
    }

    @Test public void importsSingleMarkdownFile() throws Exception {
        byte[] markdown = "# Single\n\nbody".getBytes("UTF-8");
        Skill imported = store.importSkill(new ByteArrayInputStream(markdown), "SKILL.md");
        assertEquals("Single", imported.name);
    }

    @Test public void rejectsZipTraversal() throws Exception {
        try {
            store.importSkill(new ByteArrayInputStream(zip("../evil.txt", "x")), "bad.zip");
            throw new AssertionError("expected rejection");
        } catch (java.io.IOException expected) {
            // unsafe entries are rejected
        }
        assertTrue(store.list().isEmpty());
    }

    @Test public void deletesSkillDirectory() {
        Skill saved = store.save(Skill.builder().name("gone").content("x").build());
        store.delete(saved.id);
        assertTrue(store.list().isEmpty());
    }
}
