package cn.lineai.core.mvi.testing;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;

import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

/** Deterministic lanes; foreign threads also post to the main queue. */
public class TestScheduler implements Scheduler {
    private final Thread owner = Thread.currentThread();
    private final Queue<Runnable> main = new ConcurrentLinkedQueue<>();
    private final Queue<Runnable> io = new ConcurrentLinkedQueue<>();
    private boolean worker;

    @Override
    public void main(Runnable runnable) {
        if (isMain()) {
            runnable.run();
        } else {
            main.add(runnable);
        }
    }

    @Override
    public void io(Runnable runnable) {
        io.add(runnable);
    }

    @Override
    public void compute(Runnable runnable) {
        throw new AssertionError("compute must not be used");
    }

    @Override
    public Cancellable io(long delayMs, Runnable runnable) {
        throw new AssertionError("Delayed IO must not be used");
    }

    @Override
    public Cancellable main(long delayMs, Runnable runnable) {
        throw new AssertionError("Delayed main must not be used");
    }

    @Override
    public boolean isMain() {
        return Thread.currentThread() == owner && !worker;
    }

    public void worker(Runnable runnable) {
        boolean previous = worker;
        worker = true;
        try {
            runnable.run();
        } finally {
            worker = previous;
        }
    }

    public void drainMain() {
        if (!isMain()) {
            throw new AssertionError("Main queue must drain on its owner");
        }
        drain(main);
    }

    public void drainIo() {
        worker(() -> drain(io));
    }

    public int mainTasks() {
        return main.size();
    }

    public int ioTasks() {
        return io.size();
    }

    private void drain(Queue<Runnable> queue) {
        Runnable task;
        while ((task = queue.poll()) != null) {
            task.run();
        }
    }
}
