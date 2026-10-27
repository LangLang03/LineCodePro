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

    ScreenKey(ScreenId<S, P> id, P params) {
        this.id = Objects.requireNonNull(id, "id");
        this.params = Objects.requireNonNull(params, "params");
        this.args = Objects.requireNonNull(params.toArgs(), "params args");
    }

    public ScreenId<S, P> id() {
        return id;
    }

    public P params() {
        return params;
    }

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

    @Override
    public boolean equals(Object other) {
        if (!(other instanceof ScreenKey<?, ?>)) {
            return false;
        }
        ScreenKey<?, ?> key = (ScreenKey<?, ?>) other;
        return id == key.id && args.equals(key.args);
    }

    @Override
    public int hashCode() {
        return 31 * id.hashCode() + args.hashCode();
    }

    @Override
    public String toString() {
        return id.route() + args;
    }
}
