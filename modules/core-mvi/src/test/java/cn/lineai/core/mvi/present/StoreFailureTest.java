package cn.lineai.core.mvi.present;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.mvi.testing.TestScheduler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static org.junit.Assert.*;

public class StoreFailureTest {
    private final RuntimeException failure = new IllegalStateException("failure");
    private final TestScheduler scheduler = new TestScheduler();
    private final TestPresenter presenter = new TestPresenter();
    private final Store<State, Event> store = new Store<>(presenter, scheduler);

    @Test
    public void reducerFailurePropagatesAndCancelsPendingWork() {
        Cancellable handle = store.observe(state -> { });
        presenter.reduce = () -> {
            store.dispatch(Event.RUN);
            throw failure;
        };
        assertSame(failure, assertThrows(RuntimeException.class, () -> store.dispatch(Event.RUN)));
        assertTrue(handle.isCancelled());
        assertSame(presenter.initial, store.state());
        assertEquals(0, scheduler.ioTasks());
        store.dispatch(Event.RUN);
    }

    @Test
    public void asynchronousReducerFailureReachesMainSchedulerCaller() {
        presenter.reduce = () -> { throw failure; };
        scheduler.worker(() -> store.dispatch(Event.RUN));
        assertSame(failure, assertThrows(RuntimeException.class, scheduler::drainMain));
    }

    @Test
    public void observerFailurePropagatesWithoutRunningEffects() {
        Cancellable handle = store.observe(state -> { });
        store.observe(state -> {
            if (state != presenter.initial) {
                throw failure;
            }
        });
        assertSame(failure, assertThrows(RuntimeException.class, () -> store.dispatch(Event.RUN)));
        assertTrue(handle.isCancelled());
        assertEquals(0, scheduler.ioTasks());
    }

    @Test
    public void initialObserverFailureDoesNotLeakSubscription() {
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> store.observe(state -> { throw failure; })));
        assertTrue(store.observe(state -> fail("closed store")).isCancelled());
    }

    @Test
    public void ioFailurePropagatesAndRejectsAlreadyEmittedActions() {
        Cancellable handle = store.observe(state -> { });
        presenter.effect = () -> { throw failure; };
        store.dispatch(Event.RUN);
        assertSame(failure, assertThrows(RuntimeException.class, scheduler::drainIo));
        assertTrue(handle.isCancelled());
        scheduler.drainMain();
        assertEquals(1, presenter.reductions);
    }

    @Test
    public void schedulerRejectionPropagatesAndClosesStore() {
        TestScheduler rejecting = new TestScheduler() {
            @Override
            public void io(Runnable task) {
                throw failure;
            }
        };
        Store<State, Event> target = new Store<>(presenter, rejecting);
        Cancellable handle = target.observe(state -> { });
        assertSame(failure, assertThrows(RuntimeException.class, () -> target.dispatch(Event.RUN)));
        assertTrue(handle.isCancelled());
    }

    @Test
    public void mainSchedulerRejectionPropagates() {
        TestScheduler rejecting = new TestScheduler() {
            @Override
            public void main(Runnable task) {
                throw failure;
            }
        };
        Store<State, Event> target = new Store<>(presenter, rejecting);
        assertSame(failure, assertThrows(RuntimeException.class, () -> target.dispatch(Event.RUN)));
        assertTrue(target.observe(state -> fail("closed store")).isCancelled());
    }

    @Test
    public void incorrectMainSchedulerFailsFast() {
        TestScheduler incorrect = new TestScheduler() {
            @Override
            public boolean isMain() {
                return false;
            }

            @Override
            public void main(Runnable task) {
                task.run();
            }
        };
        Store<State, Event> target = new Store<>(presenter, incorrect);
        assertThrows(IllegalStateException.class, () -> target.dispatch(Event.RUN));
        assertEquals(0, presenter.reductions);
    }

    @Test
    public void transitionDefensivelyCopiesEffectsAndRejectsNulls() {
        Effect[] effects = {Job.FIRST, Job.SECOND};
        Transition<State> transition = Transition.with(presenter.initial, effects);
        effects[0] = Job.SECOND;
        assertEquals(Arrays.asList(Job.FIRST, Job.SECOND), transition.effects());
        assertThrows(UnsupportedOperationException.class, () -> transition.effects().clear());
        assertThrows(UnsupportedOperationException.class, () -> transition.effects().set(0, Job.SECOND));
        assertTrue(Transition.state(presenter.initial).effects().isEmpty());
        assertThrows(UnsupportedOperationException.class,
                () -> Transition.state(presenter.initial).effects().add(Job.FIRST));
        assertThrows(NullPointerException.class, () -> Transition.state(null));
        assertThrows(NullPointerException.class, () -> Transition.with(presenter.initial, (Effect) null));
        assertThrows(NullPointerException.class, () -> Transition.with(presenter.initial, (Effect[]) null));
        assertThrows(NullPointerException.class,
                () -> Transition.with(presenter.initial, Job.FIRST, null));
    }

    @Test
    public void allEffectsUseIoInDeclaredSubmissionOrder() {
        List<Effect> seen = new ArrayList<>();
        presenter.seen = seen;
        store.dispatch(Event.RUN);
        assertTrue(seen.isEmpty());
        scheduler.drainIo();
        assertEquals(Arrays.asList(Job.FIRST, Job.SECOND), seen);
    }

    private static final class State implements UiState { }
    private enum Event implements Action { RUN }
    private enum Job implements Effect { FIRST, SECOND }

    private static final class TestPresenter implements Presenter<State, Event> {
        private final State initial = new State();
        private int reductions;
        private Runnable reduce = () -> { };
        private Runnable effect = () -> { };
        private List<Effect> seen = new ArrayList<>();

        @Override
        public State initial() {
            return initial;
        }

        @Override
        public Transition<State> reduce(State state, Event action) {
            reductions++;
            reduce.run();
            return Transition.with(new State(), Job.FIRST, Job.SECOND);
        }

        @Override
        public void run(Effect job, Emitter<Event> out) {
            seen.add(job);
            out.emit(Event.RUN);
            effect.run();
        }
    }
}
