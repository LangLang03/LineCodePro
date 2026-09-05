package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class SkillHubModels {
    private SkillHubModels() {
    }

    public static class Summary {
        private final String slug;
        private final String name;
        private final String description;
        private final String owner;
        private final String category;
        private final String source;
        private final String version;
        private final String iconUrl;
        private final long downloads;
        private final long stars;
        private final long updatedAt;
        private final boolean verified;
        private final boolean requiresApiKey;
        private final List<String> subCategories;

        public Summary(String slug, String name, String description, String owner, String category,
                       String source, String version, String iconUrl, long downloads, long stars,
                       long updatedAt, boolean verified, boolean requiresApiKey,
                       List<String> subCategories) {
            this.slug = safe(slug);
            this.name = safe(name);
            this.description = safe(description);
            this.owner = safe(owner);
            this.category = safe(category);
            this.source = safe(source);
            this.version = safe(version);
            this.iconUrl = safe(iconUrl);
            this.downloads = Math.max(0, downloads);
            this.stars = Math.max(0, stars);
            this.updatedAt = Math.max(0, updatedAt);
            this.verified = verified;
            this.requiresApiKey = requiresApiKey;
            this.subCategories = immutable(subCategories);
        }

        public String getSlug() { return slug; }
        public String getName() { return name; }
        public String getDescription() { return description; }
        public String getOwner() { return owner; }
        public String getCategory() { return category; }
        public String getSource() { return source; }
        public String getVersion() { return version; }
        public String getIconUrl() { return iconUrl; }
        public long getDownloads() { return downloads; }
        public long getStars() { return stars; }
        public long getUpdatedAt() { return updatedAt; }
        public boolean isVerified() { return verified; }
        public boolean requiresApiKey() { return requiresApiKey; }
        public List<String> getSubCategories() { return subCategories; }
    }

    public static final class Detail extends Summary {
        private final String canonicalName;
        private final String publisher;
        private final String securityStatus;
        private final String securityStatusText;
        private final String markdown;
        private final List<String> tags;
        private final List<FileEntry> files;
        private final List<Comment> comments;
        private final List<Version> versions;
        private final Evaluation evaluation;
        private final List<TestCase> testCases;

        public Detail(Summary summary, String canonicalName, String publisher,
                      String securityStatus, String securityStatusText, List<FileEntry> files) {
            this(summary, canonicalName, publisher, securityStatus, securityStatusText,
                    "", null, files, null, null, null, null);
        }

        public Detail(Summary summary, String canonicalName, String publisher,
                      String securityStatus, String securityStatusText, String markdown,
                      List<String> tags, List<FileEntry> files, List<Comment> comments,
                      List<Version> versions, Evaluation evaluation, List<TestCase> testCases) {
            super(summary.getSlug(), summary.getName(), summary.getDescription(), summary.getOwner(),
                    summary.getCategory(), summary.getSource(), summary.getVersion(), summary.getIconUrl(),
                    summary.getDownloads(), summary.getStars(), summary.getUpdatedAt(), summary.isVerified(),
                    summary.requiresApiKey(), summary.getSubCategories());
            this.canonicalName = safe(canonicalName);
            this.publisher = safe(publisher);
            this.securityStatus = safe(securityStatus);
            this.securityStatusText = safe(securityStatusText);
            this.markdown = safe(markdown);
            this.tags = immutable(tags);
            this.files = immutable(files);
            this.comments = immutable(comments);
            this.versions = immutable(versions);
            this.evaluation = evaluation;
            this.testCases = immutable(testCases);
        }

        public String getCanonicalName() { return canonicalName; }
        public String getPublisher() { return publisher; }
        public String getSecurityStatus() { return securityStatus; }
        public String getSecurityStatusText() { return securityStatusText; }
        public String getMarkdown() { return markdown; }
        public List<String> getTags() { return tags; }
        public List<FileEntry> getFiles() { return files; }
        public List<Comment> getComments() { return comments; }
        public List<Version> getVersions() { return versions; }
        public Evaluation getEvaluation() { return evaluation; }
        public List<TestCase> getTestCases() { return testCases; }
        public boolean hasScripts() {
            for (FileEntry file : files) {
                if (file.getPath().startsWith("scripts/") || file.getPath().endsWith(".sh")) {
                    return true;
                }
            }
            return false;
        }
    }

    public static final class Comment {
        private final long id;
        private final long userId;
        private final long parentId;
        private final String author;
        private final String handle;
        private final String avatarUrl;
        private final String content;
        private final long createdAt;
        private final long likeCount;
        private final long replyCount;
        private final boolean liked;
        private final String status;
        private final List<String> imageUrls;
        private final List<Comment> replies;

        public Comment(long id, String author, String content, long createdAt,
                       long likeCount, List<Comment> replies) {
            this(id, 0, 0, author, "", "", content, createdAt, likeCount,
                    replies == null ? 0 : replies.size(), false, "", null, replies);
        }

        public Comment(long id, long userId, long parentId, String author,
                       String handle, String avatarUrl, String content, long createdAt,
                       long likeCount, long replyCount, boolean liked, String status,
                       List<String> imageUrls, List<Comment> replies) {
            this.id = Math.max(0, id);
            this.userId = Math.max(0, userId);
            this.parentId = Math.max(0, parentId);
            this.author = safe(author);
            this.handle = safe(handle);
            this.avatarUrl = safe(avatarUrl);
            this.content = safe(content);
            this.createdAt = Math.max(0, createdAt);
            this.likeCount = Math.max(0, likeCount);
            this.replyCount = Math.max(0, replyCount);
            this.liked = liked;
            this.status = safe(status);
            this.imageUrls = immutable(imageUrls);
            this.replies = immutable(replies);
        }

        public long getId() { return id; }
        public long getUserId() { return userId; }
        public long getParentId() { return parentId; }
        public String getAuthor() { return author; }
        public String getHandle() { return handle; }
        public String getAvatarUrl() { return avatarUrl; }
        public String getContent() { return content; }
        public long getCreatedAt() { return createdAt; }
        public long getLikeCount() { return likeCount; }
        public long getReplyCount() { return replyCount; }
        public boolean isLiked() { return liked; }
        public String getStatus() { return status; }
        public List<String> getImageUrls() { return imageUrls; }
        public List<Comment> getReplies() { return replies; }
    }

    public static final class Version {
        private final String version;
        private final String changelog;
        private final long createdAt;
        private final String securityStatus;
        private final String securityStatusText;

        public Version(String version, String changelog, long createdAt,
                       String securityStatus, String securityStatusText) {
            this.version = safe(version);
            this.changelog = safe(changelog);
            this.createdAt = Math.max(0, createdAt);
            this.securityStatus = safe(securityStatus);
            this.securityStatusText = safe(securityStatusText);
        }

        public String getVersion() { return version; }
        public String getChangelog() { return changelog; }
        public long getCreatedAt() { return createdAt; }
        public String getSecurityStatus() { return securityStatus; }
        public String getSecurityStatusText() { return securityStatusText; }
    }

    public static final class Evaluation {
        private final String status;
        private final double score;
        private final String summary;
        private final List<String> highlights;
        private final List<String> suggestions;

        public Evaluation(String status, double score, String summary,
                          List<String> highlights, List<String> suggestions) {
            this.status = safe(status);
            this.score = Math.max(0, score);
            this.summary = safe(summary);
            this.highlights = immutable(highlights);
            this.suggestions = immutable(suggestions);
        }

        public String getStatus() { return status; }
        public double getScore() { return score; }
        public String getSummary() { return summary; }
        public List<String> getHighlights() { return highlights; }
        public List<String> getSuggestions() { return suggestions; }
    }

    public static final class TestCase {
        private final String title;
        private final String prompt;
        private final String expected;

        public TestCase(String title, String prompt, String expected) {
            this.title = safe(title);
            this.prompt = safe(prompt);
            this.expected = safe(expected);
        }

        public String getTitle() { return title; }
        public String getPrompt() { return prompt; }
        public String getExpected() { return expected; }
    }

    public static final class FileEntry {
        private final String path;
        private final String sha256;
        private final long size;

        public FileEntry(String path, String sha256, long size) {
            this.path = safe(path);
            this.sha256 = safe(sha256);
            this.size = Math.max(0, size);
        }

        public String getPath() { return path; }
        public String getSha256() { return sha256; }
        public long getSize() { return size; }
    }

    public static final class Page {
        private final List<Summary> skills;
        private final long total;

        public Page(List<Summary> skills, long total) {
            this.skills = immutable(skills);
            this.total = Math.max(0, total);
        }

        public List<Summary> getSkills() { return skills; }
        public long getTotal() { return total; }
    }

    private static String safe(String value) {
        return value == null ? "" : value;
    }

    private static <T> List<T> immutable(List<T> values) {
        return Collections.unmodifiableList(new ArrayList<>(values == null
                ? Collections.<T>emptyList() : values));
    }
}
