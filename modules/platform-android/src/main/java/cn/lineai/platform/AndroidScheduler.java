package cn.lineai.platform;

import android.os.Handler;
import android.os.Looper;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;

/**
 * The platform scheduler: the only place in the rewritten code base allowed to create threads,
 * handlers or executors. Both pools are owned by this object and shut down by {@link #close()}.
 */
public final class AndroidScheduler implements Scheduler, AutoCloseable {

    private final Handler main = new Handler(Looper.getMainLooper());
    private final ExecutorService io = Executors.newCachedThreadPool(runnable -> {
        Thread thread = new Thread(runnable, "linecode-io");
        thread.setDaemon(true);
        return thread;
    });
    private final ExecutorService compute = Executors.newFixedThreadPool(2, runnable -> {
        Thread thread = new Thread(runnable, "linecode-compute");
        thread.setDaemon(true);
        return thread;
    });

    @Override
    public void main(Runnable runnable) {
        if (isMain()) {
            runnable.run();
        } else {
            main.post(runnable);
        }
    }

    @Override
    public void io(Runnable runnable) {
        io.execute(runnable);
    }

    @Override
    public void compute(Runnable runnable) {
        compute.execute(runnable);
    }

    @Override
    public Cancellable io(long delayMs, Runnable runnable) {
        Post post = new Post(main, runnable, io);
        main.postDelayed(post, Math.max(0, delayMs));
        return post;
    }

    @Override
    public Cancellable main(long delayMs, Runnable runnable) {
        Post post = new Post(main, runnable, null);
        main.postDelayed(post, Math.max(0, delayMs));
        return post;
    }

    @Override
    public boolean isMain() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    @Override
    public void close() {
        io.shutdownNow();
        compute.shutdownNow();
        try {
            io.awaitTermination(2, TimeUnit.SECONDS);
            compute.awaitTermination(2, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    private static final class Post implements Runnable, Cancellable {
        private final Handler handler;
        private final Runnable delegate;
        private final ExecutorService executor;
        private volatile boolean cancelled;

        Post(Handler handler, Runnable delegate, ExecutorService executor) {
            this.handler = handler;
            this.delegate = delegate;
            this.executor = executor;
        }

        @Override
        public void run() {
            if (cancelled) {
                return;
            }
            if (executor == null) {
                delegate.run();
            } else {
                executor.execute(() -> {
                    if (!cancelled) {
                        delegate.run();
                    }
                });
            }
        }

        @Override
        public void cancel() {
            cancelled = true;
            handler.removeCallbacks(this);
        }

        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
