package cn.lineai.security;

import java.net.URI;
import java.util.Locale;

public final class UrlPolicy {
    private UrlPolicy() {
    }

    public static String normalizeHttpOrHttpsUrl(String rawUrl) {
        String value = rawUrl == null ? "" : rawUrl.trim();
        if (value.length() == 0) {
            return "";
        }
        URI uri = parse(value);
        if (uri == null || uri.getHost() == null) {
            return "";
        }
        String scheme = lower(uri.getScheme());
        return "https".equals(scheme) || "http".equals(scheme) ? value : "";
    }

    public static String normalizeHttpOrLocalCleartextUrl(String rawUrl) {
        String value = normalizeHttpOrHttpsUrl(rawUrl);
        if (value.length() == 0) {
            return "";
        }
        URI uri = parse(value);
        if (uri == null) {
            return "";
        }
        if ("https".equals(lower(uri.getScheme()))) {
            return value;
        }
        return isAllowedCleartextHttpHost(uri.getHost()) ? value : "";
    }

    public static String requireHttpOrLocalCleartextUrl(String rawUrl, String label) {
        String normalized = normalizeHttpOrHttpsUrl(rawUrl);
        String name = label == null || label.trim().length() == 0 ? "URL" : label.trim();
        if (normalized.length() == 0) {
            throw new IllegalArgumentException(name + " 必须以 http:// 或 https:// 开头。");
        }
        URI uri = parse(normalized);
        if (uri != null && "http".equals(lower(uri.getScheme())) && !isAllowedCleartextHttpHost(uri.getHost())) {
            throw new IllegalArgumentException(name + " 使用 HTTP 明文时仅允许 localhost、127.0.0.1 或 10.0.2.2。");
        }
        return normalized;
    }

    public static boolean isAllowedCleartextHttpUrl(String rawUrl) {
        String value = normalizeHttpOrHttpsUrl(rawUrl);
        if (value.length() == 0) {
            return false;
        }
        URI uri = parse(value);
        return uri != null
                && "http".equals(lower(uri.getScheme()))
                && isAllowedCleartextHttpHost(uri.getHost());
    }

    private static boolean isAllowedCleartextHttpHost(String host) {
        String value = lower(host);
        return "localhost".equals(value)
                || "127.0.0.1".equals(value)
                || "10.0.2.2".equals(value);
    }

    private static URI parse(String value) {
        try {
            return new URI(value);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static String lower(String value) {
        return value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
    }
}
