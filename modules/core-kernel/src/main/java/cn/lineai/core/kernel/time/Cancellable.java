package cn.lineai.core.kernel.time;

/**
 * Handle for work that can be cancelled. Cancelling twice is safe.
 *
 * <p>Cancellation is advisory and never throws: implementations stop or detach the associated
 * work, and a cancelled handle stays cancelled, so {@link #isCancelled()} is monotonic.</p>
 */
public interface Cancellable {

    /** Shared no-op handle for callers with nothing to cancel; never reports cancelled. */
    Cancellable NONE = new Cancellable() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    /** Cancels the associated work; repeated calls have no further effect. */
    void cancel();

    /** Returns {@code true} once cancellation has been requested. */
    boolean isCancelled();
}
