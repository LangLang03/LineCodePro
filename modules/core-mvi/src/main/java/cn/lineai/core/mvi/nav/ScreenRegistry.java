package cn.lineai.core.mvi.nav;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owned by the app graph, never global. Route registration is unique and append-only. */
public final class ScreenRegistry {
    private final Map<String, ScreenId<?, ?>> screens = new LinkedHashMap<>();

    public synchronized ScreenRegistry register(ScreenId<?, ?> id) {
        Objects.requireNonNull(id, "id");
        if (screens.containsKey(id.route())) {
            throw new IllegalArgumentException("Duplicate screen route: " + id.route());
        }
        screens.put(id.route(), id);
        return this;
    }

    public synchronized ScreenId<?, ?> require(String route) {
        ScreenId<?, ?> id = screens.get(Objects.requireNonNull(route, "route"));
        if (id == null) {
            throw new IllegalArgumentException("Unknown screen route: " + route);
        }
        return id;
    }

    void requireRegistered(ScreenId<?, ?> id) {
        Objects.requireNonNull(id, "id");
        if (require(id.route()) != id) {
            throw new IllegalArgumentException("ScreenId is not the registered instance: " + id.route());
        }
    }

    public ScreenKey<?, ?> restore(Params.Args saved) {
        Objects.requireNonNull(saved, "saved");
        if (saved.asMap().size() != 2) {
            throw new IllegalArgumentException("Saved entry must contain only route and params");
        }
        return require(saved.requireString("route")).restore(saved.requireArgs("params"));
    }
}
