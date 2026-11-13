package cn.lineai.core.kernel.time;

/**
 * Injected clock. Reducers never call {@code System.currentTimeMillis()} directly.
 *
 * <p>Time is a dependency rather than a global so that timeouts, throttling and expiry are
 * deterministic under test and consistent across threads.</p>
 */
public interface Clock {

    /** Returns the current wall-clock time in milliseconds since the epoch. */
    long nowMs();

    /** Returns a clock backed by {@code System.currentTimeMillis()} for production wiring. */
    static Clock system() {
        return System::currentTimeMillis;
    }

    /** Returns a clock frozen at {@code fixedValue}, for deterministic tests and replay. */
    static Clock fixed(long fixedValue) {
        return () -> fixedValue;
    }
}
