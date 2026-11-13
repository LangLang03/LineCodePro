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

    /**
     * Runs a task on the main thread immediately, inlining the call when already on the main
     * thread so the invoking frame sees synchronous ordering.
     *
     * @param runnable task to run; never null
     */
    @Override
    public void main(Runnable runnable) {
        if (isMain()) {
            runnable.run();
        } else {
            main.post(runnable);
        }
    }

    /**
     * Executes a task on the shared io pool.
     *
     * @param runnable task to run; never null
     */
    @Override
    public void io(Runnable runnable) {
        io.execute(runnable);
    }

    /**
     * Executes a task on the two-thread compute pool.
     *
     * @param runnable task to run; never null
     */
    @Override
    public void compute(Runnable runnable) {
        compute.execute(runnable);
    }

    /**
     * Schedules a delayed io task through the main handler's clock.
     *
     * @param delayMs delay in milliseconds; negative values are clamped to zero
     * @param runnable task to run on the io pool when the delay elapses
     * @return a cancellable handle; cancelling before the delay elapses also stops the pool
     *     submission
     */
    @Override
    public Cancellable io(long delayMs, Runnable runnable) {
        Post post = new Post(main, runnable, io);
        main.postDelayed(post, Math.max(0, delayMs));
        return post;
    }

    /**
     * Schedules a delayed main-thread task.
     *
     * @param delayMs delay in milliseconds; negative values are clamped to zero
     * @param runnable task to run on the main thread when the delay elapses
     * @return a cancellable handle
     */
    @Override
    public Cancellable main(long delayMs, Runnable runnable) {
        Post post = new Post(main, runnable, null);
        main.postDelayed(post, Math.max(0, delayMs));
        return post;
    }

    /**
     * Reports whether the calling thread is the Android main thread.
     *
     * @return true when the caller runs on the main looper
     */
    @Override
    public boolean isMain() {
        return Looper.myLooper() == Looper.getMainLooper();
    }

    /**
     * Shuts both pools down and waits up to two seconds each for termination. After this call the
     * scheduler must not be used again; interrupted waits restore the interrupt flag.
     */
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

    /**
     * Delayed-task handle pairing a main-handler timer with an optional io hand-off. Cancellation
     * is checked twice on purpose: before the timer fires and again inside the pool task, so a
     * task already handed to the pool still skips its body.
     */
    private static final class Post implements Runnable, Cancellable {
        private final Handler handler;
        private final Runnable delegate;
        private final ExecutorService executor;
        private volatile boolean cancelled;

        /** Creates the handle; a null executor means the delegate runs on the main thread. */
        Post(Handler handler, Runnable delegate, ExecutorService executor) {
            this.handler = handler;
            this.delegate = delegate;
            this.executor = executor;
        }

        /** Runs the delegate on the main thread or hands it to the io pool, honouring cancellation. */
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

        /** Flags the task cancelled and removes it from the handler queue. */
        @Override
        public void cancel() {
            cancelled = true;
            handler.removeCallbacks(this);
        }

        /** Reports the cancellation flag. */
        @Override
        public boolean isCancelled() {
            return cancelled;
        }
    }
}
