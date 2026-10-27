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

    public SkillHubDetail(String slug, String name, String description, String author,
                          String version, String markdown, long downloads,
                          String securityStatus, List<String> tags) {
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
    }
}
