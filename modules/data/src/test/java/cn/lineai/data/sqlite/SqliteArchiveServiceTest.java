package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
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
public class SqliteArchiveServiceTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase source;
    private LineCodeDatabase target;

    @Before public void create() {
        source = new LineCodeDatabase(new File(temporary.getRoot(), "source.db"));
        target = new LineCodeDatabase(new File(temporary.getRoot(), "target.db"));
    }

    @After public void close() {
        source.close();
        target.close();
    }

    @Test public void roundTripsWorkspaceFilesAsBinary() throws Exception {
        java.io.File sourceFiles = temporary.newFolder("files-source");
        java.io.File targetFiles = temporary.newFolder("files-target");
        java.io.File notes = new java.io.File(sourceFiles, "workspace/docs/notes.txt");
        notes.getParentFile().mkdirs();
        byte[] binary = new byte[] {0, 1, (byte) 0xFF, 65};
        java.nio.file.Files.write(notes.toPath(), binary);
        java.io.File skill = new java.io.File(sourceFiles, "skills/review/SKILL.md");
        skill.getParentFile().mkdirs();
        java.nio.file.Files.write(skill.toPath(), "# Review".getBytes("UTF-8"));

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2), sourceFiles).export(bytes);
        new SqliteArchiveService(target, Clock.fixed(3), targetFiles)
                .importFrom(new ByteArrayInputStream(bytes.toByteArray()));

        java.util.List<String> names = new java.util.ArrayList<>();
        try (java.util.zip.ZipInputStream zin = new java.util.zip.ZipInputStream(
                new ByteArrayInputStream(bytes.toByteArray()))) {
            java.util.zip.ZipEntry entry;
            while ((entry = zin.getNextEntry()) != null) names.add(entry.getName());
        }
        java.io.File restored = new java.io.File(targetFiles, "workspace/docs/notes.txt");
        assertTrue(names.toString(), restored.isFile());
        org.junit.Assert.assertArrayEquals(binary,
                java.nio.file.Files.readAllBytes(restored.toPath()));
        assertTrue(new java.io.File(targetFiles, "skills/review/SKILL.md").isFile());
    }

    @Test public void roundTripsEveryTable() {
        SqliteConversationStore conversations = new SqliteConversationStore(source, Clock.fixed(1));
        SqliteMessageStore messages = new SqliteMessageStore(source);
        String conversationId = conversations.create("Trip").id;
        messages.append(Message.builder().id("m1").conversationId(conversationId).seq(1)
                .role(Role.USER).addBlock(ContentBlock.text("hello world")).build());
        new SqliteSettingsStore(source, Clock.fixed(1)).put("ui.themeMode", "light");
        new SqliteModelStore(source).save(ModelConfig.builder().identity("model1", "Mock")
                .endpoint("custom", "mock-model", "https://example.com/v1").build());
        new SqliteErrorLogStore(source, Clock.fixed(1)).record("t", "s", "d");

        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        cn.lineai.data.api.ArchiveService.Summary exported =
                new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);
        assertEquals(1, exported.conversations);
        assertEquals(1, exported.models);
        assertTrue(exported.settings >= 1);

        cn.lineai.data.api.ArchiveService.Summary imported =
                new SqliteArchiveService(target, Clock.fixed(3))
                        .importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals(1, imported.conversations);
        assertEquals(1, imported.models);

        assertEquals(1, new SqliteConversationStore(target, Clock.fixed(1)).list().size());
        assertEquals("hello world", new SqliteMessageStore(target).load(conversationId).get(0).plainText());
        assertEquals("light", new SqliteSettingsStore(target, Clock.fixed(1)).get("ui.themeMode", ""));
        assertEquals("Mock", new SqliteModelStore(target).selected().displayName());
        assertEquals(1, new SqliteErrorLogStore(target, Clock.fixed(1)).list().size());
    }

    @Test public void importReplacesExistingRowsAndIsRepeatable() {
        new SqliteSettingsStore(source, Clock.fixed(1)).put("k", "v");
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        new SqliteArchiveService(source, Clock.fixed(2)).export(bytes);
        new SqliteSettingsStore(target, Clock.fixed(1)).put("stale", "1");
        SqliteArchiveService service = new SqliteArchiveService(target, Clock.fixed(3));
        service.importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        service.importFrom(new ByteArrayInputStream(bytes.toByteArray()));
        assertEquals("v", new SqliteSettingsStore(target, Clock.fixed(1)).get("k", ""));
        assertEquals("", new SqliteSettingsStore(target, Clock.fixed(1)).get("stale", ""));
    }

    @Test public void rejectsGarbageAndWrongFormat() {
        try {
            new SqliteArchiveService(target, Clock.fixed(1))
                    .importFrom(new ByteArrayInputStream(new byte[] {1, 2, 3}));
            fail("expected failure");
        } catch (DomainException expected) {
            assertNotNull(expected.code());
        }
    }
}
