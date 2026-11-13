package cn.lineai.core.mvi.present;

import cn.lineai.core.kernel.observe.Observable;
import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.internal.MainMailbox;

import java.util.Objects;

/** Fail-fast store: unexpected reducer, observer or effect failures close it and propagate. */
public final class Store<S extends UiState, A extends Action> implements AutoCloseable {
    private final Presenter<S, A> presenter;
    private final Scheduler scheduler;
    private final MainMailbox<S> mailbox;

    /**
     * Creates a store whose state starts at {@code presenter.initial()}.
     * @param presenter reduction and effect logic, never null
     * @param scheduler main and IO execution backing the mailbox, never null
     */
    public Store(Presenter<S, A> presenter, Scheduler scheduler) {
        this.presenter = Objects.requireNonNull(presenter, "presenter");
        this.scheduler = Objects.requireNonNull(scheduler, "scheduler");
        this.mailbox = new MainMailbox<>(presenter.initial(), scheduler);
    }

    /** Returns the latest published state. */
    public S state() {
        return mailbox.value();
    }

    /**
     * Queues {@code action} for serial reduction on the main thread.
     * @param action action to reduce, never null
     */
    public void dispatch(A action) {
        Objects.requireNonNull(action, "action");
        mailbox.post(() -> reduce(action));
    }

    /** Applies {@code action} through the presenter, publishes the state and starts its effects. */
    private void reduce(A action) {
        Transition<S> transition = Objects.requireNonNull(
                presenter.reduce(mailbox.value(), action), "transition");
        mailbox.publish(transition.state());
        for (Effect effect : transition.effects()) {
            if (mailbox.isClosed()) {
                return;
            }
            scheduler.io(() -> run(effect));
        }
    }

    /** Executes {@code effect} on an IO thread; unexpected failures close the store and propagate. */
    private void run(Effect effect) {
        if (mailbox.isClosed()) {
            return;
        }
        try {
            presenter.run(effect, this::dispatch);
        } catch (RuntimeException | Error failure) {
            close();
            throw failure;
        }
    }

    /** Replays current state on main; cancellation is immediate and idempotent. */
    public Cancellable observe(Observable.Observer<S> observer) {
        return mailbox.observe(observer);
    }

    /** Drops pending actions/effects and late emissions; running IO is not interrupted. */
    @Override
    public void close() {
        mailbox.close();
    }
}
