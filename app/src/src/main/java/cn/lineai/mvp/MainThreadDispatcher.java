package cn.lineai.mvp;

import android.os.Handler;
import android.os.Looper;

public final class MainThreadDispatcher {
    private final Handler handler;

    public MainThreadDispatcher() {
        this(new Handler(Looper.getMainLooper()));
    }

    MainThreadDispatcher(Handler handler) {
        this.handler = handler;
    }

    public void post(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        handler.post(runnable);
    }

    public void postDelayed(Runnable runnable, long delayMillis) {
        if (runnable == null) {
            return;
        }
        handler.postDelayed(runnable, Math.max(0L, delayMillis));
    }

    public void dispatch(Runnable runnable) {
        if (runnable == null) {
            return;
        }
        if (isMainThread()) {
            runnable.run();
        } else {
            post(runnable);
        }
    }

    public boolean isMainThread() {
        return Looper.myLooper() == Looper.getMainLooper();
    }
}
