package cn.lineai.engine.local;

/** Build-time local model capabilities. */
public final class LocalModelSupport {

    private LocalModelSupport() {
    }

    public static boolean isEnabled() {
        return BuildConfig.LOCAL_MODEL_ENABLED;
    }

    public static boolean isGpuAvailable() {
        return isEnabled() && BuildConfig.LOCAL_MODEL_GPU;
    }

    public static String engineName() {
        return "llama.cpp";
    }

    public static String revision() {
        return "b11201";
    }

    public static String runtimeInfo() {
        if (!isEnabled()) return "local model backend is not compiled into this build";
        try {
            return NativeLlamaEngine.nativeRuntimeInfo();
        } catch (Throwable error) {
            return "local model backend unavailable: " + error.getMessage();
        }
    }
}
