package cn.lineai.engine.local;

/**
 * Build-time local model capabilities.
 *
 * <p>Feature flags are baked in as {@code BuildConfig} fields, so a build either contains the
 * native backend or reports it as absent; queries never crash, they degrade to descriptive
 * strings.</p>
 */
public final class LocalModelSupport {

    /** Not instantiable; the support checks are static queries. */
    private LocalModelSupport() {
    }

    /**
     * Reports whether the native backend is compiled into this build.
     *
     * @return true when the local model feature flag was enabled at build time
     */
    public static boolean isEnabled() {
        return BuildConfig.LOCAL_MODEL_ENABLED;
    }

    /**
     * Reports whether the backend was built with GPU acceleration enabled.
     *
     * @return true when the backend is present and its GPU flag was enabled at build time
     */
    public static boolean isGpuAvailable() {
        return isEnabled() && BuildConfig.LOCAL_MODEL_GPU;
    }

    /**
     * Names the local inference engine.
     *
     * @return the engine label shown in the UI
     */
    public static String engineName() {
        return "llama.cpp";
    }

    /**
     * Identifies the pinned llama.cpp revision the native code was built from.
     *
     * @return the upstream revision identifier
     */
    public static String revision() {
        return "b11201";
    }

    /**
     * Collects the native runtime description for the diagnostics screen, degrading to a
     * descriptive string instead of throwing when the backend is absent or fails to load.
     *
     * @return runtime information, or an explanatory message when unavailable
     */
    public static String runtimeInfo() {
        if (!isEnabled()) return "local model backend is not compiled into this build";
        try {
            return NativeLlamaEngine.nativeRuntimeInfo();
        } catch (Throwable error) {
            return "local model backend unavailable: " + error.getMessage();
        }
    }
}
