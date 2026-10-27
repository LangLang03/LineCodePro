package cn.lineai.feature.workspace;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/** Converts a SAF tree/document URI into a filesystem path when the volume is addressable. */
public final class SafPaths {

    private SafPaths() {
    }

    public static String toFileSystemPath(String uri) {
        if (uri == null || !uri.startsWith("content://")) return "";
        String documentId = extractDocumentId(uri);
        if (documentId.isEmpty()) return "";
        String decoded = decode(documentId);
        while (decoded.startsWith("/")) decoded = decoded.substring(1);
        String authority = authority(uri);
        if ("com.android.providers.downloads.documents".equals(authority)) {
            return mapDownloads(decoded);
        }
        return mapExternal(decoded);
    }

    private static String authority(String uri) {
        int start = "content://".length();
        int end = uri.indexOf('/', start);
        return end < 0 ? uri.substring(start) : uri.substring(start, end);
    }

    private static String extractDocumentId(String uri) {
        String clean = uri.split("[?#]", 2)[0];
        int document = clean.indexOf("/document/");
        if (document >= 0) return clean.substring(document + "/document/".length());
        int tree = clean.indexOf("/tree/");
        if (tree >= 0) return clean.substring(tree + "/tree/".length());
        return "";
    }

    private static String mapExternal(String documentId) {
        if (documentId.startsWith("raw:")) {
            String raw = documentId.substring("raw:".length());
            return raw.startsWith("/") ? raw : "";
        }
        int separator = documentId.indexOf(':');
        if (separator < 0) return "";
        String volume = documentId.substring(0, separator);
        String rest = documentId.substring(separator + 1);
        if ("primary".equals(volume)) return join("/storage/emulated/0", rest);
        if ("home".equals(volume)) return join("/storage/emulated/0/Documents", rest);
        if (volume.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}")) return join("/storage/" + volume, rest);
        return "";
    }

    private static String mapDownloads(String documentId) {
        if (documentId.startsWith("raw:")) {
            String raw = documentId.substring("raw:".length());
            return raw.startsWith("/") ? raw : "";
        }
        if ("downloads".equals(documentId) || "download".equals(documentId)) {
            return "/storage/emulated/0/Download";
        }
        if (documentId.startsWith("downloads/") || documentId.startsWith("download/")) {
            int slash = documentId.indexOf('/');
            return join("/storage/emulated/0/Download", documentId.substring(slash + 1));
        }
        int separator = documentId.indexOf(':');
        if (separator >= 0) {
            String volume = documentId.substring(0, separator);
            String rest = documentId.substring(separator + 1);
            if ("downloads".equals(volume) || "download".equals(volume)) {
                return join("/storage/emulated/0/Download", rest);
            }
            return mapExternal(documentId);
        }
        return "";
    }

    private static String join(String root, String rest) {
        String clean = rest == null ? "" : rest;
        while (clean.startsWith("/")) clean = clean.substring(1);
        return clean.isEmpty() ? root : root + "/" + clean;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException error) {
            return value;
        }
    }
}
