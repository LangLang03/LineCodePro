package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;

/**
 * Runs store writes one at a time on the IO pool; callbacks may arrive faster than SQLite commits.
 *
 * <p>This is the IO-side counterpart of {@link SerialEvents}, the second sanctioned hand-rolled
 * ordered queue behind the chat event loop: a synchronized list drained FIFO on the io scheduler.
 * Its drain tolerates failed writes — a store exception is logged and the queue keeps going, so
 * one bad write can never stall the writes queued behind it.</p>
 */
final class SerialWrites {

    private final Scheduler scheduler;
    private final List<Runnable> queue = new ArrayList<>();
    private boolean scheduled;

    /** @param scheduler io scheduler used to run the drain */
    SerialWrites(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Enqueues a write; the first enqueue of an empty queue schedules one drain, later enqueues
     * piggyback on that pending drain, preserving submission order.
     *
     * @param task store write; runs on the io scheduler
     */
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

    /** Drains the queue FIFO; write failures are logged, never fatal to the queue or thread. */
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
                // A failed write (e.g. the conversation was deleted mid-turn) must not
                // kill the io thread or stall the writes queued behind it.
                try {
                    next.run();
                } catch (RuntimeException error) {
                    System.err.println("Chat write failed: " + error);
                }
            }
        }
    }
}
