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

    @Override public ModelClient wrap(ModelClient fallback, Scheduler scheduler) {
        return LocalModelSupport.isEnabled()
                ? new LocalModelClient(fallback, scheduler) : fallback;
    }

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
