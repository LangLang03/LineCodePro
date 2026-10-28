package cn.lineai.core.kernel.time;

/**
 * The only abstraction over posting work. Nothing in the rewritten code base is allowed to call
 * {@code new Thread}, {@code Handler} or {@code Executors} directly.
 */
public interface Scheduler {

    /** Runs on the UI thread immediately when called from it, otherwise posts. */
    void main(Runnable runnable);

    /** Runs on a background thread suited for blocking IO. */
    void io(Runnable runnable);

    /** Runs on a background thread suited for CPU work. */
    void compute(Runnable runnable);

    /** Runs on a background thread after the given delay. */
    Cancellable io(long delayMs, Runnable runnable);

    /** Runs on the UI thread after the given delay. */
    Cancellable main(long delayMs, Runnable runnable);

    boolean isMain();

    /** Returns a scheduler that executes inline; used by tests and by deterministic reducers. */
    static Scheduler immediate() {
        return new Scheduler() {
            @Override
            public void main(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void io(Runnable runnable) {
                runnable.run();
            }

            @Override
            public void compute(Runnable runnable) {
                runnable.run();
            }

            @Override
            public Cancellable io(long delayMs, Runnable runnable) {
                runnable.run();
                return Cancellable.NONE;
            }

            @Override
            public Cancellable main(long delayMs, Runnable runnable) {
                runnable.run();
                return Cancellable.NONE;
            }

            @Override
            public boolean isMain() {
                return true;
            }
        };
    }
}
