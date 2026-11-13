package cn.lineai.core.kernel.observe;

import cn.lineai.core.kernel.time.Cancellable;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

/**
 * Minimal observable value.
 *
 * <p>Emits the current value to every new observer, then every subsequent change. Listeners are
 * notified on the thread that calls {@link #set(Object)}; the store that owns the observable is
 * responsible for hopping onto the main thread first.</p>
 */
public final class Observable<T> {

    /** Callback invoked with the current value on subscription and with each new value after. */
    public interface Observer<T> {
        /** Receives a value synchronously on the thread that produced it. */
        void onChanged(T value);
    }

    private final List<Observer<T>> observers = new CopyOnWriteArrayList<>();
    private volatile T value;

    /** Creates an observable holding {@code initial} as its current value. */
    public Observable(T initial) {
        this.value = initial;
    }

    /** Returns the current value. */
    public T value() {
        return value;
    }

    /**
     * Replaces the current value and notifies every observer synchronously on the calling thread.
     *
     * @param next new value to publish
     */
    public void set(T next) {
        value = next;
        for (Observer<T> observer : observers) {
            observer.onChanged(next);
        }
    }

    /**
     * Subscribes an observer and immediately delivers the current value to it.
     *
     * @param observer callback to register; {@code null} yields a no-op handle
     * @return handle that removes the observer when cancelled; cancelling twice is safe
     */
    public Cancellable observe(Observer<T> observer) {
        if (observer == null) {
            return Cancellable.NONE;
        }
        observers.add(observer);
        observer.onChanged(value);
        boolean[] cancelled = {false};
        return new Cancellable() {
            @Override
            public void cancel() {
                if (!cancelled[0]) {
                    cancelled[0] = true;
                    observers.remove(observer);
                }
            }

            @Override
            public boolean isCancelled() {
                return cancelled[0];
            }
        };
    }
}
