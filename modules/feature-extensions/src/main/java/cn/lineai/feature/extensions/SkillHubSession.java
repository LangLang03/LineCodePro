package cn.lineai.feature.extensions;

import java.io.IOException;

import cn.lineai.core.model.Skill;

/** Supplies the WebView session cookie to the SkillHub client. */
final class SkillHubSession {

    private static final String API_ROOT = "https://api.skillhub.cn";
    private final cn.lineai.engine.skillhub.SkillHubSessionClient client =
            new cn.lineai.engine.skillhub.SkillHubSessionClient();

    boolean signedIn() throws IOException {
        return client.signedIn(cookie());
    }

    void publish(Skill skill, String slug, String displayName, String version)
            throws IOException {
        client.publish(skill, slug, displayName, version, cookie());
    }

    void postComment(String slug, String content) throws IOException {
        client.postComment(slug, content, cookie());
    }

    private static String cookie() {
        String value = android.webkit.CookieManager.getInstance().getCookie(API_ROOT);
        return value == null ? "" : value.trim();
    }
}
