package cn.lineai.ai;

import java.util.ArrayList;
import java.util.List;

public final class ModelCancellationToken {
    private final List<Runnable> listeners = new ArrayList<>();
    private volatile boolean cancelled;

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        List<Runnable> callbacks;
        synchronized (listeners) {
            if (cancelled) {
                return;
            }
            cancelled = true;
            callbacks = new ArrayList<>(listeners);
            listeners.clear();
        }
        for (Runnable callback : callbacks) {
            callback.run();
        }
    }

    public void onCancel(Runnable callback) {
        boolean runNow;
        synchronized (listeners) {
            runNow = cancelled;
            if (!runNow) {
                listeners.add(callback);
            }
        }
        if (runNow) {
            callback.run();
        }
    }
}
