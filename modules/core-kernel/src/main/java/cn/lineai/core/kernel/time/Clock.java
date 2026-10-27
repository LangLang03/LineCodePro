package cn.lineai.core.kernel.time;

/** Injected clock. Reducers never call {@code System.currentTimeMillis()} directly. */
public interface Clock {

    long nowMs();

    static Clock system() {
        return System::currentTimeMillis;
    }

    static Clock fixed(long fixedValue) {
        return () -> fixedValue;
    }
}
