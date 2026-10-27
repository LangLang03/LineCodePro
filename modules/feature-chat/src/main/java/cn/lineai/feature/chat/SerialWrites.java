package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;

/** Runs store writes one at a time on the IO pool; callbacks may arrive faster than SQLite commits. */
final class SerialWrites {

    private final Scheduler scheduler;
    private final List<Runnable> queue = new ArrayList<>();
    private boolean scheduled;

    SerialWrites(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    void submit(Runnable task) {
        synchronized (queue) {
            queue.add(task);
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        scheduler.io(this::drain);
    }

    private void drain() {
        boolean draining = true;
        while (draining) {
            Runnable next = null;
            synchronized (queue) {
                if (queue.isEmpty()) {
                    scheduled = false;
                    draining = false;
                } else {
                    next = queue.remove(0);
                }
            }
            if (next != null) {
                next.run();
            }
        }
    }
}
