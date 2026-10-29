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

/** Minimal read-only SkillHub client: search and download. */
public final class SkillHubApi {

    private static final String ROOT = "https://api.skillhub.cn";
    private static final int MAX_ZIP_BYTES = 20 * 1024 * 1024;

    public List<SkillHubEntry> list(String keyword, int page) throws IOException {
        return list(keyword, page, "");
    }

    /** Lists one page ordered by {@code sort} ({@code downloads}, {@code stars}, {@code updated_at}). */
    public List<SkillHubEntry> list(String keyword, int page, String sort) throws IOException {
        int safePage = Math.max(1, page);
        StringBuilder url = new StringBuilder(ROOT + "/api/skills?page=")
                .append(safePage).append("&pageSize=20");
        if (sort != null && !sort.trim().isEmpty()) {
            url.append("&sort=").append(encode(sort.trim()));
        }
        if (keyword != null && !keyword.trim().isEmpty()) {
            url.append("&keyword=").append(encode(keyword.trim()));
        }
        String body = get(url.toString(), 2 * 1024 * 1024);
        List<SkillHubEntry> entries = parseList(body);
        if (entries == null) throw new IOException("SkillHub returned an error");
        return entries;
    }

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
                detail.version, markdown, detail.downloads, detail.securityStatus, detail.tags);
    }

    public java.util.List<SkillHubComment> comments(String slug) throws IOException {
        String safeSlug = slug == null ? "" : slug.trim();
        if (safeSlug.isEmpty()) throw new IOException("Invalid SkillHub slug");
        String body = get(ROOT + "/api/v1/skills/" + encode(safeSlug) + "/comments",
                2 * 1024 * 1024);
        return parseComments(body);
    }

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
                    first(skill.optString("summary_zh"), skill.optString("summary")),
                    owner == null ? "" : first(owner.optString("displayName"),
                            owner.optString("handle")),
                    latest == null ? skill.optString("version") : latest.optString("version"),
                    "", stats == null ? 0 : stats.optLong("downloads"),
                    security == null ? "" : security.optString("statusText"), tags);
        } catch (Exception error) {
            return null;
        }
    }

    private static JSONObject preferredSecurity(JSONObject reports) {
        if (reports == null) return null;
        JSONObject preferred = reports.optJSONObject("skillhub");
        return preferred == null ? reports.optJSONObject("static") : preferred;
    }

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
                        first(skill.optString("summary_zh"), skill.optString("summary")),
                        owner == null ? "" : first(owner.optString("displayName"),
                                owner.optString("handle")),
                        latest == null ? skill.optString("version") : latest.optString("version"),
                        stats == null ? 0 : stats.optLong("downloads")));
            }
            return result;
        } catch (Exception error) {
            return null;
        }
    }

    private static String first(String first, String second) {
        return first == null || first.isEmpty() ? (second == null ? "" : second) : first;
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name());
        } catch (Exception error) {
            return value;
        }
    }

    private static String get(String url, int maxBytes) throws IOException {
        return new String(getBytes(url, maxBytes), StandardCharsets.UTF_8);
    }

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
