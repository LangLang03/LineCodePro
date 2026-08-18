package cn.lineai.data.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.SkillRecord;
import cn.lineai.resource.ResourceProvider;
import java.io.File;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.List;
import java.util.Locale;
import org.json.JSONObject;
import org.junit.Test;

public final class SkillHubSessionClientTest {
    /**
     * Fake resource provider used by the JVM unit tests (no Android resources
     * available). It maps the handful of resource ids exercised by these tests
     * back to the Chinese texts asserted below.
     */
    private static final ResourceProvider RES = new ResourceProvider() {
        @Override
        public InputStream openAsset(String path) {
            throw new UnsupportedOperationException("openAsset not used in tests");
        }

        @Override
        public String getString(int resId) {
            if (resId == cn.lineai.R.string.skillhub_error_sensitive_file) {
                return "Skill 包含敏感文件：";
            }
            if (resId == cn.lineai.R.string.skillhub_error_file_too_large_with_name) {
                return "Skill 文件过大：";
            }
            if (resId == cn.lineai.R.string.skillhub_error_invalid_session) {
                return "无效的 SkillHub 会话";
            }
            return "test";
        }

        @Override
        public String getString(int resId, Object... formatArgs) {
            return String.format(Locale.ROOT, getString(resId), formatArgs);
        }
    };

    private static SkillHubSessionClient client() {
        return new SkillHubSessionClient(RES);
    }

    @Test
    public void parsesNestedUserAccount() throws Exception {
        JSONObject root = new JSONObject().put("user", new JSONObject()
                .put("displayName", "测试用户")
                .put("handle", "tester")
                .put("avatarUrl", "https://skillhub.cn/avatar.png"));

        SkillHubSessionClient.Account account = client().parseAccount(root);

        assertEquals("测试用户", account.getDisplayName());
        assertEquals("tester", account.getHandle());
        assertEquals("https://skillhub.cn/avatar.png", account.getAvatarUrl());
    }

    @Test
    public void parsesFallbackUserFields() throws Exception {
        JSONObject root = new JSONObject()
                .put("nickname", "昵称")
                .put("username", "name");

        SkillHubSessionClient.Account account = client().parseAccount(root);

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
                client().collectPublishFiles(skill);

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
            client().collectPublishFiles(skill(root));
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
        client().requireSafeCookie("sid=value\r\nX-Test: injected");
    }
}