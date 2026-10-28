package cn.lineai.feature.extensions;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;

/** Downloads a GitHub repository archive for skill installation. */
final class GitHubSkillDownloader {

    private static final int MAX_BYTES = 20 * 1024 * 1024;

    private GitHubSkillDownloader() {
    }

    static byte[] download(String rawUrl) throws IOException {
        String input = rawUrl == null ? "" : rawUrl.trim();
        if (!input.startsWith("https://")) throw new IOException("GitHub URL must use https://");
        String primary = archiveUrl(input, "main");
        try {
            return fetch(primary);
        } catch (IOException error) {
            String fallback = archiveUrl(input, "master");
            if (fallback.equals(primary)) throw error;
            return fetch(fallback);
        }
    }

    static String archiveUrl(String rawUrl, String defaultBranch) throws IOException {
        String value = rawUrl == null ? "" : rawUrl.trim();
        while (value.endsWith("/")) value = value.substring(0, value.length() - 1);
        if (value.endsWith(".zip")) {
            if (!value.startsWith("https://github.com/")
                    && !value.startsWith("https://codeload.github.com/")) {
                throw new IOException("Only github.com archives are allowed");
            }
            return value;
        }
        String prefix = "https://github.com/";
        if (!value.startsWith(prefix)) throw new IOException("Not a GitHub URL");
        String rest = value.substring(prefix.length());
        String[] parts = rest.split("/");
        if (parts.length < 2) throw new IOException("GitHub URL must include owner and repository");
        String owner = parts[0];
        String repo = parts[1].replace(".git", "");
        String branch = defaultBranch;
        if (parts.length >= 4 && "tree".equals(parts[2])) {
            StringBuilder name = new StringBuilder(parts[3]);
            for (int i = 4; i < parts.length; i++) name.append('/').append(parts[i]);
            branch = name.toString();
        }
        return "https://github.com/" + owner + "/" + repo + "/archive/refs/heads/"
                + branch + ".zip";
    }

    private static byte[] fetch(String url) throws IOException {
        HttpURLConnection connection = null;
        try {
            connection = (HttpURLConnection) new URL(url).openConnection();
            connection.setConnectTimeout(15_000);
            connection.setReadTimeout(60_000);
            connection.setInstanceFollowRedirects(true);
            int code = connection.getResponseCode();
            if (code < 200 || code >= 300) throw new IOException("GitHub HTTP " + code);
            try (InputStream input = connection.getInputStream()) {
                ByteArrayOutputStream out = new ByteArrayOutputStream();
                byte[] buffer = new byte[8192];
                int count;
                while ((count = input.read(buffer)) >= 0) {
                    out.write(buffer, 0, count);
                    if (out.size() > MAX_BYTES) throw new IOException("GitHub archive too large");
                }
                return out.toByteArray();
            }
        } finally {
            if (connection != null) connection.disconnect();
        }
    }
}
