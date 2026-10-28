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

    public synchronized boolean isClosed() {
        return closed;
    }

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
