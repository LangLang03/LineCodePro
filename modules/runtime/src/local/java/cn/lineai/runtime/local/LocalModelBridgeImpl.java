package cn.lineai.runtime.local;

import android.app.Activity;
import android.view.View;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.ai.ModelClient;
import cn.lineai.engine.local.LocalModelSupport;
import cn.lineai.feature.models.LocalModelPage;
import cn.lineai.runtime.LocalModelBridge;
import cn.lineai.runtime.LocalModelClient;
import cn.lineai.ui.tokens.Tokens;

/** Local-build implementation of the optional llama.cpp model runtime. */
public final class LocalModelBridgeImpl implements LocalModelBridge {

    /**
     * Wraps the HTTP client with in-process llama.cpp inference when the local runtime is
     * enabled on this device; otherwise the fallback client is used unchanged.
     *
     * @param fallback the network client used for non-local models and when disabled
     * @param scheduler scheduler used to move inference off the calling thread
     * @return a client routing {@code LOCAL_GGUF} models to llama.cpp, or {@code fallback}
     */
    @Override public ModelClient wrap(ModelClient fallback, Scheduler scheduler) {
        return LocalModelSupport.isEnabled()
                ? new LocalModelClient(fallback, scheduler) : fallback;
    }

    /**
     * Builds the local model management page and adapts it to the bridge page contract.
     *
     * @param activity current activity, used as view context
     * @param tokens design tokens for theming
     * @param back navigation callback closing the page
     * @param existing model being edited, or {@code null} when creating
     * @param listener host callbacks for file picking and persistence
     * @return the page handle exposing the view and late model-path updates
     */
    @Override public Page createPage(Activity activity, Tokens tokens, Runnable back,
                                     ModelConfig existing, Listener listener) {
        final LocalModelPage page = new LocalModelPage(activity, tokens, back, existing,
                LocalModelSupport.isGpuAvailable(), new LocalModelPage.Listener() {
                    public void onPickFile() { listener.onPickFile(); }
                    public void onSave(ModelConfig model) { listener.onSave(model); }
                    public void onDelete(String id) { listener.onDelete(id); }
                });
        return new Page() {
            @Override public View view() { return page; }
            @Override public void setModelPath(String path) { page.setModelPath(path); }
        };
    }
}
