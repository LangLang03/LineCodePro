package cn.lineai.core.model;

/** One SkillHub search result. */
public final class SkillHubEntry {

    public final String slug;
    public final String name;
    public final String description;
    public final String author;
    public final String version;
    public final long downloads;
    public final SkillHubMeta meta;

    public SkillHubEntry(String slug, String name, String description, String author,
                         String version, long downloads) {
        this(slug, name, description, author, version, downloads, SkillHubMeta.empty());
    }

    public SkillHubEntry(String slug, String name, String description, String author,
                         String version, long downloads, SkillHubMeta meta) {
        this.slug = slug == null ? "" : slug;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.author = author == null ? "" : author;
        this.version = version == null ? "" : version;
        this.downloads = downloads;
        this.meta = meta == null ? SkillHubMeta.empty() : meta;
    }
}
