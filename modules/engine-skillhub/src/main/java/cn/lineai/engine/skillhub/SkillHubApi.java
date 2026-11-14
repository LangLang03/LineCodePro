package cn.lineai.engine.skillhub;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.SkillHubDetail;
import cn.lineai.core.model.SkillHubComment;
import cn.lineai.core.model.SkillHubEntry;
import cn.lineai.core.model.SkillHubMeta;

/**
 * Minimal read-only SkillHub client: search and download.
 *
 * <p>Every request is synchronous, size-capped and unauthenticated; download sizes and icon hosts
 * are validated so the client cannot be tricked into fetching arbitrary payloads.</p>
 */
public final class SkillHubApi {

    private static final String ROOT = "https://api.skillhub.cn";
    private static final int MAX_ZIP_BYTES = 20 * 1024 * 1024;
    private static final int MAX_ICON_BYTES = 4 * 1024 * 1024;

    /**
     * Lists one page of entries in the service default order.
     *
     * @param keyword free-text filter; blank lists everything
     * @param page one-based page index
     * @return the entries of that page
     * @throws IOException on network failure or when the service reports an error
     */
    public List<SkillHubEntry> list(String keyword, int page) throws IOException {
        return list(keyword, page, "");
    }

    /**
     * Lists one page ordered by {@code sort} ({@code downloads}, {@code stars}, {@code updated_at}).
     *
     * @param keyword free-text filter; blank lists everything
     * @param page one-based page index, clamped to at least one
     * @param sort server-side sort key; blank uses the service default
     * @return the entries of that page
     * @throws IOException on network failure or when the service reports an error
     */
    public List<SkillHubEntry> list(String keyword, int page, String sort) throws IOException {
        int safePage = Math.max(1, page);
        StringBuilder url = new StringBuilder(ROOT + "/api/skills?page=")
                .append(safePage).append("&pageSize=20");
        if (sort != null && !sort.trim().isEmpty()) {
            url.append("&sortBy=").append(encode(sort.trim())).append("&order=desc");
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            url.append("&keyword=").append(encode(keyword.trim()));
        }
        String body = get(url.toString(), 2 * 1024 * 1024);
        List<SkillHubEntry> entries = parseList(body);
        if (entries == null) throw new IOException("SkillHub returned an error");
        return entries;
    }

    /**
     * Fetches one skill detail together with its SKILL.md content. A missing SKILL.md degrades to
     * empty content rather than failing the call.
     *
     * @param slug skill slug
     * @return the detail record including the fetched markdown when a version exists
     * @throws IOException when the slug is blank, the skill is unknown, or the request fails
     */
    public SkillHubDetail detail(String slug) throws IOException {
        String safeSlug = slug == null ? "" : slug.trim();
        if (safeSlug.isEmpty()) throw new IOException("Invalid SkillHub slug");
        String body = get(ROOT + "/api/v1/skills/" + encode(safeSlug), 2 * 1024 * 1024);
        SkillHubDetail detail = parseDetail(safeSlug, body);
        if (detail == null) throw new IOException("SkillHub returned no skill");
        if (detail.version.isEmpty()) return detail;
        String markdown;
        try {
            markdown = get(ROOT + "/api/v1/skills/" + encode(safeSlug) + "/file?version="
                    + encode(detail.version) + "&path=SKILL.md", 512 * 1024);
        } catch (IOException error) {
            markdown = "";
        }
        return new SkillHubDetail(detail.slug, detail.name, detail.description, detail.author,
                detail.version, markdown, detail.downloads, detail.securityStatus, detail.tags,
                detail.meta);
    }

    /**
     * Downloads a remote skill icon after validating the host allow-list.
     *
     * @param rawUrl icon URL advertised by the service
     * @return the icon bytes
     * @throws IOException when the URL host is not allowed, the icon is empty, or the download
     *         fails
     */
    public byte[] icon(String rawUrl) throws IOException {
        String url = requireIconUrl(rawUrl);
        byte[] bytes = getBytes(url, MAX_ICON_BYTES);
        if (bytes.length == 0) throw new IOException("SkillHub icon is empty");
        return bytes;
    }

    /**
     * Enforces the icon host allow-list over HTTPS and returns the normalized ASCII URL.
     *
     * @param rawUrl icon URL advertised by the service
     * @return the validated URL string
     * @throws IOException when the URL is malformed or its host is not on the allow-list
     */
    static String requireIconUrl(String rawUrl) throws IOException {
        String value = rawUrl == null ? "" : rawUrl.trim();
        try {
            java.net.URI uri = new java.net.URI(value);
            String host = uri.getHost();
            boolean allowed = "https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && ("skillhub.cn".equalsIgnoreCase(host)
                    || "www.skillhub.cn".equalsIgnoreCase(host)
                    || "api.skillhub.cn".equalsIgnoreCase(host)
                    || "cloudcache.tencent-cloud.com".equalsIgnoreCase(host)
                    || "skillhub-1388575217.cos.accelerate.myqcloud.com".equalsIgnoreCase(host));
            if (!allowed) throw new IOException("Invalid SkillHub icon URL");
            return uri.toASCIIString();
        } catch (java.net.URISyntaxException error) {
            throw new IOException("Invalid SkillHub icon URL", error);
        }
    }

    /**
     * Reads the comment thread of one skill.
     *
     * @param slug skill slug
     * @return the parsed comments in service order; empty when none exist
     * @throws IOException when the slug is blank or the request fails
     */
    public java.util.List<SkillHubComment> comments(String slug) throws IOException {
        String safeSlug = slug == null ? "" : slug.trim();
        if (safeSlug.isEmpty()) throw new IOException("Invalid SkillHub slug");
        String body = get(ROOT + "/api/v1/skills/" + encode(safeSlug) + "/comments",
                2 * 1024 * 1024);
        return parseComments(body);
    }

    /** Parses a comment list payload; malformed items and bodies are skipped silently. */
    static java.util.List<SkillHubComment> parseComments(String body) {
        java.util.List<SkillHubComment> result = new java.util.ArrayList<>();
        try {
            JSONArray values = new JSONObject(body).optJSONArray("items");
            if (values == null) return result;
            for (int i = 0; i < values.length(); i++) {
                JSONObject value = values.optJSONObject(i);
                if (value == null) continue;
                JSONObject user = value.optJSONObject("user");
                String author = user == null ? ""
                        : first(user.optString("displayName"), user.optString("handle"));
                String content = value.optString("content", "").trim();
                if (content.isEmpty()) continue;
                result.add(new SkillHubComment(value.optLong("id"), author, content,
                        value.optLong("createdAt")));
            }
        } catch (Exception error) {
            return result;
        }
        return result;
    }

    /** Parses one detail payload; returns null when the payload has no skill object. */
    static SkillHubDetail parseDetail(String slug, String body) {
        try {
            JSONObject root = new JSONObject(body);
            JSONObject skill = root.optJSONObject("skill");
            if (skill == null) return null;
            JSONObject owner = root.optJSONObject("owner");
            JSONObject latest = root.optJSONObject("latestVersion");
            JSONObject stats = skill.optJSONObject("stats");
            JSONObject security = preferredSecurity(root.optJSONObject("securityReports"));
            JSONArray tagArray = skill.optJSONArray("tags");
            java.util.List<String> tags = new java.util.ArrayList<>();
            if (tagArray != null) {
                for (int i = 0; i < tagArray.length(); i++) {
                    String tag = tagArray.optString(i, "").trim();
                    if (!tag.isEmpty()) tags.add(tag);
                }
            }
            return new SkillHubDetail(slug,
                    first(skill.optString("displayName"), slug),
                    description(skill),
                    author(skill, owner),
                    version(skill, latest),
                    "", downloads(skill, stats),
                    security == null ? "" : security.optString("statusText"), tags,
                    meta(skill, stats));
        } catch (Exception error) {
            return null;
        }
    }

    /** Prefers the dedicated SkillHub security report over the static analysis one. */
    private static JSONObject preferredSecurity(JSONObject reports) {
        if (reports == null) return null;
        JSONObject preferred = reports.optJSONObject("skillhub");
        return preferred == null ? reports.optJSONObject("static") : preferred;
    }

    /**
     * Downloads one skill package and verifies it is a ZIP payload before handing it over.
     *
     * @param slug skill slug
     * @param version package version; blank resolves to the latest version
     * @return the ZIP bytes of the package
     * @throws IOException when the slug is blank, the payload is not a ZIP, or the download fails
     */
    public byte[] download(String slug, String version) throws IOException {
        String safeSlug = slug == null ? "" : slug.trim();
        if (safeSlug.isEmpty()) throw new IOException("Invalid SkillHub slug");
        String safeVersion = version == null || version.trim().isEmpty() ? "latest" : version.trim();
        String url = ROOT + "/api/v1/download?slug=" + encode(safeSlug)
                + "&version=" + encode(safeVersion);
        byte[] bytes = getBytes(url, MAX_ZIP_BYTES);
        if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
            throw new IOException("SkillHub download is not a valid ZIP");
        }
        return bytes;
    }

    /**
     * Parses one list payload. Returns null to signal a service-reported error, distinguishing it
     * from a valid empty page.
     */
    static List<SkillHubEntry> parseList(String body) {
        try {
            JSONObject root = new JSONObject(body);
            if (root.optInt("code", 0) != 0) return null;
            JSONObject data = root.optJSONObject("data");
            if (data == null) return null;
            JSONArray skills = data.optJSONArray("skills");
            List<SkillHubEntry> result = new ArrayList<>();
            if (skills == null) return result;
            for (int i = 0; i < skills.length(); i++) {
                JSONObject skill = skills.optJSONObject(i);
                if (skill == null) continue;
                String slug = skill.optString("slug", "").trim();
                if (slug.isEmpty()) continue;
                JSONObject owner = skill.optJSONObject("owner");
                JSONObject latest = skill.optJSONObject("latestVersion");
                JSONObject stats = skill.optJSONObject("stats");
                result.add(new SkillHubEntry(
                        slug,
                        first(skill.optString("displayName"), slug),
                        description(skill),
                        author(skill, owner),
                        version(skill, latest),
                        downloads(skill, stats),
                        meta(skill, stats)));
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    /**
     * The list endpoint is flat ({@code description/downloads/ownerName/iconUrl/stars}) while the
     * detail endpoint nests them under {@code stats}/{@code owner}; accept both shapes.
     */
    private static String description(JSONObject skill) {
        return first(first(skill.optString("description_zh"), skill.optString("description")),
                first(skill.optString("summary_zh"), skill.optString("summary")));
    }

    /** Resolves the display author, preferring the nested owner over flat payload fields. */
    private static String author(JSONObject skill, JSONObject owner) {
        if (owner != null) {
            String name = first(owner.optString("displayName"), owner.optString("handle"));
            if (!name.isEmpty()) return name;
        }
        String ownerName = skill.optString("ownerName");
        if (!ownerName.isEmpty()) return ownerName;
        JSONObject namespace = skill.optJSONObject("namespace");
        return namespace == null ? ""
                : first(namespace.optString("displayName"), namespace.optString("handle"));
    }

    /** Resolves the latest version, preferring the nested version object. */
    private static String version(JSONObject skill, JSONObject latest) {
        String value = latest == null ? "" : latest.optString("version");
        return value.isEmpty() ? skill.optString("version") : value;
    }

    /** Reads the download counter from the flat or nested location. */
    private static long downloads(JSONObject skill, JSONObject stats) {
        return skill.optLong("downloads", stats == null ? 0 : stats.optLong("downloads"));
    }

    /** Assembles the metadata record: icon, stars, verification, category and key requirement. */
    private static SkillHubMeta meta(JSONObject skill, JSONObject stats) {
        JSONObject labels = skill.optJSONObject("labels");
        long stars = skill.optLong("stars", stats == null ? 0 : stats.optLong("stars"));
        boolean verified = skill.optBoolean("verified", skill.optBoolean("isAuthorVerified"));
        boolean apiKey = labels != null
                && "true".equalsIgnoreCase(labels.optString("requires_api_key"));
        return new SkillHubMeta(skill.optString("iconUrl"), stars, verified,
                skill.optString("category"), apiKey);
    }

    /** Returns the first non-empty argument, else empty. */
    private static String first(String first, String second) {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }

    /** Percent-encodes one query component; the value is returned unchanged on failure. */
    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return value;
        }
    }

    /** Performs a GET request and decodes the capped body as UTF-8 text. */
    private static String get(String url, int maxBytes) throws IOException {
        return new String(getBytes(url, maxBytes), StandardCharsets.UTF_8);
    }

    /**
     * Performs a GET request with fixed timeouts and aborts as soon as the body exceeds
     * {@code maxBytes}, so a hostile response cannot exhaust memory.
     */
    private static byte[] getBytes(String url, int maxBytes) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setRequestProperty("Accept", "application/json");
            int code = connection.getResponseCode();
            InputStream stream = code >= 200 && code < 300
                    ? connection.getInputStream() : connection.getErrorStream();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            if (stream != null) {
                try (InputStream input = stream) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) >= 0) {
                        out.write(buffer, 0, count);
                        if (out.size() > maxBytes) throw new IOException("SkillHub response too large");
                    }
                }
            }
            if (code < 200 || code >= 300) {
                throw new IOException("SkillHub HTTP " + code);
            }
            return out.toByteArray();
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
