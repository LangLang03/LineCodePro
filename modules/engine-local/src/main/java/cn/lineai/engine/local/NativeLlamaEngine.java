package cn.lineai.engine.local;

/** JNI entry points for the bundled llama.cpp backend. */
public final class NativeLlamaEngine {
    public interface Callback {
        void onPiece(String piece);

        void onLoadProgress(int percent);

        boolean isCancelled();
    }

    static {
        System.loadLibrary("linecode_local_model");
    }

    private NativeLlamaEngine() {
    }

    public static native String nativeRuntimeInfo();

    public static native int[] nativeGenerate(
            String modelPath,
            String[] roles,
            String[] contents,
            int maxTokens,
            int contextSize,
            int gpuLayers,
            Callback callback);
}
