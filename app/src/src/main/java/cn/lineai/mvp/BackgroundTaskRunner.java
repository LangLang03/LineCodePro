package cn.lineai.mvp;

import java.util.ArrayList;

public final class BackgroundTaskRunner {
    private final Object lock = new Object();
    private final ArrayList<Thread> runningThreads = new ArrayList<>();
    private boolean shutdown;

    public void execute(String name, Runnable runnable) {
        if (runnable == null) {
            return;
        }
        String threadName = name == null || name.trim().length() == 0 ? "linecode-background" : name.trim();
        Thread thread = new Thread(() -> {
            try {
                if (!isShutdown()) {
                    runnable.run();
                }
            } finally {
                synchronized (lock) {
                    runningThreads.remove(Thread.currentThread());
                }
            }
        }, threadName);
        synchronized (lock) {
            if (shutdown) {
                return;
            }
            runningThreads.add(thread);
        }
        thread.start();
    }

    public void shutdownNow() {
        ArrayList<Thread> snapshot;
        synchronized (lock) {
            shutdown = true;
            snapshot = new ArrayList<>(runningThreads);
            runningThreads.clear();
        }
        for (Thread thread : snapshot) {
            if (thread != null) {
                thread.interrupt();
            }
        }
    }

    public boolean isShutdown() {
        synchronized (lock) {
            return shutdown;
        }
    }
}
