package cn.lineai.core.mvi.nav;

import cn.lineai.core.mvi.present.UiState;

import java.util.Objects;
import java.util.function.Function;

/** Registered identity of a screen: route, hierarchy placement and params decoder. */
public final class ScreenId<S extends UiState, P extends Params> {
    private final String route;
    private final ScreenId<?, ?> parent;
    private final boolean singleTop;
    private final Function<Params.Args, P> decoder;

    /**
     * Creates a root-level identity without singleTop behaviour.
     * @param route unique route string, nonblank and unpadded
     * @param decoder rebuilds typed params from saved arguments
     */
    public ScreenId(String route, Function<Params.Args, P> decoder) {
        this(route, null, false, decoder);
    }

    /**
     * Creates a screen identity with full hierarchy options.
     * @param route unique route string, nonblank and unpadded
     * @param parent parent identity for back-stack semantics, null at the root
     * @param singleTop whether pushes onto the current top are collapsed
     * @param decoder rebuilds typed params from saved arguments
     * @throws IllegalArgumentException when the route is blank or padded
     */
    public ScreenId(String route, ScreenId<?, ?> parent, boolean singleTop,
                    Function<Params.Args, P> decoder) {
        this.route = Objects.requireNonNull(route, "route");
        if (isBlank(route) || !route.equals(route.trim())) {
            throw new IllegalArgumentException("Route must be nonblank without surrounding whitespace");
        }
        this.parent = parent;
        this.singleTop = singleTop;
        this.decoder = Objects.requireNonNull(decoder, "decoder");
    }

    /** Returns whether {@code value} contains only whitespace. */
    private static boolean isBlank(String value) {
        for (int offset = 0; offset < value.length();) {
            int codePoint = value.codePointAt(offset);
            if (!Character.isWhitespace(codePoint)) {
                return false;
            }
            offset += Character.charCount(codePoint);
        }
        return true;
    }

    /** Returns the unique route string. */
    public String route() {
        return route;
    }

    /** Returns the parent identity, or null at the root. */
    public ScreenId<?, ?> parent() {
        return parent;
    }

    /** Returns whether repeated pushes onto the current top are collapsed. */
    public boolean singleTop() {
        return singleTop;
    }

    /**
     * Builds a stack key from typed params.
     * @param params params instance for the screen, never null
     * @return a key usable for pushes and equality checks
     */
    public ScreenKey<S, P> key(P params) {
        return new ScreenKey<>(this, params);
    }

    /** Rejects lossy decoders, so restored identity is exactly the saved identity. */
    public ScreenKey<S, P> restore(Params.Args args) {
        Objects.requireNonNull(args, "args");
        ScreenKey<S, P> key = key(Objects.requireNonNull(decoder.apply(args), "decoded params"));
        if (!key.args().equals(args)) {
            throw new IllegalArgumentException("Params did not round-trip for route: " + route);
        }
        return key;
    }
}
