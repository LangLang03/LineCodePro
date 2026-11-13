package cn.lineai.core.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Full SkillHub detail used by the store detail screen. */
public final class SkillHubDetail {

    public final String slug;
    public final String name;
    public final String description;
    public final String author;
    public final String version;
    public final String markdown;
    public final long downloads;
    public final String securityStatus;
    public final List<String> tags;
    public final SkillHubMeta meta;

    /**
     * Convenience overload that supplies an empty {@link SkillHubMeta}.
     * @param slug unique hub slug
     * @param name display name
     * @param description full description
     * @param author publisher name
     * @param version published version string
     * @param markdown rendered SKILL.md content
     * @param downloads download count
     * @param securityStatus hub security assessment label
     * @param tags hub tags
     */
    public SkillHubDetail(String slug, String name, String description, String author,
                          String version, String markdown, long downloads,
                          String securityStatus, List<String> tags) {
        this(slug, name, description, author, version, markdown, downloads, securityStatus, tags,
                SkillHubMeta.empty());
    }

    /**
     * Creates a detail record, normalising null strings to empty and copying {@code tags}.
     * @param slug unique hub slug
     * @param name display name
     * @param description full description
     * @param author publisher name
     * @param version published version string
     * @param markdown rendered SKILL.md content
     * @param downloads download count
     * @param securityStatus hub security assessment label
     * @param tags hub tags, null becomes an empty list
     * @param meta presentation metadata, null becomes {@link SkillHubMeta#empty()}
     */
    public SkillHubDetail(String slug, String name, String description, String author,
                          String version, String markdown, long downloads,
                          String securityStatus, List<String> tags, SkillHubMeta meta) {
        this.slug = slug == null ? "" : slug;
        this.name = name == null ? "" : name;
        this.description = description == null ? "" : description;
        this.author = author == null ? "" : author;
        this.version = version == null ? "" : version;
        this.markdown = markdown == null ? "" : markdown;
        this.downloads = downloads;
        this.securityStatus = securityStatus == null ? "" : securityStatus;
        this.tags = tags == null ? Collections.<String>emptyList()
                : Collections.unmodifiableList(new ArrayList<>(tags));
        this.meta = meta == null ? SkillHubMeta.empty() : meta;
    }
}
