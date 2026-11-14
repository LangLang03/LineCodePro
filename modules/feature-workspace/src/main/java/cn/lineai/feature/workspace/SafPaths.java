package cn.lineai.feature.workspace;

import java.io.UnsupportedEncodingException;
import java.net.URLDecoder;

/**
 * Converts a SAF tree/document URI into a filesystem path when the volume is addressable.
 *
 * <p>The mapping covers the authorities the app actually grants: the downloads provider and
 * external-storage documents with {@code raw:}, {@code primary:}, {@code home:} and SD-card volume
 * prefixes. Anything unaddressable maps to an empty string rather than throwing, so callers can
 * treat empty as "not a real file path" and keep the SAF URI as the source of truth.</p>
 */
public final class SafPaths {

    /** Not instantiable; the mapping is static. */
    private SafPaths() {
    }

    /**
     * Maps a SAF content URI to a filesystem path.
     *
     * @param uri SAF tree or document URI string; may be null
     * @return absolute filesystem path, or an empty string when the URI is not content-based, has no
     *     document id, or points at a volume this mapper cannot address
     */
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

    /** Extracts the authority segment between {@code content://} and the first slash. */
    private static String authority(String uri) {
        int start = "content://".length();
        int end = uri.indexOf('/', start);
        return end < 0 ? uri.substring(start) : uri.substring(start, end);
    }

    /** Pulls the document or tree id out of the URI, ignoring query and fragment parts. */
    private static String extractDocumentId(String uri) {
        String clean = uri.split("[?#]", 2)[0];
        int document = clean.indexOf("/document/");
        if (document >= 0) return clean.substring(document + "/document/".length());
        int tree = clean.indexOf("/tree/");
        if (tree >= 0) return clean.substring(tree + "/tree/".length());
        return "";
    }

    /**
     * Maps an external-storage document id to a path under the primary volume, the home Documents
     * directory or an SD-card mount point.
     */
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

    /** Maps a downloads-provider document id, falling back to the external mapping for foreign
     * volume prefixes so shared document ids still resolve. */
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

    /** Joins a normalized rest segment onto a root, returning the root alone when the rest is empty. */
    private static String join(String root, String rest) {
        String clean = rest == null ? "" : rest;
        while (clean.startsWith("/")) clean = clean.substring(1);
        return clean.isEmpty() ? root : root + "/" + clean;
    }

    /** URL-decodes a document id, returning the input unchanged on malformed escapes. */
    private static String decode(String value) {
        try {
            return URLDecoder.decode(value, "UTF-8");
        } catch (UnsupportedEncodingException | IllegalArgumentException error) {
            return value;
        }
    }
}
