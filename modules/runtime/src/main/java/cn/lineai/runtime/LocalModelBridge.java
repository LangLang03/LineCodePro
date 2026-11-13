package cn.lineai.runtime;

import android.app.Activity;
import android.view.View;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.ui.tokens.Tokens;

/**
 * Optional local-model runtime. The slim build has no implementation; the local build registers
 * one through {@link java.util.ServiceLoader} so llama.cpp classes never reach the slim APK.
 */
public interface LocalModelBridge {

    /**
     * Wraps the HTTP client with local inference when the local runtime is enabled.
     *
     * @param fallback the network client used for non-local models and when disabled
     * @param scheduler scheduler used to move inference off the calling thread
     * @return a client that routes {@code LOCAL_GGUF} models to llama.cpp, or {@code fallback}
     */
    ModelClient wrap(ModelClient fallback, Scheduler scheduler);

    /**
     * Builds the local model management page (import, edit, delete of GGUF models).
     *
     * @param activity current activity, used as view context
     * @param tokens design tokens for theming
     * @param back navigation callback closing the page
     * @param existing model being edited, or {@code null} when creating
     * @param listener host callbacks for file picking and persistence
     * @return the page handle exposing the view and late model-path updates
     */
    Page createPage(Activity activity, Tokens tokens, Runnable back, ModelConfig existing,
                    Listener listener);

    /** Handle over the created local model page, kept for late updates. */
    interface Page {
        /** Returns the root view to push onto the screen host. */
        View view();

        /** Applies an imported model path after the page was created. */
        void setModelPath(String path);
    }

    /** Host callbacks of the local model page. */
    interface Listener {
        /** Requests a SAF file picker for a GGUF file. */
        void onPickFile();

        /** Persists the model configuration and returns to the model list. */
        void onSave(ModelConfig model);

        /** Deletes the model with the given id and returns to the model list. */
        void onDelete(String id);
    }
}
