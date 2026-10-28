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

    ModelClient wrap(ModelClient fallback, Scheduler scheduler);

    Page createPage(Activity activity, Tokens tokens, Runnable back, ModelConfig existing,
                    Listener listener);

    interface Page {
        View view();

        void setModelPath(String path);
    }

    interface Listener {
        void onPickFile();

        void onSave(ModelConfig model);

        void onDelete(String id);
    }
}
