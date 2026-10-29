package cn.lineai.core.model;

/** Optional SkillHub presentation metadata shared by list rows and the detail hero. */
public final class SkillHubMeta {

    public final String iconUrl;
    public final long stars;
    public final boolean verified;
    public final String category;
    public final boolean requiresApiKey;

    public SkillHubMeta(String iconUrl, long stars, boolean verified, String category,
                        boolean requiresApiKey) {
        this.iconUrl = iconUrl == null ? "" : iconUrl;
        this.stars = Math.max(0, stars);
        this.verified = verified;
        this.category = category == null ? "" : category;
        this.requiresApiKey = requiresApiKey;
    }

    public static SkillHubMeta empty() {
        return new SkillHubMeta("", 0, false, "", false);
    }
}
