package cn.lineai.ipc.service;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * IPC 服务端共享线程池。
 *
 * <p>多个 Service 可共享同一个 {@link ExecutorService}，避免每个 Service 各自
 * newCachedThreadPool 带来的线程数不可控。线程标记为守护线程，不会阻止进程退出。
 *
 * <p>本工具属于进程级单例；不要在 Service.onDestroy 中调用 {@code shutdown}，
 * 共享线程池生命周期与进程一致。
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

    /** 获取进程级共享线程池。 */
    public static ExecutorService shared() {
        return SHARED;
    }
}
