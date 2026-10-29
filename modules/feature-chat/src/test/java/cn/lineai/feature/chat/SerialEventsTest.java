package cn.lineai.feature.chat;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/** The chat event queue must preserve submission order even when handlers post more work. */
public class SerialEventsTest {

    @Test
    public void drainsPostedEventsInSubmissionOrder() {
        QueueScheduler scheduler = new QueueScheduler();
        SerialEvents events = new SerialEvents(scheduler);
        List<Integer> seen = new ArrayList<>();

        events.post(() -> seen.add(1));
        events.post(() -> {
            seen.add(2);
            events.post(() -> seen.add(4));
        });
        events.post(() -> seen.add(3));

        assertTrue("nothing runs before the main queue drains", seen.isEmpty());
        scheduler.runMain();
        assertEquals(Arrays.asList(1, 2, 3, 4), seen);
    }

    /** A scheduler that defers {@code main} work, mirroring Android's Handler queue. */
    private static final class QueueScheduler implements Scheduler {

        private final Deque<Runnable> main = new ArrayDeque<>();

        @Override public void main(Runnable runnable) {
            main.add(runnable);
        }

        @Override public void io(Runnable runnable) {
            runnable.run();
        }

        @Override public void compute(Runnable runnable) {
            runnable.run();
        }

        @Override public Cancellable io(long delayMs, Runnable runnable) {
            runnable.run();
            return Cancellable.NONE;
        }

        @Override public Cancellable main(long delayMs, Runnable runnable) {
            runnable.run();
            return Cancellable.NONE;
        }

        @Override public boolean isMain() {
            return false;
        }

        void runMain() {
            while (!main.isEmpty()) {
                main.poll().run();
            }
        }
    }
}
