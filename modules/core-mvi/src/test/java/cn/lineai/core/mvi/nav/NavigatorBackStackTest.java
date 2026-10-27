package cn.lineai.core.mvi.nav;

import cn.lineai.core.kernel.time.Cancellable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.*;

public class NavigatorBackStackTest {
    private final NavigationFixture f = new NavigationFixture();

    @Test
    public void emptyStackAndRootCannotBePopped() {
        List<Integer> sizes = new ArrayList<>();
        f.stack.observe(entries -> sizes.add(entries.size()));
        assertNull(f.stack.current());
        f.stack.pop();
        f.stack.back();
        f.stack.popToRoot();
        f.stack.push(f.root.key(Params.Empty.INSTANCE));
        f.stack.pop();
        f.stack.back();
        assertSame(f.root, f.stack.current().id());
        assertEquals(Arrays.asList(0, 1), sizes);
    }

    @Test
    public void pushesAndPopsInOrder() {
        f.seed();
        assertSame(f.other, f.stack.current().id());
        f.stack.pop();
        assertEquals(f.edit("input"), f.stack.current());
        f.stack.back();
        assertSame(f.root, f.stack.current().id());
    }

    @Test
    public void singleTopComparesParamsNotJustRoute() {
        f.stack.push(f.edit("input"));
        f.stack.push(f.edit("input"));
        assertEquals(1, f.stack.entries().size());
        f.stack.push(f.edit("output"));
        assertEquals(2, f.stack.entries().size());
        assertEquals(f.edit("output"), f.stack.current());
    }

    @Test
    public void singleTopDoesNotDeduplicateDeeperEntries() {
        f.stack.push(f.edit("input"));
        f.stack.push(f.other.key(Params.Empty.INSTANCE));
        f.stack.push(f.edit("input"));
        assertEquals(3, f.stack.entries().size());
    }

    @Test
    public void normalScreensAllowDuplicateKeys() {
        f.stack.push(f.other.key(Params.Empty.INSTANCE));
        f.stack.push(f.other.key(Params.Empty.INSTANCE));
        assertEquals(2, f.stack.entries().size());
    }

    @Test
    public void deduplicationDoesNotNotifyObservers() {
        List<Integer> sizes = new ArrayList<>();
        f.stack.observe(entries -> sizes.add(entries.size()));
        f.stack.push(f.edit("input"));
        f.stack.push(f.edit("input"));
        assertEquals(Arrays.asList(0, 1), sizes);
    }

    @Test
    public void popToKeepsNearestMatchingScreen() {
        f.seed();
        f.stack.push(f.edit("output"));
        f.stack.push(f.other.key(Params.Empty.INSTANCE));
        f.stack.popTo(f.edit);
        assertEquals(4, f.stack.entries().size());
        assertEquals(f.edit("output"), f.stack.current());
        f.stack.pop();
        f.stack.popTo(f.edit);
        assertEquals(2, f.stack.entries().size());
        assertEquals(f.edit("input"), f.stack.current());
    }

    @Test
    public void popToAbsentOrCurrentScreenDoesNotNotify() {
        f.seed();
        List<List<ScreenKey<?, ?>>> seen = new ArrayList<>();
        f.stack.observe(seen::add);
        f.stack.popTo(f.absent);
        f.stack.popTo(f.other);
        assertEquals(1, seen.size());
        assertEquals(3, f.stack.entries().size());
    }

    @Test
    public void popToRootKeepsOriginalRootAndSnapshotsStayImmutable() {
        f.seed();
        List<ScreenKey<?, ?>> snapshot = f.stack.entries();
        f.stack.popToRoot();
        assertEquals(1, f.stack.entries().size());
        assertSame(f.root, f.stack.current().id());
        assertEquals(3, snapshot.size());
        assertThrows(UnsupportedOperationException.class, snapshot::clear);
    }

    @Test
    public void unregisteredOrImpersonatingIdIsRejectedWithoutChangingStack() {
        f.seed();
        ScreenId<NavigationFixture.State, Params.Empty> impostor =
                new ScreenId<>(f.root.route(), Params.Empty::fromArgs);
        ScreenId<NavigationFixture.State, Params.Empty> unknown =
                new ScreenId<>("unknown", Params.Empty::fromArgs);
        assertThrows(IllegalArgumentException.class, () -> f.stack.push(impostor.key(Params.Empty.INSTANCE)));
        assertThrows(IllegalArgumentException.class, () -> f.stack.push(unknown.key(Params.Empty.INSTANCE)));
        assertThrows(IllegalArgumentException.class, () -> f.stack.popTo(unknown));
        f.stack.back();
        assertEquals(2, f.stack.entries().size());
    }

    @Test
    public void navigationAndObservationHopToMainInFifoOrder() {
        List<Integer> sizes = new ArrayList<>();
        f.scheduler.worker(() -> {
            f.stack.push(f.root.key(Params.Empty.INSTANCE));
            f.stack.observe(entries -> {
                assertTrue(f.scheduler.isMain());
                sizes.add(entries.size());
            });
            f.stack.push(f.edit("input"));
            f.stack.back();
        });
        assertNull(f.stack.current());
        assertTrue(sizes.isEmpty());
        f.scheduler.drainMain();
        assertEquals(Arrays.asList(1, 2, 1), sizes);
    }

    @Test
    public void reentrantNavigationDoesNotInvertNotifications() {
        List<String> events = new ArrayList<>();
        f.stack.observe(entries -> {
            events.add("first:" + entries.size());
            if (entries.size() == 1) {
                f.stack.push(f.edit("input"));
                assertEquals(1, f.stack.entries().size());
            }
        });
        f.stack.observe(entries -> events.add("second:" + entries.size()));
        f.stack.push(f.root.key(Params.Empty.INSTANCE));
        assertEquals(Arrays.asList("first:0", "second:0", "first:1", "second:1",
                "first:2", "second:2"), events);
    }

    @Test
    public void cancellationBeforeReplayAndAfterReplayStopsNotifications() {
        AtomicReference<Cancellable> pending = new AtomicReference<>();
        f.scheduler.worker(() -> pending.set(f.stack.observe(entries -> fail("cancelled replay"))));
        pending.get().cancel();
        f.scheduler.drainMain();
        List<Integer> sizes = new ArrayList<>();
        Cancellable active = f.stack.observe(entries -> sizes.add(entries.size()));
        active.cancel();
        active.cancel();
        f.seed();
        assertEquals(Collections.singletonList(0), sizes);
        assertTrue(active.isCancelled());
    }

    @Test
    public void closeCancelsQueuedOperationsAndKeepsLastSnapshot() {
        f.seed();
        List<Params.Args> saved = f.stack.save();
        Cancellable handle = f.stack.observe(entries -> { });
        f.scheduler.worker(f.stack::back);
        f.stack.close();
        f.stack.close();
        f.scheduler.drainMain();
        f.stack.push(f.edit("output"));
        f.stack.popToRoot();
        f.stack.popTo(f.root);
        f.stack.restore(Collections.emptyList());
        assertEquals(saved, f.stack.save());
        assertTrue(handle.isCancelled());
        assertTrue(f.stack.observe(entries -> fail("closed replay")).isCancelled());
    }

    @Test
    public void observerFailureIsNotSwallowedAndClosesStack() {
        RuntimeException failure = new IllegalStateException("render failed");
        Cancellable handle = f.stack.observe(entries -> { });
        f.stack.observe(entries -> {
            if (!entries.isEmpty()) {
                throw failure;
            }
        });
        assertSame(failure, assertThrows(RuntimeException.class,
                () -> f.stack.push(f.root.key(Params.Empty.INSTANCE))));
        assertTrue(handle.isCancelled());
        f.stack.push(f.edit("input"));
        assertEquals(1, f.stack.entries().size());
    }
}
