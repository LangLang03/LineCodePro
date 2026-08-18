package cn.lineai.data.service;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.SkillHubModels;
import cn.lineai.resource.ResourceProvider;
import org.json.JSONArray;
import org.json.JSONObject;
import org.junit.Test;

public final class SkillHubClientTest {
    private static final ResourceProvider RESOURCES = new ResourceProvider() {
        @Override
        public java.io.InputStream openAsset(String path) {
            throw new UnsupportedOperationException("not needed in unit tests");
        }

        @Override
        public String getString(int resId) {
            return "error";
        }

        @Override
        public String getString(int resId, Object... formatArgs) {
            return "error";
        }
    };

    private static final SkillHubClient CLIENT = new SkillHubClient(RESOURCES);
    @Test
    public void parseSummaryPrefersChineseDescriptionAndMapsRiskFields() throws Exception {
        JSONObject value = new JSONObject()
                .put("slug", "pdf-helper")
                .put("name", "PDF Helper")
                .put("description", "English")
                .put("description_zh", "中文说明")
                .put("ownerName", "author")
                .put("category", "office")
                .put("source", "community")
                .put("version", "1.2.0")
                .put("iconUrl", "https://skillhub.cn/icons/pdf-helper.png")
                .put("downloads", 42)
                .put("stars", 7)
                .put("updated_at", 99)
                .put("verified", true)
                .put("labels", new JSONObject().put("requires_api_key", "true"))
                .put("subCategories", new JSONArray()
                        .put(new JSONObject().put("key", "pdf").put("name", "PDF 工具")));

        SkillHubModels.Summary summary = CLIENT.parseSummary(value);

        assertEquals("pdf-helper", summary.getSlug());
        assertEquals("中文说明", summary.getDescription());
        assertEquals("PDF 工具", summary.getSubCategories().get(0));
        assertEquals("https://skillhub.cn/icons/pdf-helper.png", summary.getIconUrl());
        assertTrue(summary.isVerified());
        assertTrue(summary.requiresApiKey());
    }

    @Test(expected = IllegalArgumentException.class)
    public void parseSummaryRejectsUnsafeSlug() throws Exception {
        CLIENT.parseSummary(new JSONObject().put("slug", "../escape"));
    }

    @Test
    public void iconUrlAllowsOnlySkillHubHttpsHosts() {
        assertEquals(
                "https://api.skillhub.cn/icons/skill.png",
                CLIENT.requireIconUrl("https://api.skillhub.cn/icons/skill.png"));
        assertEquals(
                "https://cloudcache.tencent-cloud.com/qcloud/ui/icon.png",
                CLIENT.requireIconUrl("https://cloudcache.tencent-cloud.com/qcloud/ui/icon.png"));
        assertEquals(
                "https://skillhub-1388575217.cos.accelerate.myqcloud.com/skill-icons/icon.png",
                CLIENT.requireIconUrl(
                        "https://skillhub-1388575217.cos.accelerate.myqcloud.com/skill-icons/icon.png"));
        assertRejectedIconUrl("http://skillhub.cn/icons/skill.png");
        assertRejectedIconUrl("https://skillhub.cn.evil.example/icons/skill.png");
        assertRejectedIconUrl("https://example.com/icons/skill.png");
    }


    @Test
    public void filePathAllowsNestedRelativePaths() {
        assertEquals("references/api.md", CLIENT.requireFilePath("references/api.md"));
        assertEquals("SKILL.md", CLIENT.requireFilePath("SKILL.md"));
    }

    @Test(expected = IllegalArgumentException.class)
    public void filePathRejectsTraversal() {
        CLIENT.requireFilePath("references/../SKILL.md");
    }

    @Test(expected = IllegalArgumentException.class)
    public void filePathRejectsAbsolutePath() {
        CLIENT.requireFilePath("/SKILL.md");
    }

    @Test(expected = IllegalArgumentException.class)
    public void filePathRejectsBackslashes() {
        CLIENT.requireFilePath("references\\api.md");
    }
    @Test
    public void parseCommentMapsPublicReplies() throws Exception {
        JSONObject reply = new JSONObject()
                .put("id", 2)
                .put("authorName", "回复者")
                .put("content", "回复")
                .put("createdAt", 20);
        JSONObject value = new JSONObject()
                .put("id", 1)
                .put("authorName", "作者")
                .put("content", "评论")
                .put("createdAt", 10)
                .put("likeCount", 3)
                .put("replies", new JSONObject().put("preview", new JSONArray().put(reply)));

        SkillHubModels.Comment comment = SkillHubClient.parseComment(value);

        assertEquals("作者", comment.getAuthor());
        assertEquals("评论", comment.getContent());
        assertEquals(3, comment.getLikeCount());
        assertEquals(1, comment.getReplies().size());
        assertEquals("回复", comment.getReplies().get(0).getContent());
    }

    private static void assertRejectedIconUrl(String value) {
        try {
            CLIENT.requireIconUrl(value);
        } catch (IllegalArgumentException expected) {
            return;
        }
        throw new AssertionError("Expected icon URL rejection: " + value);
    }

    @Test
    public void detailDetectsScripts() {
        SkillHubModels.Summary summary = new SkillHubModels.Summary(
                "safe", "Safe", "", "", "", "community", "1.0.0", "",
                0, 0, 0, false, false, null);
        SkillHubModels.Detail detail = new SkillHubModels.Detail(
                summary, "@owner/safe", "", "benign", "安全",
                java.util.Arrays.asList(
                        new SkillHubModels.FileEntry("SKILL.md", "", 1),
                        new SkillHubModels.FileEntry("scripts/run.py", "", 2)));

        assertTrue(detail.hasScripts());
        assertFalse(detail.getFiles().isEmpty());
    }
}
