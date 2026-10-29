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
 */
final class SerialEvents {

    private final Scheduler scheduler;
    private final Deque<Runnable> queue = new ArrayDeque<>();
    private boolean scheduled;

    SerialEvents(Scheduler scheduler) {
        this.scheduler = scheduler;
    }

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

    private void drain() {
        Runnable next = poll();
        while (next != null) {
            next.run();
            next = poll();
        }
    }

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
