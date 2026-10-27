package cn.lineai.core.kernel.time;

/** Handle for work that can be cancelled. Cancelling twice is safe. */
public interface Cancellable {

    Cancellable NONE = new Cancellable() {
        @Override
        public void cancel() {
        }

        @Override
        public boolean isCancelled() {
            return false;
        }
    };

    void cancel();

    boolean isCancelled();
}
