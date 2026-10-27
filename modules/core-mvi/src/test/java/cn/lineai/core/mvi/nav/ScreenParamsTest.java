package cn.lineai.core.mvi.nav;

import org.junit.Test;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.Assert.*;

public class ScreenParamsTest {
    private final NavigationFixture f = new NavigationFixture();

    @Test
    public void argsDefensivelyCopiesNestedMapsAndLists() {
        Map<String, Object> child = new HashMap<>();
        child.put("enabled", true);
        List<Object> list = new ArrayList<>();
        list.add(child);
        Map<String, Object> source = new HashMap<>();
        source.put("child", child);
        source.put("list", list);
        Params.Args args = new Params.Args(source);
        child.put("enabled", false);
        list.clear();
        source.clear();
        assertEquals(true, args.requireArgs("child").get("enabled"));
        assertEquals(1, ((List<?>) args.get("list")).size());
        assertThrows(UnsupportedOperationException.class, () -> args.asMap().clear());
        assertThrows(UnsupportedOperationException.class, () -> ((Map<?, ?>) args.get("child")).clear());
        assertThrows(UnsupportedOperationException.class, () -> ((List<?>) args.get("list")).clear());
        Map<?, ?> nested = (Map<?, ?>) ((List<?>) args.get("list")).get(0);
        assertEquals(true, nested.get("enabled"));
        assertThrows(UnsupportedOperationException.class, nested::clear);
    }

    @Test
    public void argsUseValueEqualityAndKeepScalarTypes() {
        Map<String, Object> leftValues = new LinkedHashMap<>();
        leftValues.put("name", "theme");
        leftValues.put("count", 2);
        leftValues.put("revision", 3L);
        Map<String, Object> rightValues = new LinkedHashMap<>();
        rightValues.put("revision", 3L);
        rightValues.put("count", 2);
        rightValues.put("name", "theme");
        Params.Args left = new Params.Args(leftValues);
        Params.Args right = new Params.Args(rightValues);
        assertEquals(left, right);
        assertEquals(left.hashCode(), right.hashCode());
        assertSame(left, left.toArgs());
        assertEquals("theme", left.requireString("name"));
        assertEquals(2, left.get("count"));
        assertNotEquals(Params.Args.of("count", 2L), Params.Args.of("count", 2));
        assertNull(left.get("missing"));
        assertThrows(IllegalArgumentException.class, () -> left.requireString("missing"));
        assertThrows(IllegalArgumentException.class, () -> left.requireString("count"));
        assertThrows(IllegalArgumentException.class, () -> left.requireArgs("name"));
    }

    @Test
    public void argsRejectsNullsMutableObjectsAndNonStringNestedKeys() {
        Map<String, Object> nullValue = new HashMap<>();
        nullValue.put("null", null);
        assertThrows(NullPointerException.class, () -> new Params.Args(nullValue));
        assertThrows(NullPointerException.class, () -> new Params.Args(null));
        assertThrows(NullPointerException.class, () -> Params.Args.of(null, "value"));
        assertThrows(NullPointerException.class, () -> Params.Args.of("key", null));
        assertThrows(NullPointerException.class,
                () -> Params.Args.of("list", Arrays.asList("valid", null)));
        assertThrows(NullPointerException.class,
                () -> Params.Args.of("map", nullValue));
        assertThrows(IllegalArgumentException.class,
                () -> Params.Args.of("map", Collections.singletonMap(null, "value")));
        assertThrows(IllegalArgumentException.class, () -> Params.Args.of("object", new Object()));
        assertThrows(IllegalArgumentException.class, () -> Params.Args.of("bytes", new byte[1]));
        assertThrows(IllegalArgumentException.class,
                () -> Params.Args.of("map", Collections.singletonMap(1, "value")));
    }

    @Test
    public void emptyParamsRestoresOnlyAnEmptyMap() {
        assertEquals(Params.Args.empty(), Params.Empty.INSTANCE.toArgs());
        assertSame(Params.Empty.INSTANCE, Params.Empty.fromArgs(Params.Args.empty()));
        assertThrows(IllegalArgumentException.class,
                () -> Params.Empty.fromArgs(Params.Args.of("unexpected", true)));
        assertSame(Params.Empty.INSTANCE, f.root.restore(Params.Args.empty()).params());
    }

    @Test
    public void screenIdentityUsesCanonicalIdAndSerializedParamsNotObjectIdentity() {
        ScreenKey<NavigationFixture.State, NavigationFixture.PageParams> first = f.edit("input");
        ScreenKey<NavigationFixture.State, NavigationFixture.PageParams> second = f.edit("input");
        assertNotSame(first.params(), second.params());
        assertEquals(first, second);
        assertEquals(first.hashCode(), second.hashCode());
        assertNotEquals(first, f.edit("output"));
        ScreenId<NavigationFixture.State, NavigationFixture.PageParams> impostor =
                new ScreenId<>(f.edit.route(), NavigationFixture.PageParams::fromArgs);
        assertNotEquals(first, impostor.key(first.params()));
        assertEquals(first, f.edit.restore(first.args()));
        assertSame(f.root, f.edit.parent());
        assertTrue(f.edit.singleTop());
        assertFalse(f.other.singleTop());
    }

    @Test
    public void registryRejectsDuplicateRoutesAndUnknownRoutes() {
        assertThrows(IllegalArgumentException.class, () -> f.registry.register(f.root));
        ScreenId<NavigationFixture.State, Params.Empty> duplicate =
                new ScreenId<>(f.root.route(), Params.Empty::fromArgs);
        assertThrows(IllegalArgumentException.class, () -> f.registry.register(duplicate));
        assertSame(f.root, f.registry.require(f.root.route()));
        assertThrows(IllegalArgumentException.class, () -> f.registry.require("missing"));
        assertEquals(f.edit("input"), f.registry.restore(f.edit("input").save()));
    }

    @Test
    public void routesAndDecodersAreValidated() {
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenId<>(" ", Params.Empty::fromArgs));
        assertThrows(IllegalArgumentException.class,
                () -> new ScreenId<>(" settings", Params.Empty::fromArgs));
        assertThrows(NullPointerException.class,
                () -> new ScreenId<NavigationFixture.State, Params.Empty>("settings", null));
        assertThrows(NullPointerException.class, () -> f.root.key(null));
        assertThrows(NullPointerException.class, () -> f.edit.restore(null));
    }

    @Test
    public void routesPreserveUnicodeBlankAndTrimValidation() {
        for (String route : Arrays.asList("", "\t\r\n", "\u2003", "\u3000", "\u2003\t\u3000",
                "settings ", "\tsettings")) {
            assertThrows(IllegalArgumentException.class,
                    () -> new ScreenId<>(route, Params.Empty::fromArgs));
        }
        assertThrows(NullPointerException.class,
                () -> new ScreenId<>(null, Params.Empty::fromArgs));
        for (String route : Arrays.asList("settings", "settings theme", "\u00a0", "\u2007", "\u202f",
                "\u2003settings\u2003", "\ud801\udc00")) {
            assertEquals(route, new ScreenId<>(route, Params.Empty::fromArgs).route());
        }
    }

    @Test
    public void restoreRejectsLossyOrNullDecoders() {
        ScreenId<NavigationFixture.State, Params.Empty> lossy =
                new ScreenId<>("lossy", args -> Params.Empty.INSTANCE);
        assertThrows(IllegalArgumentException.class, () -> lossy.restore(Params.Args.of("lost", "value")));
        ScreenId<NavigationFixture.State, Params.Empty> broken =
                new ScreenId<>("broken", args -> null);
        assertThrows(NullPointerException.class, () -> broken.restore(Params.Args.empty()));
    }
}
