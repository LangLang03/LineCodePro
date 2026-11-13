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

    /**
     * Creates a mailbox holding {@code initial} until the first publish.
     * @param initial starting value, never null
     * @param scheduler scheduler that must run tasks on the main thread, never null
     */
    public MainMailbox(T initial, Scheduler scheduler) {
        this.value = Objects.requireNonNull(initial, "initial");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
    }

    /** Returns the latest published value. */
    public T value() {
        return value;
    }

    /** Returns whether {@link #close()} has run. */
    public synchronized boolean isClosed() {
        return closed;
    }

    /**
     * Queues {@code task} for serial execution on the main thread; dropped once closed.
     * @param task task to run, never null
     */
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

    /** Runs queued tasks on the main thread until the queue empties or the mailbox closes. */
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

    /**
     * Subscribes {@code observer}; activation with the current value happens on the main thread.
     * @param observer notified of every published value
     * @return handle cancelling the subscription
     */
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

    /** Cancels all observers, discards queued tasks and ignores further posts; idempotent. */
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

    /** Per-observer handle; delivery stops once cancelled or the mailbox closes. */
    private final class Subscription implements Cancellable {
        private Observable.Observer<T> observer;
        private boolean active;
        private boolean cancelled;

        /** Wraps {@code observer} until activation. */
        private Subscription(Observable.Observer<T> observer) {
            this.observer = observer;
        }

        /** Marks the subscription active and delivers the current value. */
        private void activate() {
            active = true;
            deliver(value);
        }

        /** Invokes the observer when still active, not cancelled and the mailbox is open. */
        private void deliver(T next) {
            if (active && !cancelled && !closed) {
                observer.onChanged(next);
            }
        }

        /** Detaches the observer; idempotent. */
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

        /** Returns whether {@link #cancel()} has run. */
        @Override
        public boolean isCancelled() {
            synchronized (MainMailbox.this) {
                return cancelled;
            }
        }
    }
}
