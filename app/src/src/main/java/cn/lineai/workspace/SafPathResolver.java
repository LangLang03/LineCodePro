package cn.lineai.workspace;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

public final class SafPathResolver {
    public String treeUriToFileSystemPath(String uri) {
        if (uri == null || !uri.startsWith("content://")) {
            return "";
        }
        String documentId = extractDocumentId(uri);
        if (documentId.length() == 0) {
            return "";
        }
        String decoded = decode(documentId);
        while (decoded.startsWith("/")) {
            decoded = decoded.substring(1);
        }
        String authority = extractAuthority(uri);
        if ("com.android.providers.downloads.documents".equals(authority)) {
            return mapDownloadsDocumentId(decoded);
        }
        return mapExternalStorageDocumentId(decoded);
    }

    private String extractAuthority(String uri) {
        int start = "content://".length();
        int end = uri.indexOf('/', start);
        return end < 0 ? uri.substring(start) : uri.substring(start, end);
    }

    private String extractDocumentId(String uri) {
        String clean = uri.split("[?#]", 2)[0];
        String documentMarker = "/document/";
        int documentIndex = clean.indexOf(documentMarker);
        if (documentIndex >= 0) {
            return clean.substring(documentIndex + documentMarker.length());
        }
        String treeMarker = "/tree/";
        int treeIndex = clean.indexOf(treeMarker);
        if (treeIndex >= 0) {
            return clean.substring(treeIndex + treeMarker.length());
        }
        return "";
    }

    private String mapExternalStorageDocumentId(String documentId) {
        if (documentId.startsWith("raw:")) {
            String rawPath = documentId.substring("raw:".length());
            return rawPath.startsWith("/") ? rawPath : "";
        }
        int separator = documentId.indexOf(':');
        if (separator < 0) {
            return "";
        }
        String volume = documentId.substring(0, separator);
        String rest = documentId.substring(separator + 1);
        if ("primary".equals(volume)) {
            return joinAbsolutePath("/storage/emulated/0", rest);
        }
        if ("home".equals(volume)) {
            return joinAbsolutePath("/storage/emulated/0/Documents", rest);
        }
        if (volume.matches("(?i)[0-9a-f]{4}-[0-9a-f]{4}")) {
            return joinAbsolutePath("/storage/" + volume, rest);
        }
        return "";
    }

    private String mapDownloadsDocumentId(String documentId) {
        if (documentId.startsWith("raw:")) {
            String rawPath = documentId.substring("raw:".length());
            return rawPath.startsWith("/") ? rawPath : "";
        }
        if ("downloads".equals(documentId) || "download".equals(documentId)) {
            return "/storage/emulated/0/Download";
        }
        if (documentId.startsWith("downloads/") || documentId.startsWith("download/")) {
            int slash = documentId.indexOf('/');
            return joinAbsolutePath("/storage/emulated/0/Download", documentId.substring(slash + 1));
        }
        int separator = documentId.indexOf(':');
        if (separator >= 0) {
            String volume = documentId.substring(0, separator);
            String rest = documentId.substring(separator + 1);
            if ("downloads".equals(volume) || "download".equals(volume)) {
                return joinAbsolutePath("/storage/emulated/0/Download", rest);
            }
            return mapExternalStorageDocumentId(documentId);
        }
        return "";
    }

    private String joinAbsolutePath(String root, String rest) {
        String cleanRest = rest == null ? "" : rest;
        while (cleanRest.startsWith("/")) {
            cleanRest = cleanRest.substring(1);
        }
        return cleanRest.length() == 0 ? root : root + "/" + cleanRest;
    }

    private String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException e) {
            return value;
        }
    }
}
