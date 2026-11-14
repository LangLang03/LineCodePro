package cn.lineai.engine.local;

/**
 * JNI entry points for the bundled llama.cpp backend.
 *
 * <p>This module is Android and native by design: the methods below are implemented in C++ under
 * {@code src/main/cpp} and the shared library is loaded once at class initialization. The class
 * fails fast with {@link UnsatisfiedLinkError} when the backend was not compiled into the build,
 * which callers must treat as "local model unavailable".</p>
 */
public final class NativeLlamaEngine {

    /**
     * Progress and cancellation hooks invoked from the native generation loop. Implementations
     * must be cheap and must not re-enter the engine.
     */
    public interface Callback {

        /**
         * Receives one generated text piece as it is produced.
         *
         * @param piece incrementally decoded text fragment
         */
        void onPiece(String piece);

        /**
         * Reports model loading progress.
         *
         * @param percent progress from 0 to 100
         */
        void onLoadProgress(int percent);

        /**
         * Polled by the native loop between tokens.
         *
         * @return true to request cancellation of the running generation
         */
        boolean isCancelled();
    }

    static {
        System.loadLibrary("linecode_local_model");
    }

    /** Not instantiable; the engine exposes static native entry points only. */
    private NativeLlamaEngine() {
    }

    /**
     * Queries the native backend for its build configuration.
     *
     * @return human-readable runtime description (backend version, acceleration in use)
     */
    public static native String nativeRuntimeInfo();

    /**
     * Runs one synchronous generation over a transcript. The call blocks until the transcript is
     * consumed, the token budget is reached, or the callback requests cancellation.
     *
     * @param modelPath file path of the local GGUF model
     * @param roles parallel array of role names, one per transcript entry
     * @param contents parallel array of message contents, one per transcript entry
     * @param maxTokens upper bound on generated tokens for this call
     * @param contextSize context window the model was configured with
     * @param gpuLayers number of layers to offload to the GPU; zero disables offload
     * @param callback streaming and cancellation hooks; must not be null
     * @return generated token identifiers, empty when cancelled or when nothing was produced
     */
    public static native int[] nativeGenerate(
            String modelPath,
            String[] roles,
            String[] contents,
            int maxTokens,
            int contextSize,
            int gpuLayers,
            Callback callback);
}
