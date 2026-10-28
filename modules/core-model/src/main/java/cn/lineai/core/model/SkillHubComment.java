package cn.lineai.core.model;

/** One SkillHub comment. */
public final class SkillHubComment {

    public final long id;
    public final String author;
    public final String content;
    public final long createdAt;

    public SkillHubComment(long id, String author, String content, long createdAt) {
        this.id = id;
        this.author = author == null ? "" : author;
        this.content = content == null ? "" : content;
        this.createdAt = createdAt;
    }
}
