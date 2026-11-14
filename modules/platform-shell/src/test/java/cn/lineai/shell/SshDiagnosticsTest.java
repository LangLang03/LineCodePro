package cn.lineai.shell;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Deque;
import java.util.List;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

/**
 * Verifies the io-to-main hand-off of diagnostic reports: the probe blocks on the io lane while
 * the consumer mutates page views, so the consumer must never run on the io thread.
 */
public class SshDiagnosticsTest {

    @Test public void reportArrivesOnMainAfterTheIoProbeCompletes() {
        Lanes scheduler = new Lanes();
        List<String> lanes = new ArrayList<>();
        List<String> results = new ArrayList<>();

        SshDiagnostics.runOnIoDeliverOnMain(scheduler,
                () -> {
                    lanes.add(scheduler.lane());
                    return "probe-output";
                },
                result -> {
                    lanes.add(scheduler.lane());
                    results.add(result);
                });

        assertEquals("the probe is queued on the io lane", 1, scheduler.ioTasks());
        assertTrue("nothing reaches the page before the io lane drains", lanes.isEmpty());

        scheduler.drainIo();

        assertEquals("the probe runs on the io lane", Arrays.asList("io"), lanes);
        assertTrue("no delivery while only the io lane has run", results.isEmpty());
        assertEquals("the report waits on the main lane", 1, scheduler.mainTasks());

        scheduler.drainMain();

        assertEquals("the page callback runs on the main lane", Arrays.asList("io", "main"), lanes);
        assertEquals(1, results.size());
        assertEquals("probe-output", results.get(0));
    }

    @Test public void immediateSchedulerStillDeliversTheReport() {
        List<String> delivered = new ArrayList<>();
        SshDiagnostics.runOnIoDeliverOnMain(Scheduler.immediate(), () -> "ok", delivered::add);
        assertEquals(Arrays.asList("ok"), delivered);
    }

    /** Two-lane scheduler that queues work instead of spawning threads. */
    private static final class Lanes implements Scheduler {
        private final Deque<Runnable> io = new ArrayDeque<>();
        private final Deque<Runnable> main = new ArrayDeque<>();
        private final Thread owner = Thread.currentThread();
        private boolean background;

        @Override public void main(Runnable runnable) {
            if (isMain()) {
                runnable.run();
            } else {
                main.add(runnable);
            }
        }

        @Override public void io(Runnable runnable) {
            io.add(runnable);
        }

        @Override public void compute(Runnable runnable) {
            throw new AssertionError("diagnostics must not use the compute lane");
        }

        @Override public Cancellable io(long delayMs, Runnable runnable) {
            throw new AssertionError("diagnostics must not use delayed io");
        }

        @Override public Cancellable main(long delayMs, Runnable runnable) {
            throw new AssertionError("diagnostics must not use delayed main");
        }

        @Override public boolean isMain() {
            return Thread.currentThread() == owner && !background;
        }

        String lane() {
            return isMain() ? "main" : "io";
        }

        int ioTasks() {
            return io.size();
        }

        int mainTasks() {
            return main.size();
        }

        /** Drains the io lane while posing as its worker thread. */
        void drainIo() {
            background = true;
            try {
                drain(io);
            } finally {
                background = false;
            }
        }

        /** Drains the main lane on its owner thread. */
        void drainMain() {
            if (!isMain()) throw new AssertionError("the main lane must drain on its owner");
            drain(main);
        }

        private static void drain(Deque<Runnable> queue) {
            Runnable task;
            while ((task = queue.poll()) != null) task.run();
        }
    }
}
