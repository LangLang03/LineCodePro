package cn.lineai.model;

public final class OutputSettings {
    public static final String BROWSER_BUILTIN = "builtin";
    public static final String BROWSER_EXTERNAL = "external";

    private final boolean codeWrapEnabled;
    private final String browserMode;
    private final boolean browserJavaScriptEnabled;

    public OutputSettings(boolean codeWrapEnabled, String browserMode) {
        this(codeWrapEnabled, browserMode, false);
    }

    public OutputSettings(boolean codeWrapEnabled, String browserMode, boolean browserJavaScriptEnabled) {
        this.codeWrapEnabled = codeWrapEnabled;
        this.browserMode = normalizeBrowserMode(browserMode);
        this.browserJavaScriptEnabled = browserJavaScriptEnabled;
    }

    public boolean isCodeWrapEnabled() {
        return codeWrapEnabled;
    }

    public String getBrowserMode() {
        return browserMode;
    }

    public boolean isBrowserJavaScriptEnabled() {
        return browserJavaScriptEnabled;
    }

    public static String normalizeBrowserMode(String mode) {
        return BROWSER_EXTERNAL.equals(mode) ? BROWSER_EXTERNAL : BROWSER_BUILTIN;
    }
}
