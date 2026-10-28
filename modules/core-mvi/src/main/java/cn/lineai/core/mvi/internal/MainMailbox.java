package cn.lineai.core.mvi.internal;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.List;
import java.util.Objects;

/** Serial main-thread delivery. Unexpected callback failures close the mailbox and propagate. */
public final class MainMailbox<T> implements AutoCloseable {
    private final Scheduler scheduler;
    private final Deque<Runnable> pending = new ArrayDeque<>();
    private final List<Subscription> observers = new ArrayList<>();
    private volatile T value;
    private boolean scheduled;
    private boolean closed;

    public MainMailbox(T initial, Scheduler scheduler) {
        this.value = Objects.requireNonNull(initial, "initial");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    public T value() {
        return value;
    }

    public synchronized boolean isClosed() {
        return closed;
    }

    public void post(Runnable task) {
        Objects.requireNonNull(task, "task");
        synchronized (this) {
            if (closed) {
                return;
            }
            pending.addLast(task);
            if (scheduled) {
                return;
            }
            scheduled = true;
        }
        try {
            scheduler.main(this::drain);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    private void drain() {
        try {
            if (!scheduler.isMain()) {
                throw new IllegalStateException("Scheduler.main did not enter the main thread");
            }
            boolean more = true;
            while (more) {
                synchronized (this) {
                    if (closed || pending.isEmpty()) {
                        scheduled = false;
                        more = false;
                    } else {
                        pending.removeFirst().run();
                    }
                }
            }
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /** Only call from a posted task; subscriptions see a stable value before the next task. */
    public synchronized void publish(T next) {
        if (closed) {
            return;
        }
        value = Objects.requireNonNull(next, "next");
        for (Subscription observer : new ArrayList<>(observers)) {
            observer.deliver(next);
        }
    }

    public Cancellable observe(Observable.Observer<T> observer) {
        Subscription subscription = new Subscription(Objects.requireNonNull(observer, "observer"));
        synchronized (this) {
            if (closed) {
                subscription.cancel();
                return subscription;
            }
            observers.add(subscription);
        }
        post(subscription::activate);
        return subscription;
    }

    @Override
    public synchronized void close() {
        if (closed) {
            return;
        }
        closed = true;
        pending.clear();
        for (Subscription subscription : new ArrayList<>(observers)) {
            subscription.cancel();
        }
    }

    private final class Subscription implements Cancellable {
        private Observable.Observer<T> observer;
        private boolean active;
        private boolean cancelled;

        private Subscription(Observable.Observer<T> observer) {
            this.observer = observer;
        }

        private void activate() {
            active = true;
            deliver(value);
        }

        private void deliver(T next) {
            if (active && !cancelled && !closed) {
                observer.onChanged(next);
            }
        }

        @Override
        public void cancel() {
            synchronized (MainMailbox.this) {
                if (!cancelled) {
                    cancelled = true;
                    observer = null;
                    observers.remove(this);
                }
            }
        }

        @Override
        public boolean isCancelled() {
            synchronized (MainMailbox.this) {
                return cancelled;
            }
        }
    }
}
