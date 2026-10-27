package cn.lineai.engine.skillhub;

import org.json.JSONObject;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

import cn.lineai.core.model.Skill;

/** SkillHub account and publish endpoints; the caller supplies the session cookie. */
public final class SkillHubSessionClient {

    private static final String API_ROOT = "https://api.skillhub.cn";

    public boolean signedIn(String cookie) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_ROOT + "/api/v1/auth/me")
                    .openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            applyCookie(connection, cookie);
            return connection.getResponseCode() == 200;
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void publish(Skill skill, String slug, String displayName, String version,
                        String cookie) throws IOException {
        String boundary = "LineCode-" + UUID.randomUUID();
        JSONObject payload = new JSONObject();
        try {
            payload.put("slug", slug);
            payload.put("displayName", displayName);
            payload.put("version", version);
            payload.put("summaryZh", skill.description);
            payload.put("iconUrl", "");
        } catch (Exception error) {
            throw new IOException("Cannot build publish payload", error);
        }
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"payload\"\r\n\r\n"
                + payload + "\r\n").getBytes(StandardCharsets.UTF_8));
        String markdown = skill.content.trim().isEmpty()
                ? "# " + skill.name + "\n\n" + skill.description : skill.content;
        body.write(("--" + boundary + "\r\n"
                + "Content-Disposition: form-data; name=\"files\"; filename=\"SKILL.md\"\r\n"
                + "Content-Type: text/markdown\r\n\r\n").getBytes(StandardCharsets.UTF_8));
        body.write(markdown.getBytes(StandardCharsets.UTF_8));
        body.write(("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8));

        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(
                    API_ROOT + "/api/v1/community/skills/publish").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type",
                    "multipart/form-data; boundary=" + boundary);
            applyCookie(connection, cookie);
            byte[] bytes = body.toByteArray();
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("SkillHub publish HTTP " + code);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    public void postComment(String slug, String content, String cookie) throws IOException {
        JSONObject body = new JSONObject();
        try {
            body.put("content", content);
            body.put("imageUrls", new org.json.JSONArray());
        } catch (Exception error) {
            throw new IOException("Cannot build comment payload", error);
        }
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(API_ROOT + "/api/v1/skills/"
                    + java.net.URLEncoder.encode(slug, "UTF-8") + "/comments").openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(30_000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Accept", "application/json");
            connection.setRequestProperty("Content-Type", "application/json");
            applyCookie(connection, cookie);
            byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
            connection.setFixedLengthStreamingMode(bytes.length);
            try (OutputStream output = connection.getOutputStream()) {
                output.write(bytes);
            }
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) {
                throw new IOException("SkillHub comment HTTP " + code);
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static void applyCookie(HttpURLConnection connection, String cookie) {
        if (cookie != null && !cookie.trim().isEmpty()
                && cookie.indexOf('\r') < 0 && cookie.indexOf('\n') < 0) {
            connection.setRequestProperty("Cookie", cookie.trim());
        }
    }
}
