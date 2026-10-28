package cn.lineai.core.mvi.nav;

import cn.lineai.core.kernel.time.Cancellable;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class NavigatorRestoreTest {
    private final NavigationFixture f = new NavigationFixture();

    @Test
    public void saveRestoreRoundTripsWithFreshRegistryAndTypedParams() {
        f.seed();
        f.stack.push(f.edit("output"));
        List<Params.Args> saved = f.stack.save();
        NavigationFixture recreated = new NavigationFixture();
        recreated.stack.restore(saved);
        assertEquals(saved, recreated.stack.save());
        assertSame(recreated.edit, recreated.stack.current().id());
        NavigationFixture.PageParams params =
                (NavigationFixture.PageParams) recreated.stack.current().params();
        assertEquals("output", params.section);
        recreated.stack.push(recreated.edit("output"));
        assertEquals(4, recreated.stack.entries().size());
        recreated.stack.back();
        assertSame(recreated.other, recreated.stack.current().id());
    }

    @Test
    public void restoreReplacesEntireStackInOneNotification() {
        f.stack.push(f.other.key(Params.Empty.INSTANCE));
        NavigationFixture source = new NavigationFixture();
        source.seed();
        List<Integer> sizes = new ArrayList<>();
        f.stack.observe(entries -> sizes.add(entries.size()));
        f.stack.restore(source.stack.save());
        assertEquals(Arrays.asList(1, 3), sizes);
        f.stack.restore(source.stack.save());
        assertEquals(Arrays.asList(1, 3), sizes);
    }

    @Test
    public void unknownRouteInLaterEntryRejectsWholeRestoreAndStackRemainsUsable() {
        f.seed();
        List<Params.Args> before = f.stack.save();
        List<Integer> sizes = new ArrayList<>();
        Cancellable handle = f.stack.observe(entries -> sizes.add(entries.size()));
        List<Params.Args> invalid = Arrays.asList(before.get(0), saved("removed", Params.Args.empty()));
        assertThrows(IllegalArgumentException.class, () -> f.stack.restore(invalid));
        assertEquals(before, f.stack.save());
        assertEquals(Collections.singletonList(3), sizes);
        assertFalse(handle.isCancelled());
        f.stack.back();
        assertEquals(2, f.stack.entries().size());
    }

    @Test
    public void decoderFailureInLaterEntryIsAtomic() {
        f.seed();
        List<Params.Args> before = f.stack.save();
        List<Params.Args> invalid = Arrays.asList(before.get(0), saved(f.edit.route(), Params.Args.empty()));
        assertThrows(IllegalArgumentException.class, () -> f.stack.restore(invalid));
        assertEquals(before, f.stack.save());
    }

    @Test
    public void malformedEnvelopeOrNullEntryCannotPartiallyReplaceState() {
        f.seed();
        List<Params.Args> before = f.stack.save();
        assertThrows(IllegalArgumentException.class,
                () -> f.stack.restore(Collections.singletonList(Params.Args.of("route", f.root.route()))));
        Map<String, Object> malformed = new LinkedHashMap<>();
        malformed.put("route", 1);
        malformed.put("params", Collections.emptyMap());
        assertThrows(IllegalArgumentException.class,
                () -> f.stack.restore(Collections.singletonList(new Params.Args(malformed))));
        List<Params.Args> withNull = new ArrayList<>(before);
        withNull.add(null);
        assertThrows(NullPointerException.class, () -> f.stack.restore(withNull));
        assertEquals(before, f.stack.save());
    }

    @Test
    public void restoreCopiesCallerListBeforePostingToMain() {
        f.seed();
        List<Params.Args> input = new ArrayList<>(f.stack.save());
        BackStack target = new BackStack(f.registry, f.scheduler);
        List<Integer> sizes = new ArrayList<>();
        target.observe(entries -> {
            assertTrue(f.scheduler.isMain());
            sizes.add(entries.size());
        });
        f.scheduler.worker(() -> target.restore(input));
        input.clear();
        assertTrue(target.entries().isEmpty());
        f.scheduler.drainMain();
        assertEquals(3, target.entries().size());
        assertEquals(Arrays.asList(0, 3), sizes);
    }

    @Test
    public void saveIsImmutableAndDetachedFromFutureMutations() {
        f.seed();
        List<Params.Args> saved = f.stack.save();
        assertThrows(UnsupportedOperationException.class, saved::clear);
        assertThrows(UnsupportedOperationException.class, () -> saved.get(0).asMap().clear());
        f.stack.popToRoot();
        assertEquals(3, saved.size());
        assertEquals(1, f.stack.save().size());
    }

    @Test
    public void emptyRestoreClearsAndPopRemainsSafe() {
        f.seed();
        f.stack.restore(Collections.emptyList());
        assertNull(f.stack.current());
        f.stack.back();
        assertTrue(f.stack.entries().isEmpty());
        f.stack.push(f.edit("input"));
        assertEquals(1, f.stack.entries().size());
    }

    @Test
    public void closeDropsPendingRestore() {
        f.seed();
        List<Params.Args> before = f.stack.save();
        f.scheduler.worker(() -> f.stack.restore(Collections.emptyList()));
        f.stack.close();
        f.scheduler.drainMain();
        assertEquals(before, f.stack.save());
    }

    @Test
    public void routeAndParamsNamedParametersDoNotCollideWithEnvelope() {
        ScreenId<NavigationFixture.State, Params.Args> screen = new ScreenId<>("collision", args -> args);
        f.registry.register(screen);
        Params.Args params = new Params.Args(Map.of("route", "user-value", "params", "also-user-value"));
        f.stack.push(screen.key(params));
        BackStack target = new BackStack(f.registry, f.scheduler);
        target.restore(f.stack.save());
        assertEquals(params, target.current().params());
    }

    private Params.Args saved(String route, Params.Args params) {
        return new Params.Args(Map.of("route", route, "params", params));
    }
}
