package cn.lineai.feature.workspace;

/** Folder-name normalisation shared by managed workspace creation. */
public final class WorkspaceNames {

    private WorkspaceNames() {
    }

    /**
     * Turns a display name into a directory name without destroying non-Latin text.
     *
     * <p>Only characters that are illegal in a path segment are replaced; CJK names stay readable
     * instead of collapsing to a lone dash. Returns an empty string when nothing usable remains.</p>
     */
    public static String folder(String name) {
        String value = name == null ? "" : name.trim();
        value = value.replaceAll("[\\\\/:*?\"<>|\\p{Cntrl}]", "-")
                .replaceAll("\\s+", "-");
        while (value.endsWith(".")) {
            value = value.substring(0, value.length() - 1);
        }
        if (value.isEmpty() || ".".equals(value) || "..".equals(value)) {
            return "";
        }
        return value.length() > 60 ? value.substring(0, 60) : value;
    }
}
