package cn.lineai.feature.extensions;

import java.io.IOException;

import cn.lineai.core.model.Skill;

/**
 * Supplies the WebView session cookie to the SkillHub client.
 *
 * <p>Authentication reuses the WebView cookie jar so the app never stores credentials itself;
 * every request reads the cookie for the API host at call time.</p>
 */
final class SkillHubSession {

    private static final String API_ROOT = "https://api.skillhub.cn";
    private final cn.lineai.engine.skillhub.SkillHubSessionClient client =
            new cn.lineai.engine.skillhub.SkillHubSessionClient();

    /**
     * Checks the sign-in state against the SkillHub API.
     *
     * @return true when a valid session cookie exists
     * @throws IOException on network failure
     */
    boolean signedIn() throws IOException {
        return client.signedIn(cookie());
    }

    /**
     * Publishes a skill to SkillHub under the given slug.
     *
     * @param skill skill to publish
     * @param slug public slug of the skill
     * @param displayName display name of the release
     * @param version version label of the release
     * @throws IOException on network failure or API rejection
     */
    void publish(Skill skill, String slug, String displayName, String version)
            throws IOException {
        client.publish(skill, slug, displayName, version, cookie());
    }

    /**
     * Posts a comment on a published skill.
     *
     * @param slug slug of the skill to comment on
     * @param content comment text
     * @throws IOException on network failure or API rejection
     */
    void postComment(String slug, String content) throws IOException {
        client.postComment(slug, content, cookie());
    }

    /**
     * Reads the current WebView cookie for the API host.
     *
     * @return cookie header value; empty when none is available
     */
    private static String cookie() {
        String value = android.webkit.CookieManager.getInstance().getCookie(API_ROOT);
        return value == null ? "" : value.trim();
    }
}
