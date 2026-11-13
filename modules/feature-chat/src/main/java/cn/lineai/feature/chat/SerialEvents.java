package cn.lineai.feature.chat;

import java.util.ArrayDeque;
import java.util.Deque;

import cn.lineai.core.kernel.time.Scheduler;

/**
 * FIFO event queue for chat state transitions.
 *
 * <p>Stream deltas arrive on the network thread while tool results and turn boundaries are posted
 * from the IO pool. Posting them straight to the main thread lets a finished message overtake its
 * last delta, which used to flash interstitial prose into the answer area and then fold it back.
 * Every model/tool callback is enqueued here and drained in submission order on the main thread;
 * message drafts, sequence numbers and approvals are only mutated inside that drain.</p>
 *
 * <p>This is one of the two sanctioned hand-rolled ordered queues behind the chat event loop
 * (the IO-side counterpart is {@link SerialWrites}): a synchronized deque guarded by a single
 * scheduled flag, deliberately not replaced by an executor to keep drain order total.</p>
 */
final class SerialEvents {

    private final Scheduler scheduler;
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private boolean scheduled;

    /** @param scheduler main scheduler used to run the drain */
    SerialEvents(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

    /**
     * Enqueues a task in submission order; the first enqueue of an empty queue schedules one
     * drain on the main thread, later enqueues piggyback on that pending drain.
     *
     * @param task event callback; null is ignored
     */
    void post(Runnable task) {
        if (task == null) {
            return;
        }
        synchronized (queue) {
            queue.add(task);
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        scheduler.main(this::drain);
    }

    /** Runs queued tasks in submission order until the queue reports empty. */
    private void drain() {
        Runnable next = poll();
        while (next != null) {
            next.run();
            next = poll();
        }
    }

    /** Pops the next task; an empty queue clears the scheduled flag so new posts schedule again. */
    private Runnable poll() {
        synchronized (queue) {
            Runnable next = queue.poll();
            if (next == null) {
                scheduled = false;
            }
            return next;
        }
    }
}
