package cn.lineai.core.kernel.time;

import java.util.ArrayList;
import java.util.List;

/**
 * Structured cancellation for one lifetime (a screen, a generation, a tool batch).
 *
 * <p>Closing a scope cancels every tracked handle in reverse order, so nobody has to remember to
 * unsubscribe: the scope is created when the screen is created and closed when it is destroyed.</p>
 */
public final class Scope implements AutoCloseable {

    private final List<Cancellable> tracked = new ArrayList<>();
    private boolean closed;

    /**
     * Registers a handle with this scope and returns it unchanged.
     *
     * <p>Tracking after the scope is closed cancels the handle immediately, which keeps
     * subscriptions started during teardown from leaking past the lifetime.</p>
     *
     * @param cancellable handle to track; {@code null} is accepted and ignored
     * @param <T> concrete handle type
     * @return the handle passed in, already cancelled when this scope is closed
     */
    public synchronized <T extends Cancellable> T track(T cancellable) {
        if (cancellable == null) {
            return null;
        }
        if (closed) {
            cancellable.cancel();
            return cancellable;
        }
        tracked.add(cancellable);
        return cancellable;
    }

    /** Returns {@code true} once this scope has been closed. */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Cancels every tracked handle in reverse registration order and marks the scope closed.
     *
     * <p>Repeated calls do nothing. Handles are cancelled outside the monitor so a listener that
     * re-enters the scope during teardown cannot deadlock.</p>
     */
    @Override
    public void close() {
        List<Cancellable> snapshot;
        synchronized (this) {
            if (closed) {
                return;
            }
            closed = true;
            snapshot = new ArrayList<>(tracked);
            tracked.clear();
        }
        for (int i = snapshot.size() - 1; i >= 0; i--) {
            snapshot.get(i).cancel();
        }
    }
}
