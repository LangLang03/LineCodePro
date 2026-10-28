package cn.lineai.core.mvi.nav;

import cn.lineai.core.mvi.present.UiState;

import java.util.Objects;
import java.util.function.Function;

public final class ScreenId<S extends UiState, P extends Params> {
    private final String route;
    private final ScreenId<?, ?> parent;
    private final boolean singleTop;
    private final Function<Params.Args, P> decoder;

    public ScreenId(String route, Function<Params.Args, P> decoder) {
        this(route, null, false, decoder);
    }

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

    public String route() {
        return route;
    }

    public ScreenId<?, ?> parent() {
        return parent;
    }

    public boolean singleTop() {
        return singleTop;
    }

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
