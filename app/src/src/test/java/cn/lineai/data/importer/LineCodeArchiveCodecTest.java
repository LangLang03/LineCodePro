package cn.lineai.data.importer;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LineCodeArchiveCodecTest {
    @Rule
    public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void writesZipArchiveWithLegacyPayloadAndWorkspaceRoots() throws Exception {
        File home = temporaryFolder.newFolder("home");
        File project = temporaryFolder.newFolder("project");
        File skills = temporaryFolder.newFolder("skills");
        write(new File(home, "notes.txt"), "home file");
        write(new File(project, "Main.java"), "class Main {}");
        write(new File(skills, "SKILL.md"), "# Skill");

        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        settings.put("@linecode_chat_mode", "agent");
        ImportedLineCodeData data = new ImportedLineCodeData(
                Collections.singletonList(new ModelConfig(
                        "m1",
                        "OpenAI",
                        ModelProtocolType.OPENAI_COMPATIBLE,
                        "OpenAI",
                        "https://api.example.test",
                        "sk-test",
                        "gpt-test"
                )),
                "m1",
                Collections.singletonList(new ConversationRecord(
                        "c1",
                        "迁移测试",
                        "project",
                        100L,
                        200L,
                        true,
                        "",
                        Collections.singletonList(new MessageRecord(
                                "u1",
                                ChatMessage.Role.USER,
                                "你好",
                                "",
                                101L,
                                false,
                                false,
                                false,
                                "",
                                "",
                                false,
                                ""
                        ))
                )),
                "c1",
                settings
        );

        ByteArrayOutputStream output = new ByteArrayOutputStream();
        new LineCodeArchiveCodec().writeArchive(data, home, project, skills, null, output);
        byte[] archive = output.toByteArray();

        assertEquals('P', archive[0]);
        assertEquals('K', archive[1]);

        LineCodeArchiveCodec codec = new LineCodeArchiveCodec();
        File payload = temporaryFolder.newFolder("payload");
        codec.extractArchive(new ByteArrayInputStream(archive), payload);
        ImportedLineCodeData restoredData = codec.readLegacyPayload(payload);

        assertTrue(new File(payload, LineCodeArchiveCodec.ENTRY_MANIFEST).isFile());
        assertTrue(new File(payload, LineCodeArchiveCodec.ENTRY_ASYNC_STORAGE).isFile());
        assertEquals("m1", restoredData.getSelectedModelId());
        assertEquals("", restoredData.getModels().get(0).getApiKey());
        assertEquals("agent", restoredData.getSettings().get("@linecode_chat_mode"));
        assertEquals(1, restoredData.getConversations().size());
        assertEquals("你好", restoredData.getConversations().get(0).getMessages().get(0).getContent());

        File restoredHome = temporaryFolder.newFolder("restored-home");
        File restoredProject = temporaryFolder.newFolder("restored-project");
        File restoredSkills = temporaryFolder.newFolder("restored-skills");
        int restoredFiles = codec.restoreWorkspaceRoots(payload, restoredHome, restoredProject, restoredSkills, true);

        assertEquals(3, restoredFiles);
        assertEquals("home file", read(new File(restoredHome, "notes.txt")));
        assertEquals("class Main {}", read(new File(restoredProject, "Main.java")));
        assertEquals("# Skill", read(new File(restoredSkills, "SKILL.md")));
    }

    @Test
    public void rejectsZipEntriesOutsideTargetDirectory() throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        ZipOutputStream zip = new ZipOutputStream(output);
        zip.putNextEntry(new ZipEntry("../escape.txt"));
        zip.write("bad".getBytes(StandardCharsets.UTF_8));
        zip.closeEntry();
        zip.close();

        try {
            new LineCodeArchiveCodec().extractArchive(
                    new ByteArrayInputStream(output.toByteArray()),
                    temporaryFolder.newFolder("payload")
            );
            fail("Expected zip slip entry to be rejected.");
        } catch (IllegalArgumentException expected) {
            assertTrue(expected.getMessage().contains("越界路径"));
        }
    }

    private static void write(File file, String value) throws Exception {
        File parent = file.getParentFile();
        if (parent != null && !parent.exists()) {
            parent.mkdirs();
        }
        FileOutputStream output = new FileOutputStream(file);
        try {
            output.write(value.getBytes(StandardCharsets.UTF_8));
        } finally {
            output.close();
        }
    }

    private static String read(File file) throws Exception {
        byte[] bytes = java.nio.file.Files.readAllBytes(file.toPath());
        return new String(bytes, StandardCharsets.UTF_8);
    }
}
