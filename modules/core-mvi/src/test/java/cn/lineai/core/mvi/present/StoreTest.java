package cn.lineai.core.mvi.present;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.mvi.testing.TestScheduler;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class StoreTest {
    private final TestScheduler scheduler = new TestScheduler();
    private final CounterPresenter presenter = new CounterPresenter();
    private final Store<Count, Event> store = new Store<>(presenter, scheduler);

    @Test
    public void initializesOnceAndReplaysCurrentState() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> seen.add(state.value));
        assertEquals(1, presenter.initialCalls);
        assertEquals(Collections.singletonList(0), seen);
        assertSame(presenter.initial, store.state());
    }

    @Test
    public void foreignThreadDispatchHopsToMain() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> {
            assertTrue(scheduler.isMain());
            seen.add(state.value);
        });
        CompletableFuture.runAsync(() -> store.dispatch(Event.ADD)).join();
        assertEquals(0, store.state().value);
        assertEquals(1, scheduler.mainTasks());
        scheduler.drainMain();
        assertEquals(Arrays.asList(0, 1), seen);
        assertSame(Thread.currentThread(), presenter.reduceThread);
    }

    @Test
    public void foreignThreadSubscriptionReplaysOnMainAndCanBeCancelledBeforeReplay() {
        List<Integer> seen = new ArrayList<>();
        AtomicReference<Cancellable> handle = new AtomicReference<>();
        scheduler.worker(() -> handle.set(store.observe(state -> seen.add(state.value))));
        assertTrue(seen.isEmpty());
        handle.get().cancel();
        scheduler.drainMain();
        assertTrue(seen.isEmpty());
        assertTrue(handle.get().isCancelled());
    }

    @Test
    public void subscriptionAndActionShareFifoMailbox() {
        List<Integer> seen = new ArrayList<>();
        scheduler.worker(() -> {
            store.dispatch(Event.ADD);
            store.observe(state -> {
                assertTrue(scheduler.isMain());
                seen.add(state.value);
            });
            store.dispatch(Event.ADD);
        });
        scheduler.drainMain();
        assertEquals(Arrays.asList(1, 2), seen);
    }

    @Test
    public void observersCannotReenterReductionOrInvertNotificationOrder() {
        List<String> seen = new ArrayList<>();
        store.observe(state -> {
            seen.add("first:" + state.value);
            if (state.value == 1) {
                store.dispatch(Event.ADD);
                assertEquals(1, store.state().value);
            }
        });
        store.observe(state -> seen.add("second:" + state.value));
        store.dispatch(Event.ADD);
        assertEquals(Arrays.asList("first:0", "second:0", "first:1", "second:1",
                "first:2", "second:2"), seen);
    }

    @Test
    public void initialReplayAlsoUsesMailbox() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> {
            seen.add(state.value);
            if (state.value == 0) {
                store.dispatch(Event.ADD);
                assertEquals(0, store.state().value);
            }
        });
        assertEquals(Arrays.asList(0, 1), seen);
    }

    @Test
    public void reducerReentrantDispatchSeesCommittedPredecessor() {
        presenter.duringReduce = () -> {
            presenter.duringReduce = () -> { };
            store.dispatch(Event.ADD);
            assertEquals(0, store.state().value);
        };
        store.dispatch(Event.ADD);
        assertEquals(2, store.state().value);
        assertEquals(2, presenter.reduceCalls);
    }

    @Test
    public void synchronousEffectEmissionDoesNotGrowTheStack() {
        Presenter<Count, Event> looping = new Presenter<Count, Event>() {
            @Override
            public Count initial() {
                return new Count(0);
            }

            @Override
            public Transition<Count> reduce(Count state, Event action) {
                Count next = new Count(state.value + 1);
                return next.value < 10000 ? Transition.with(next, Work.LOAD) : Transition.state(next);
            }

            @Override
            public void run(Effect effect, Emitter<Event> out) {
                out.emit(Event.ADD);
            }
        };
        Store<Count, Event> immediate = new Store<>(looping, Scheduler.immediate());
        immediate.dispatch(Event.ADD);
        assertEquals(10000, immediate.state().value);
    }

    @Test
    public void effectsRunOnIoAndEmitBackThroughMain() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> seen.add(state.value));
        store.dispatch(Event.LOAD);
        assertEquals(Arrays.asList(0, 0), seen);
        assertEquals(1, scheduler.ioTasks());
        assertEquals(0, presenter.ioCalls);
        scheduler.drainIo();
        assertEquals(1, presenter.ioCalls);
        assertEquals(0, store.state().value);
        scheduler.drainMain();
        assertEquals(Arrays.asList(0, 0, 1), seen);
    }

    @Test
    public void cancelsIndividualSubscriptionsWithoutClosingStore() {
        List<Integer> seen = new ArrayList<>();
        Cancellable subscription = store.observe(state -> seen.add(state.value));
        subscription.cancel();
        subscription.cancel();
        store.dispatch(Event.ADD);
        assertEquals(Collections.singletonList(0), seen);
        assertEquals(1, store.state().value);
    }

    @Test
    public void subscribingDuringNotificationDoesNotReplayTwice() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> {
            if (state.value == 1) {
                store.observe(next -> seen.add(next.value));
            }
        });
        store.dispatch(Event.ADD);
        store.dispatch(Event.ADD);
        assertEquals(Arrays.asList(1, 2), seen);
    }

    @Test
    public void cancellationDuringNotificationSkipsCancelledObserver() {
        List<Integer> seen = new ArrayList<>();
        AtomicReference<Cancellable> second = new AtomicReference<>();
        store.observe(state -> {
            if (state.value == 1) {
                second.get().cancel();
            }
        });
        second.set(store.observe(state -> seen.add(state.value)));
        store.dispatch(Event.ADD);
        assertEquals(Collections.singletonList(0), seen);
    }

    @Test
    public void closeDropsQueuedDispatchAndSubscriptions() {
        List<Integer> seen = new ArrayList<>();
        Cancellable subscription = store.observe(state -> seen.add(state.value));
        scheduler.worker(() -> store.dispatch(Event.ADD));
        store.close();
        store.close();
        scheduler.drainMain();
        store.dispatch(Event.ADD);
        assertTrue(subscription.isCancelled());
        assertEquals(Collections.singletonList(0), seen);
        assertEquals(0, presenter.reduceCalls);
    }

    @Test
    public void closeBeforeIoPreventsEffectExecution() {
        store.dispatch(Event.LOAD);
        store.close();
        scheduler.drainIo();
        assertEquals(0, presenter.ioCalls);
        assertEquals(0, store.state().value);
    }

    @Test
    public void closeRejectsQueuedAndLateEffectEmissions() {
        store.dispatch(Event.LOAD);
        scheduler.drainIo();
        assertEquals(1, scheduler.mainTasks());
        store.close();
        presenter.emitter.emit(Event.ADD);
        scheduler.drainMain();
        assertEquals(0, store.state().value);
        assertEquals(1, presenter.reduceCalls);
    }

    @Test
    public void closeDuringNotificationStopsRemainingObserversAndEffects() {
        List<Integer> seen = new ArrayList<>();
        store.observe(state -> {
            if (presenter.reduceCalls > 0) {
                store.close();
            }
        });
        Cancellable second = store.observe(state -> seen.add(state.value));
        store.dispatch(Event.LOAD);
        assertTrue(second.isCancelled());
        assertEquals(Collections.singletonList(0), seen);
        assertEquals(0, scheduler.ioTasks());
    }

    @Test
    public void closedStoreDoesNotReplayNewSubscriptions() {
        store.close();
        Cancellable subscription = store.observe(state -> fail("closed replay"));
        assertTrue(subscription.isCancelled());
        assertEquals(0, scheduler.mainTasks());
    }

    @Test
    public void closeBeforeSubscriptionReplayCancelsReturnedHandle() {
        AtomicReference<Cancellable> subscription = new AtomicReference<>();
        scheduler.worker(() -> subscription.set(store.observe(state -> fail("late replay"))));
        store.close();
        scheduler.drainMain();
        assertTrue(subscription.get().isCancelled());
    }

    private static final class Count implements UiState {
        private final int value;

        private Count(int value) {
            this.value = value;
        }
    }

    private enum Event implements Action { ADD, LOAD }
    private enum Work implements Effect { LOAD }

    private final class CounterPresenter implements Presenter<Count, Event> {
        private final Count initial = new Count(0);
        private int initialCalls;
        private int reduceCalls;
        private int ioCalls;
        private Thread reduceThread;
        private Emitter<Event> emitter;
        private Runnable duringReduce = () -> { };

        @Override
        public Count initial() {
            initialCalls++;
            return initial;
        }

        @Override
        public Transition<Count> reduce(Count state, Event action) {
            assertTrue(scheduler.isMain());
            reduceThread = Thread.currentThread();
            reduceCalls++;
            duringReduce.run();
            return action == Event.LOAD ? Transition.with(state, Work.LOAD)
                    : Transition.state(new Count(state.value + 1));
        }

        @Override
        public void run(Effect effect, Emitter<Event> out) {
            assertFalse(scheduler.isMain());
            assertSame(Work.LOAD, effect);
            ioCalls++;
            emitter = out;
            out.emit(Event.ADD);
        }
    }
}
