package cn.lineai.data.service;

import cn.lineai.model.SkillHubModels;
import cn.lineai.security.SimpleHttpClient;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONObject;

public final class SkillHubClient {
    private static final String API_ROOT = "https://api.skillhub.cn";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_JSON_CHARS = 2 * 1024 * 1024;
    private static final int MAX_MARKDOWN_CHARS = 512 * 1024;
    private static final int MAX_ZIP_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ICON_BYTES = 512 * 1024;

    public SkillHubModels.Page list(int page, int pageSize, String keyword, String category,
                                    String source, String sortBy, String order) throws Exception {
        int safePage = Math.max(1, page);
        int safePageSize = Math.max(1, Math.min(50, pageSize));
        StringBuilder url = new StringBuilder(API_ROOT + "/api/skills?page=")
                .append(safePage).append("&pageSize=").append(safePageSize);
        append(url, "keyword", keyword);
        append(url, "category", category);
        if (!"all".equals(source)) {
            append(url, "source", source);
        }
        append(url, "sortBy", sortBy);
        append(url, "order", order);
        JSONObject root = getJson(url.toString());
        if (root.optInt("code", -1) != 0) {
            throw new IllegalArgumentException("SkillHub 返回错误: " + root.optString("message"));
        }
        JSONObject data = root.optJSONObject("data");
        if (data == null) {
            throw new IllegalArgumentException("SkillHub 列表响应缺少 data");
        }
        JSONArray values = data.optJSONArray("skills");
        ArrayList<SkillHubModels.Summary> skills = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) {
                    skills.add(parseSummary(value));
                }
            }
        }
        return new SkillHubModels.Page(skills, data.optLong("total"));
    }

    public SkillHubModels.Detail detail(String rawSlug) throws Exception {
        String slug = requireSlug(rawSlug);
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug));
        JSONObject skill = root.optJSONObject("skill");
        if (skill == null) {
            throw new IllegalArgumentException("SkillHub 详情响应缺少 skill");
        }
        JSONObject latestVersion = root.optJSONObject("latestVersion");
        JSONObject namespace = root.optJSONObject("namespace");
        JSONObject owner = root.optJSONObject("owner");
        JSONObject publisher = root.optJSONObject("publisher");
        JSONObject stats = skill.optJSONObject("stats");
        JSONObject labels = skill.optJSONObject("labels");
        String description = prefer(skill.optString("summary_zh"), skill.optString("summary"));
        String version = latestVersion == null ? "" : latestVersion.optString("version");
        SkillHubModels.Summary summary = new SkillHubModels.Summary(
                slug,
                skill.optString("displayName", slug),
                description,
                owner == null ? "" : prefer(owner.optString("displayName"), owner.optString("handle")),
                skill.optString("category"),
                skill.optString("source"),
                version,
                skill.optString("iconUrl"),
                stats == null ? 0 : stats.optLong("downloads"),
                stats == null ? 0 : stats.optLong("stars"),
                skill.optLong("updatedAt"),
                skill.optBoolean("verified") || skill.optBoolean("isAuthorVerified"),
                labels != null && "true".equalsIgnoreCase(labels.optString("requires_api_key")),
                subCategories(skill.optJSONArray("subCategories"))
        );
        String namespaceHandle = namespace == null ? "" : namespace.optString("handle");
        JSONObject security = preferredSecurityReport(root.optJSONObject("securityReports"));
        List<SkillHubModels.FileEntry> skillFiles = files(slug, namespaceHandle);
        return new SkillHubModels.Detail(
                summary,
                namespace == null ? "" : namespace.optString("canonicalName"),
                publisher == null ? "" : publisher.optString("name"),
                security == null ? "" : security.optString("status"),
                security == null ? "" : security.optString("statusText"),
                markdown(slug, version, namespaceHandle),
                strings(skill.optJSONArray("tags")),
                skillFiles,
                comments(slug, namespaceHandle),
                versions(slug, namespaceHandle),
                evaluation(slug, namespaceHandle),
                testCases(slug, namespaceHandle)
        );
    }

    public String fileContent(String rawSlug, String rawVersion, String rawPath) throws Exception {
        String slug = requireSlug(rawSlug);
        String version = requireVersion(rawVersion);
        String path = requireFilePath(rawPath);
        String value = SimpleHttpClient.get(
                API_ROOT + "/api/v1/skills/" + encode(slug) + "/file?version="
                        + encode(version) + "&path=" + encode(path),
                CONNECT_TIMEOUT_MS,
                READ_TIMEOUT_MS);
        if (value.length() > MAX_MARKDOWN_CHARS) {
            throw new IllegalArgumentException("SkillHub 文件内容过大");
        }
        return value;
    }

    public byte[] download(String rawSlug, String rawVersion) throws Exception {
        String slug = requireSlug(rawSlug);
        String version = requireVersion(rawVersion);
        SimpleHttpClient.DownloadResult result = SimpleHttpClient.download(
                API_ROOT + "/api/v1/download?slug=" + encode(slug) + "&version=" + encode(version),
                CONNECT_TIMEOUT_MS,
                60000,
                MAX_ZIP_BYTES
        );
        byte[] bytes = result.bytes;
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IllegalArgumentException("SkillHub 下载内容不是有效 ZIP");
        }
        return bytes;
    }

    public byte[] icon(String rawUrl) throws Exception {
        String url = requireIconUrl(rawUrl);
        SimpleHttpClient.DownloadResult result = SimpleHttpClient.download(
                url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS, MAX_ICON_BYTES);
        if (!result.mimeType.toLowerCase().startsWith("image/")) {
            throw new IllegalArgumentException("SkillHub 图标响应不是图片");
        }
        return result.bytes;
    }

    static String requireIconUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        try {
            URI uri = new URI(value);
            String host = uri.getHost();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || !("skillhub.cn".equalsIgnoreCase(host)
                    || "www.skillhub.cn".equalsIgnoreCase(host)
                    || "api.skillhub.cn".equalsIgnoreCase(host)
                    || "cloudcache.tencent-cloud.com".equalsIgnoreCase(host)
                    || "skillhub-1388575217.cos.accelerate.myqcloud.com".equalsIgnoreCase(host))) {
                throw new IllegalArgumentException("无效的 SkillHub 图标地址");
            }
            return uri.toASCIIString();
        } catch (IllegalArgumentException e) {
            throw e;
        } catch (Exception e) {
            throw new IllegalArgumentException("无效的 SkillHub 图标地址", e);
        }
    }

    private List<SkillHubModels.FileEntry> files(String slug, String namespace) throws Exception {
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug) + "/files"
                + namespaceQuery(namespace));
        JSONArray values = root.optJSONArray("files");
        ArrayList<SkillHubModels.FileEntry> files = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) {
                    files.add(new SkillHubModels.FileEntry(
                            value.optString("path"), value.optString("sha256"), value.optLong("size")));
                }
            }
        }
        return files;
    }

    private String markdown(String slug, String version, String namespace) throws Exception {
        if (version.length() == 0) {
            return "";
        }
        String url = API_ROOT + "/api/v1/skills/" + encode(slug) + "/file?version="
                + encode(version) + "&path=" + encode("SKILL.md");
        if (namespace.length() > 0) {
            url += "&namespace=" + encode(namespace);
        }
        String value = SimpleHttpClient.get(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        if (value.length() > MAX_MARKDOWN_CHARS) {
            throw new IllegalArgumentException("SkillHub Skill 文档过大");
        }
        return value;
    }

    private List<SkillHubModels.Comment> comments(String slug, String namespace) throws Exception {
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug) + "/comments"
                + namespaceQuery(namespace));
        JSONArray values = root.optJSONArray("items");
        ArrayList<SkillHubModels.Comment> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) {
                    result.add(parseComment(value));
                }
            }
        }
        return result;
    }

    public List<SkillHubModels.Comment> commentReplies(
            String rawSlug, long commentId, String namespace) throws Exception {
        String slug = requireSlug(rawSlug);
        if (commentId <= 0) {
            throw new IllegalArgumentException("无效的评论 ID");
        }
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug)
                + "/comments/" + commentId + "/replies" + namespaceQuery(namespace));
        JSONArray values = root.optJSONArray("items");
        ArrayList<SkillHubModels.Comment> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) {
                    result.add(parseComment(value));
                }
            }
        }
        return result;
    }

    static SkillHubModels.Comment parseComment(JSONObject value) {
        JSONObject user = value.optJSONObject("user");
        JSONObject replies = value.optJSONObject("replies");
        JSONArray preview = replies == null ? null : replies.optJSONArray("preview");
        ArrayList<SkillHubModels.Comment> children = new ArrayList<>();
        if (preview != null) {
            for (int i = 0; i < preview.length(); i++) {
                JSONObject child = preview.optJSONObject(i);
                if (child != null) {
                    children.add(parseComment(child));
                }
            }
        }
        String author = prefer(
                user == null ? "" : user.optString("displayName"),
                value.optString("authorName"));
        return new SkillHubModels.Comment(
                value.optLong("id"),
                user == null ? value.optLong("userId") : user.optLong("id", value.optLong("userId")),
                value.optLong("parentId"),
                author,
                user == null ? "" : user.optString("handle"),
                user == null ? value.optString("authorAvatar") : user.optString("avatarUrl"),
                value.optString("content"),
                value.optLong("createdAt"),
                value.optLong("likeCount"),
                replies == null ? value.optLong("replyCount") : replies.optLong("total"),
                value.optBoolean("liked"),
                value.optString("status"),
                strings(value.optJSONArray("imageUrls")),
                children);
    }

    private List<SkillHubModels.Version> versions(String slug, String namespace) throws Exception {
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug) + "/versions"
                + namespaceQuery(namespace));
        JSONArray values = root.optJSONArray("versions");
        ArrayList<SkillHubModels.Version> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) {
                    continue;
                }
                JSONObject security = preferredSecurityReport(value.optJSONObject("securityReports"));
                result.add(new SkillHubModels.Version(
                        value.optString("version"), value.optString("changelog"), value.optLong("createdAt"),
                        security == null ? "" : security.optString("status"),
                        security == null ? "" : security.optString("statusText")));
            }
        }
        return result;
    }

    private SkillHubModels.Evaluation evaluation(String slug, String namespace) throws Exception {
        JSONObject value = getJson(API_ROOT + "/api/v1/skills/" + encode(slug) + "/evaluation"
                + namespaceQuery(namespace));
        JSONObject dimensions = value.optJSONObject("dimensions");
        ArrayList<String> highlights = new ArrayList<>();
        ArrayList<String> suggestions = new ArrayList<>();
        double score = 0;
        int scoreCount = 0;
        if (dimensions != null) {
            for (String key : new String[] {"effectiveness", "reliability", "adaptability", "convention", "trust"}) {
                JSONObject dimension = dimensions.optJSONObject(key);
                if (dimension == null) {
                    continue;
                }
                double valueScore = dimension.optDouble("score", 0);
                if (valueScore > 0) {
                    score += valueScore;
                    scoreCount++;
                }
                String userReason = dimension.optString("userReason").trim();
                if (userReason.length() > 0) {
                    highlights.add(userReason);
                }
                String suggestion = dimension.optString("suggestion").trim();
                if (suggestion.length() > 0) {
                    suggestions.add(suggestion);
                }
            }
        }
        return new SkillHubModels.Evaluation(
                value.length() == 0 ? "" : "completed",
                scoreCount == 0 ? 0 : score / scoreCount,
                prefer(value.optString("userSummary"), value.optString("summary")),
                highlights, suggestions);
    }

    private List<SkillHubModels.TestCase> testCases(String slug, String namespace) throws Exception {
        JSONObject root = getJson(API_ROOT + "/api/v1/skills/" + encode(slug) + "/testcases"
                + namespaceQuery(namespace));
        JSONArray values = root.optJSONArray("testcases");
        ArrayList<SkillHubModels.TestCase> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null) {
                    result.add(new SkillHubModels.TestCase(
                            "示例 " + (i + 1), value.optString("question"), value.optString("answer")));
                }
            }
        }
        return result;
    }

    private String namespaceQuery(String namespace) {
        return namespace == null || namespace.trim().length() == 0
                ? "" : "?namespace=" + encode(namespace.trim());
    }

    private JSONObject getJson(String url) throws Exception {
        String body = SimpleHttpClient.get(url, CONNECT_TIMEOUT_MS, READ_TIMEOUT_MS);
        if (body.length() > MAX_JSON_CHARS) {
            throw new IllegalArgumentException("SkillHub 响应过大");
        }
        try {
            return new JSONObject(body);
        } catch (Exception e) {
            throw new IllegalArgumentException("SkillHub 返回了无效 JSON", e);
        }
    }

    static SkillHubModels.Summary parseSummary(JSONObject value) {
        String slug = value.optString("slug").trim();
        if (!isSafeSlug(slug)) {
            throw new IllegalArgumentException("SkillHub 返回了无效 slug");
        }
        JSONObject namespace = value.optJSONObject("namespace");
        JSONObject labels = value.optJSONObject("labels");
        return new SkillHubModels.Summary(
                slug,
                value.optString("name", slug),
                prefer(value.optString("description_zh"), value.optString("description")),
                prefer(value.optString("ownerName"), namespace == null ? "" : namespace.optString("displayName")),
                value.optString("category"),
                value.optString("source"),
                value.optString("version"),
                value.optString("iconUrl"),
                value.optLong("downloads"),
                value.optLong("stars"),
                value.optLong("updated_at"),
                value.optBoolean("verified"),
                labels != null && "true".equalsIgnoreCase(labels.optString("requires_api_key")),
                subCategories(value.optJSONArray("subCategories"))
        );
    }

    private static List<String> strings(JSONArray values) {
        ArrayList<String> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                String value = values.optString(i).trim();
                if (value.length() > 0) {
                    result.add(value);
                }
            }
        }
        return result;
    }

    private static List<String> subCategories(JSONArray values) {
        ArrayList<String> result = new ArrayList<>();
        if (values != null) {
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value != null && value.optString("name").trim().length() > 0) {
                    result.add(value.optString("name").trim());
                }
            }
        }
        return result;
    }

    private static JSONObject preferredSecurityReport(JSONObject reports) {
        if (reports == null) {
            return null;
        }
        JSONObject suspicious = null;
        for (String name : new String[] {"keen", "sanbu"}) {
            JSONObject report = reports.optJSONObject(name);
            if (report == null) {
                continue;
            }
            if (!"benign".equalsIgnoreCase(report.optString("status"))) {
                return report;
            }
            suspicious = report;
        }
        return suspicious;
    }

    private static void append(StringBuilder url, String key, String value) {
        if (value != null && value.trim().length() > 0) {
            url.append('&').append(key).append('=').append(encode(value.trim()));
        }
    }

    static String requireSlug(String value) {
        String slug = value == null ? "" : value.trim();
        if (!isSafeSlug(slug)) {
            throw new IllegalArgumentException("无效的 SkillHub slug");
        }
        return slug;
    }

    private static boolean isSafeSlug(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    }

    static String requireFilePath(String value) {
        String path = value == null ? "" : value.trim();
        if (path.length() == 0 || path.length() > 512 || path.startsWith("/")
                || path.indexOf('\\') >= 0 || path.indexOf('\u0000') >= 0) {
            throw new IllegalArgumentException("无效的 SkillHub 文件路径");
        }
        for (String segment : path.split("/", -1)) {
            if (segment.length() == 0 || ".".equals(segment) || "..".equals(segment)) {
                throw new IllegalArgumentException("无效的 SkillHub 文件路径");
            }
        }
        return path;
    }

    private static String requireVersion(String value) {
        String version = value == null ? "" : value.trim();
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")) {
            throw new IllegalArgumentException("无效的 SkillHub 版本");
        }
        return version;
    }

    private static String prefer(String first, String second) {
        return first != null && first.trim().length() > 0 ? first.trim()
                : second == null ? "" : second.trim();
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception e) {
            throw new IllegalArgumentException(e);
        }
    }
}
