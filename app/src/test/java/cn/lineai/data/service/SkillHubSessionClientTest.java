package cn.lineai.data.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.SkillRecord;
import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import org.json.JSONObject;
import org.junit.Test;

public final class SkillHubSessionClientTest {
    @Test
    public void parsesNestedUserAccount() throws Exception {
        JSONObject root = new JSONObject().put("user", new JSONObject()
                .put("displayName", "测试用户")
                .put("handle", "tester")
                .put("avatarUrl", "https://skillhub.cn/avatar.png"));

        SkillHubSessionClient.Account account = SkillHubSessionClient.parseAccount(root);

        assertEquals("测试用户", account.getDisplayName());
        assertEquals("tester", account.getHandle());
        assertEquals("https://skillhub.cn/avatar.png", account.getAvatarUrl());
    }

    @Test
    public void parsesFallbackUserFields() throws Exception {
        JSONObject root = new JSONObject()
                .put("nickname", "昵称")
                .put("username", "name");

        SkillHubSessionClient.Account account = SkillHubSessionClient.parseAccount(root);

        assertEquals("昵称", account.getDisplayName());
        assertEquals("name", account.getHandle());
    }

    @Test
    public void collectsPublishFilesFromLocalSkill() throws Exception {
        File root = Files.createTempDirectory("skillhub-publish").toFile();
        write(root, "SKILL.md", "# Test");
        write(root, "references/guide.md", "Guide");
        SkillRecord skill = skill(root);

        List<SkillHubSessionClient.PublishFile> files =
                SkillHubSessionClient.collectPublishFiles(skill);

        assertEquals(2, files.size());
        assertEquals("SKILL.md", files.get(0).path);
        assertEquals("references/guide.md", files.get(1).path);
    }

    @Test
    public void rejectsSensitivePublishFile() throws Exception {
        File root = Files.createTempDirectory("skillhub-sensitive").toFile();
        write(root, "SKILL.md", "# Test");
        write(root, ".env", "TOKEN=value");

        try {
            SkillHubSessionClient.collectPublishFiles(skill(root));
        } catch (IllegalArgumentException error) {
            assertTrue(error.getMessage().contains("敏感文件"));
            return;
        }
        throw new AssertionError("Expected sensitive file rejection");
    }

    private static SkillRecord skill(File root) {
        return new SkillRecord("test", "Test", "Description",
                root.getAbsolutePath(), new File(root, "SKILL.md").getAbsolutePath(),
                SkillRecord.LOCATION_APP, true, 0, 0);
    }

    private static void write(File root, String relative, String content) throws Exception {
        File file = new File(root, relative);
        File parent = file.getParentFile();
        if (parent != null) {
            assertTrue(parent.mkdirs() || parent.isDirectory());
        }
        Files.write(file.toPath(), content.getBytes(StandardCharsets.UTF_8));
    }

    @Test(expected = IllegalArgumentException.class)
    public void rejectsCookieHeaderInjection() {
        SkillHubSessionClient.requireSafeCookie("sid=value\r\nX-Test: injected");
    }
}
