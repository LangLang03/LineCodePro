package cn.lineai.data.service;

import android.os.Build;
import android.webkit.CookieManager;
import cn.lineai.model.SkillHubModels;
import cn.lineai.model.SkillRecord;
import cn.lineai.security.SimpleHttpClient;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import org.json.JSONObject;

public final class SkillHubSessionClient {
    private static final String API_ROOT = "https://api.skillhub.cn";
    private static final String SITE_ROOT = "https://skillhub.cn";
    private static final int CONNECT_TIMEOUT_MS = 15000;
    private static final int READ_TIMEOUT_MS = 30000;
    private static final int MAX_RESPONSE_CHARS = 256 * 1024;
    private static final int MAX_COOKIE_CHARS = 16 * 1024;
    private static final int MAX_PUBLISH_FILES = 200;
    private static final int MAX_PUBLISH_FILE_BYTES = 2 * 1024 * 1024;
    private static final int MAX_PUBLISH_TOTAL_BYTES = 10 * 1024 * 1024;

    public void publish(
            SkillRecord skill, String rawSlug, String rawDisplayName, String rawVersion) throws Exception {
        if (skill == null || SkillRecord.LOCATION_SSH.equals(skill.getLocation())) {
            throw new IllegalArgumentException("请选择 App 或当前项目中的本地 Skill");
        }
        String slug = SkillHubClient.requireSlug(rawSlug);
        String displayName = requireText(rawDisplayName, "Skill 名称", 100);
        String version = requireVersion(rawVersion);
        List<PublishFile> files = collectPublishFiles(skill);

        JSONObject payload = new JSONObject();
        payload.put("slug", slug);
        payload.put("displayName", displayName);
        payload.put("version", version);
        payload.put("summaryZh", skill.getDescription());
        payload.put("iconUrl", "");

        String boundary = "LineCode-" + UUID.randomUUID().toString();
        byte[] body = multipart(boundary, payload.toString(), files);
        SimpleHttpClient.Request request = new SimpleHttpClient.Request(
                API_ROOT + "/api/v1/community/skills/publish", "POST", null);
        request.bodyBytes = body;
        request.connectTimeoutMs = CONNECT_TIMEOUT_MS;
        request.readTimeoutMs = 60000;
        request.headers.put("Accept", "application/json");
        request.headers.put("Content-Type", "multipart/form-data; boundary=" + boundary);
        String cookie = sessionCookie();
        if (cookie.length() > 0) {
            request.headers.put("Cookie", cookie);
        }
        SimpleHttpClient.Response response = SimpleHttpClient.execute(request);
        requireAuthenticatedSuccess(response, publishError(response));
    }

    public Session currentSession() throws Exception {
        SimpleHttpClient.Response response = request("GET", "/api/v1/auth/me");
        if (response.code == 401) {
            return Session.signedOut();
        }
        requireSuccess(response, "获取 SkillHub 账号失败");
        return Session.signedIn(parseAccount(new JSONObject(response.body)));
    }

    public SkillHubModels.Comment postComment(
            String rawSlug, String namespace, String rawContent) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        String content = requireCommentContent(rawContent);
        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("imageUrls", new org.json.JSONArray());
        SimpleHttpClient.Response response = jsonRequest(
                "POST",
                "/api/v1/skills/" + encode(slug) + "/comments" + namespaceQuery(namespace),
                body.toString());
        requireAuthenticatedSuccess(response, "发表评论失败");
        return SkillHubClient.parseComment(new JSONObject(response.body));
    }

    public SkillHubModels.Comment postCommentReply(
            String rawSlug, long commentId, String namespace, String rawContent) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        requireCommentId(commentId);
        String content = requireCommentContent(rawContent);
        JSONObject body = new JSONObject();
        body.put("content", content);
        body.put("imageUrls", new org.json.JSONArray());
        SimpleHttpClient.Response response = jsonRequest(
                "POST",
                "/api/v1/skills/" + encode(slug) + "/comments/" + commentId
                        + "/replies" + namespaceQuery(namespace),
                body.toString());
        requireAuthenticatedSuccess(response, "回复评论失败");
        return SkillHubClient.parseComment(new JSONObject(response.body));
    }

    public void setCommentLiked(
            String rawSlug, long commentId, String namespace, boolean liked) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        requireCommentId(commentId);
        SimpleHttpClient.Response response = request(
                liked ? "POST" : "DELETE",
                "/api/v1/skills/" + encode(slug) + "/comments/" + commentId
                        + "/like" + namespaceQuery(namespace));
        requireAuthenticatedSuccess(response, liked ? "点赞评论失败" : "取消点赞失败");
    }

    public void deleteComment(String rawSlug, long commentId, String namespace) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        requireCommentId(commentId);
        SimpleHttpClient.Response response = request(
                "DELETE", "/api/v1/skills/" + encode(slug) + "/comments/" + commentId
                        + namespaceQuery(namespace));
        requireAuthenticatedSuccess(response, "删除评论失败");
    }

    public boolean starred(String rawSlug, String namespace) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        SimpleHttpClient.Response response = request(
                "GET", "/api/v1/skills/" + encode(slug) + "/starred" + namespaceQuery(namespace));
        requireAuthenticatedSuccess(response, "获取收藏状态失败");
        return new JSONObject(response.body).optBoolean("starred");
    }

    public void setStarred(String rawSlug, String namespace, boolean starred) throws Exception {
        String slug = SkillHubClient.requireSlug(rawSlug);
        SimpleHttpClient.Response response = request(
                starred ? "POST" : "DELETE",
                "/api/v1/skills/" + encode(slug) + "/star" + namespaceQuery(namespace));
        requireAuthenticatedSuccess(response, starred ? "收藏失败" : "取消收藏失败");
    }

    private SimpleHttpClient.Response jsonRequest(String method, String path, String body) throws Exception {
        if (body == null || body.length() > 16 * 1024) {
            throw new IllegalArgumentException("SkillHub 请求内容过大");
        }
        return execute(method, path, body, "application/json");
    }

    public void logout() throws Exception {
        SimpleHttpClient.Response response = request("POST", "/api/v1/auth/logout");
        if (response.code != 401) {
            requireSuccess(response, "退出 SkillHub 账号失败");
        }
        clearSkillHubCookies();
    }

    private SimpleHttpClient.Response request(String method, String path) throws Exception {
        return execute(method, path, null, null);
    }

    private SimpleHttpClient.Response execute(
            String method, String path, String body, String contentType) throws Exception {
        SimpleHttpClient.Request request = new SimpleHttpClient.Request(API_ROOT + path, method, body);
        request.connectTimeoutMs = CONNECT_TIMEOUT_MS;
        request.readTimeoutMs = READ_TIMEOUT_MS;
        request.headers.put("Accept", "application/json");
        if (contentType != null) {
            request.headers.put("Content-Type", contentType);
        }
        String cookie = sessionCookie();
        if (cookie.length() > 0) {
            request.headers.put("Cookie", cookie);
        }
        SimpleHttpClient.Response response = SimpleHttpClient.execute(request);
        if (response.body.length() > MAX_RESPONSE_CHARS) {
            throw new IllegalArgumentException("SkillHub 账号响应过大");
        }
        return response;
    }

    private String sessionCookie() {
        String cookie = CookieManager.getInstance().getCookie(API_ROOT);
        return requireSafeCookie(cookie);
    }

    static String requireSafeCookie(String rawCookie) {
        String cookie = rawCookie == null ? "" : rawCookie.trim();
        if (cookie.length() > MAX_COOKIE_CHARS
                || cookie.indexOf('\r') >= 0
                || cookie.indexOf('\n') >= 0) {
            throw new IllegalArgumentException("无效的 SkillHub 会话");
        }
        return cookie;
    }

    static Account parseAccount(JSONObject root) {
        JSONObject user = root.optJSONObject("user");
        if (user == null) {
            user = root;
        }
        String handle = first(
                user.optString("handle"),
                user.optString("username"),
                user.optString("userName"));
        String displayName = first(
                user.optString("displayName"),
                user.optString("nickname"),
                user.optString("name"),
                handle);
        if (displayName.length() == 0 && handle.length() == 0) {
            throw new IllegalArgumentException("SkillHub 账号信息不完整");
        }
        return new Account(
                displayName,
                handle,
                first(user.optString("avatarUrl"), user.optString("avatar"), user.optString("image")));
    }

    private void clearSkillHubCookies() {
        CookieManager manager = CookieManager.getInstance();
        LinkedHashMap<String, Boolean> names = new LinkedHashMap<>();
        collectCookieNames(names, manager.getCookie(API_ROOT));
        collectCookieNames(names, manager.getCookie(SITE_ROOT));
        for (String name : names.keySet()) {
            String expired = name + "=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/; Secure";
            manager.setCookie(API_ROOT, expired);
            manager.setCookie(SITE_ROOT, expired + "; Domain=skillhub.cn");
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            manager.flush();
        }
    }

    private static void collectCookieNames(Map<String, Boolean> names, String rawCookie) {
        String cookie = requireSafeCookie(rawCookie);
        if (cookie.length() == 0) {
            return;
        }
        for (String part : cookie.split(";")) {
            int equals = part.indexOf('=');
            String name = equals < 0 ? part.trim() : part.substring(0, equals).trim();
            if (name.matches("[A-Za-z0-9_.-]{1,128}")) {
                names.put(name, Boolean.TRUE);
            }
        }
    }

    private static void requireAuthenticatedSuccess(
            SimpleHttpClient.Response response, String message) throws Exception {
        if (response.code == 401) {
            throw new IllegalStateException("请先登录 SkillHub 账号");
        }
        requireSuccess(response, message);
    }

    private static String requireCommentContent(String rawContent) {
        String content = rawContent == null ? "" : rawContent.trim();
        if (content.length() == 0 || content.codePointCount(0, content.length()) > 500) {
            throw new IllegalArgumentException("评论内容应为 1–500 字");
        }
        return content;
    }

    private static void requireCommentId(long commentId) {
        if (commentId <= 0) {
            throw new IllegalArgumentException("无效的评论 ID");
        }
    }

    private static String publishError(SimpleHttpClient.Response response) {
        String fallback = "发布 Skill 失败";
        if (response == null || response.body.length() == 0) {
            return fallback;
        }
        try {
            JSONObject value = new JSONObject(response.body);
            String message = first(value.optString("message"), value.optString("error"));
            return message.length() == 0 ? fallback : message;
        } catch (Exception ignored) {
            return fallback;
        }
    }

    private static String requireText(String rawValue, String label, int maxLength) {
        String value = rawValue == null ? "" : rawValue.trim();
        if (value.length() == 0 || value.codePointCount(0, value.length()) > maxLength) {
            throw new IllegalArgumentException(label + "不能为空且不能超过 " + maxLength + " 字");
        }
        return value;
    }

    private static String requireVersion(String rawVersion) {
        String version = rawVersion == null ? "" : rawVersion.trim();
        if (!version.matches("[A-Za-z0-9][A-Za-z0-9._+-]{0,63}")) {
            throw new IllegalArgumentException("无效的 Skill 版本");
        }
        return version;
    }

    static List<PublishFile> collectPublishFiles(SkillRecord skill) throws Exception {
        File root = new File(skill.getRootPath()).getCanonicalFile();
        if (!root.isDirectory()) {
            throw new IllegalArgumentException("本地 Skill 目录不存在");
        }
        ArrayList<PublishFile> files = new ArrayList<>();
        collectPublishFiles(root, root, files, new long[] {0});
        files.sort(Comparator.comparing(file -> file.path));
        boolean hasSkillMarkdown = false;
        for (PublishFile file : files) {
            if ("SKILL.md".equals(file.path)) {
                hasSkillMarkdown = true;
                break;
            }
        }
        if (!hasSkillMarkdown) {
            throw new IllegalArgumentException("本地 Skill 缺少 SKILL.md");
        }
        return files;
    }

    private static void collectPublishFiles(
            File root, File current, List<PublishFile> files, long[] total) throws Exception {
        File[] children = current.listFiles();
        if (children == null) {
            throw new IllegalArgumentException("无法读取本地 Skill 目录");
        }
        for (File child : children) {
            File canonical = child.getCanonicalFile();
            String rootPath = root.getPath();
            if (!canonical.getPath().startsWith(rootPath + File.separator)) {
                throw new IllegalArgumentException("Skill 文件路径越界");
            }
            String relative = canonical.getPath().substring(rootPath.length() + 1)
                    .replace(File.separatorChar, '/');
            if (canonical.isDirectory()) {
                collectPublishFiles(root, canonical, files, total);
                continue;
            }
            if (!canonical.isFile() || isSensitive(relative)) {
                if (isSensitive(relative)) {
                    throw new IllegalArgumentException("Skill 包含敏感文件：" + relative);
                }
                continue;
            }
            long length = canonical.length();
            if (length > MAX_PUBLISH_FILE_BYTES) {
                throw new IllegalArgumentException("Skill 文件过大：" + relative);
            }
            total[0] += length;
            if (total[0] > MAX_PUBLISH_TOTAL_BYTES) {
                throw new IllegalArgumentException("Skill 文件总大小超过 10 MB");
            }
            if (files.size() >= MAX_PUBLISH_FILES) {
                throw new IllegalArgumentException("Skill 文件数量超过 200 个");
            }
            files.add(new PublishFile(relative, readFile(canonical, (int) length)));
        }
    }

    private static boolean isSensitive(String path) {
        String name = path.toLowerCase(Locale.ROOT);
        String leaf = name.substring(name.lastIndexOf('/') + 1);
        return ".env".equals(leaf) || leaf.startsWith(".env.")
                || leaf.contains("credential") || leaf.contains("secret")
                || leaf.endsWith(".pem") || leaf.endsWith(".key")
                || leaf.endsWith(".p12") || leaf.endsWith(".pfx")
                || leaf.endsWith(".jks") || leaf.endsWith(".keystore")
                || "id_rsa".equals(leaf) || "id_ed25519".equals(leaf);
    }

    private static byte[] readFile(File file, int expectedLength) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream(expectedLength);
        FileInputStream input = new FileInputStream(file);
        try {
            byte[] buffer = new byte[8192];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
        } finally {
            input.close();
        }
        return output.toByteArray();
    }

    private static byte[] multipart(
            String boundary, String payload, List<PublishFile> files) throws Exception {
        ByteArrayOutputStream output = new ByteArrayOutputStream();
        writePart(output, boundary, "payload", null, "application/json",
                payload.getBytes(StandardCharsets.UTF_8));
        for (PublishFile file : files) {
            writePart(output, boundary, "files", file.path,
                    "application/octet-stream", file.bytes);
        }
        output.write(("--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));
        if (output.size() > MAX_PUBLISH_TOTAL_BYTES + 512 * 1024) {
            throw new IllegalArgumentException("Skill 发布请求过大");
        }
        return output.toByteArray();
    }

    private static void writePart(
            ByteArrayOutputStream output, String boundary, String name,
            String filename, String contentType, byte[] bytes) throws Exception {
        output.write(("--" + boundary + "\r\n").getBytes(StandardCharsets.UTF_8));
        String disposition = "Content-Disposition: form-data; name=\"" + name + "\"";
        if (filename != null) {
            disposition += "; filename=\"" + filename.replace("\"", "") + "\"";
        }
        output.write((disposition + "\r\nContent-Type: " + contentType + "\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        output.write(bytes);
        output.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    static final class PublishFile {
        final String path;
        final byte[] bytes;

        PublishFile(String path, byte[] bytes) {
            this.path = path;
            this.bytes = bytes;
        }
    }

    private static String namespaceQuery(String namespace) {
        String value = namespace == null ? "" : namespace.trim();
        return value.length() == 0 ? "" : "?namespace=" + encode(value);
    }

    private static String encode(String value) {
        try {
            return URLEncoder.encode(value, StandardCharsets.UTF_8.name()).replace("+", "%20");
        } catch (Exception e) {
            throw new IllegalArgumentException("无法编码 SkillHub 参数", e);
        }
    }

    private static void requireSuccess(SimpleHttpClient.Response response, String message) throws Exception {
        if (response.code < 200 || response.code >= 300) {
            throw new Exception(message + "（HTTP " + response.code + "）");
        }
    }

    private static String first(String... values) {
        for (String value : values) {
            if (value != null && value.trim().length() > 0) {
                return value.trim();
            }
        }
        return "";
    }

    public static final class Account {
        private final String displayName;
        private final String handle;
        private final String avatarUrl;

        Account(String displayName, String handle, String avatarUrl) {
            this.displayName = displayName == null ? "" : displayName;
            this.handle = handle == null ? "" : handle;
            this.avatarUrl = avatarUrl == null ? "" : avatarUrl;
        }

        public String getDisplayName() { return displayName; }
        public String getHandle() { return handle; }
        public String getAvatarUrl() { return avatarUrl; }
    }

    public static final class Session {
        private final boolean authenticated;
        private final Account account;

        private Session(boolean authenticated, Account account) {
            this.authenticated = authenticated;
            this.account = account;
        }

        public static Session signedIn(Account account) { return new Session(true, account); }
        public static Session signedOut() { return new Session(false, null); }
        public boolean isAuthenticated() { return authenticated; }
        public Account getAccount() { return account; }
    }
}
