package cn.lineai.ipc.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Process-wide shared executor for the IPC server side.
 *
 * <p>Provider services share one {@link ExecutorService} instead of each creating its own
 * cached pool, which keeps the thread count bounded. All workers are daemon threads and
 * never block process exit.
 *
 * <p>The pool lives for the lifetime of the process: never call {@code shutdown} from
 * {@code Service.onDestroy}.
 */
public final class IpcServerExecutors {

    private static final ExecutorService SHARED;

    static {
        AtomicInteger counter = new AtomicInteger(0);
        ThreadFactory factory = runnable -> {
            Thread thread = new Thread(runnable, "ipc-server-worker-" + counter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
        SHARED = Executors.newCachedThreadPool(factory);
    }

    private IpcServerExecutors() {
    }

    /** Returns the process-wide shared executor. */
    public static ExecutorService shared() {
        return SHARED;
    }
}
