package cn.lineai.core.model;

/** Shell snippets recommended in the Termux integration guide. */
public final class TermuxCommands {

    /** Shell snippet that enables the {@code allow-external-apps} Termux property. */
    public static final String ALLOW_EXTERNAL_APPS = "mkdir -p ~/.termux\n"
            + "properties_path=\"$HOME/.termux/termux.properties\"\n"
            + "touch \"$properties_path\"\n"
            + "grep -qxF 'allow-external-apps=true' \"$properties_path\" || printf '\\nallow-external-apps=true\\n' >> \"$properties_path\"\n"
            + "termux-reload-settings >/dev/null 2>&1 || true";

    /** Non-instantiable constant holder. */
    private TermuxCommands() {
    }
}
