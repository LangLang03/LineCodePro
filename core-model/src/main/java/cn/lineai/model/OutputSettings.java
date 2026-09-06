package cn.lineai.model;

public final class OutputSettings {
    public static final String BROWSER_BUILTIN = "builtin";
    public static final String BROWSER_EXTERNAL = "external";

    private final boolean codeWrapEnabled;
    private final String browserMode;
    private final boolean browserJavaScriptEnabled;
    private final boolean allowAnyHttp;
    private final boolean bypassPathProtection;
    private final boolean processAutoExpandEnabled;

    public OutputSettings(boolean codeWrapEnabled, String browserMode) {
        this(codeWrapEnabled, browserMode, false, false, false);
    }

    public OutputSettings(boolean codeWrapEnabled, String browserMode, boolean browserJavaScriptEnabled) {
        this(codeWrapEnabled, browserMode, browserJavaScriptEnabled, false, false);
    }

    public OutputSettings(boolean codeWrapEnabled, String browserMode, boolean browserJavaScriptEnabled, boolean allowAnyHttp) {
        this(codeWrapEnabled, browserMode, browserJavaScriptEnabled, allowAnyHttp, false);
    }

    public OutputSettings(boolean codeWrapEnabled, String browserMode, boolean browserJavaScriptEnabled, boolean allowAnyHttp, boolean bypassPathProtection) {
        this(codeWrapEnabled, browserMode, browserJavaScriptEnabled, allowAnyHttp, bypassPathProtection, false);
    }

    public OutputSettings(boolean codeWrapEnabled, String browserMode, boolean browserJavaScriptEnabled,
                          boolean allowAnyHttp, boolean bypassPathProtection, boolean processAutoExpandEnabled) {
        this.codeWrapEnabled = codeWrapEnabled;
        this.browserMode = normalizeBrowserMode(browserMode);
        this.browserJavaScriptEnabled = browserJavaScriptEnabled;
        this.allowAnyHttp = allowAnyHttp;
        this.bypassPathProtection = bypassPathProtection;
        this.processAutoExpandEnabled = processAutoExpandEnabled;
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

    public boolean isAllowAnyHttp() {
        return allowAnyHttp;
    }

    public boolean isBypassPathProtection() {
        return bypassPathProtection;
    }

    public boolean isProcessAutoExpandEnabled() {
        return processAutoExpandEnabled;
    }

    public static String normalizeBrowserMode(String mode) {
        return BROWSER_EXTERNAL.equals(mode) ? BROWSER_EXTERNAL : BROWSER_BUILTIN;
    }
}
