package cn.lineai.model;

public final class InputAttachment {
    public static final String SOURCE_LOCAL = "local";
    public static final String SOURCE_SSH = "ssh";

    private final String name;
    private final String path;
    private final String source;

    public InputAttachment(String name, String path, String source) {
        this.path = path == null ? "" : path;
        this.name = name == null || name.length() == 0 ? basename(this.path) : name;
        this.source = SOURCE_SSH.equals(source) ? SOURCE_SSH : SOURCE_LOCAL;
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getSource() {
        return source;
    }

    public boolean matches(String otherPath, String otherSource) {
        return path.equals(otherPath == null ? "" : otherPath)
                && source.equals(SOURCE_SSH.equals(otherSource) ? SOURCE_SSH : SOURCE_LOCAL);
    }

    private static String basename(String path) {
        String value = path == null ? "" : path.trim();
        while (value.length() > 1 && value.endsWith("/")) {
            value = value.substring(0, value.length() - 1);
        }
        int index = value.lastIndexOf('/');
        if (index >= 0 && index < value.length() - 1) {
            return value.substring(index + 1);
        }
        return value;
    }
}
