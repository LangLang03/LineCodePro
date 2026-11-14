package cn.lineai.core.model;

/** Optional SkillHub presentation metadata shared by list rows and the detail hero. */
public final class SkillHubMeta {

    public final String iconUrl;
    public final long stars;
    public final boolean verified;
    public final String category;
    public final boolean requiresApiKey;

    /**
     * Creates presentation metadata, normalising null strings to empty.
     * @param iconUrl artwork URL, may be blank
     * @param stars star count, negative values clamp to zero
     * @param verified whether the publisher is verified by the hub
     * @param category hub category label, may be blank
     * @param requiresApiKey whether the skill needs a user-supplied API key
     */
    public SkillHubMeta(String iconUrl, long stars, boolean verified, String category,
                        boolean requiresApiKey) {
        this.iconUrl = iconUrl == null ? "" : iconUrl;
        this.stars = Math.max(0, stars);
        this.verified = verified;
        this.category = category == null ? "" : category;
        this.requiresApiKey = requiresApiKey;
    }

    /** Returns the metadata used when the hub supplies none. */
    public static SkillHubMeta empty() {
        return new SkillHubMeta("", 0, false, "", false);
    }
}
