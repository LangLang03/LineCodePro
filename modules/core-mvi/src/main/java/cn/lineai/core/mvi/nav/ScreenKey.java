package cn.lineai.core.mvi.nav;

import cn.lineai.core.mvi.present.UiState;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Value identity is the registered ScreenId instance plus its serialized params. */
public final class ScreenKey<S extends UiState, P extends Params> {
    private final ScreenId<S, P> id;
    private final P params;
    private final Params.Args args;

    /**
     * Creates a key; construction is reserved for {@link ScreenId#key}.
     * @param id registered identity this key belongs to
     * @param params typed params for the screen
     */
    ScreenKey(ScreenId<S, P> id, P params) {
        this.id = Objects.requireNonNull(id, "id");
        this.params = Objects.requireNonNull(params, "params");
        this.args = Objects.requireNonNull(params.toArgs(), "params args");
    }

    /** Returns the registered screen identity. */
    public ScreenId<S, P> id() {
        return id;
    }

    /** Returns the decoded params instance. */
    public P params() {
        return params;
    }

    /** Returns the serialised params backing this key. */
    public Params.Args args() {
        return args;
    }

    /** Route lives outside the params map, so arbitrary parameter names are safe. */
    public Params.Args save() {
        Map<String, Object> saved = new LinkedHashMap<>();
        saved.put("route", id.route());
        saved.put("params", args.asMap());
        return new Params.Args(saved);
    }

    /** Keys are equal when the identity is the same instance and the args match. */
    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ScreenKey<?, ?>)) {
            return false;
        }
        ScreenKey<?, ?> key = (ScreenKey<?, ?>) other;
        return id == key.id && args.equals(key.args);
    }

    /** Combines the identity and args hashes. */
    @Override
    public int hashCode() {
        return 31 * id.hashCode() + args.hashCode();
    }

    /** Renders route plus args for diagnostics. */
    @Override
    public String toString() {
        return id.route() + args;
    }
}
