package cn.lineai.core.mvi.nav;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Owned by the app graph, never global. Route registration is unique and append-only. */
public final class ScreenRegistry {
    private final Map<String, ScreenId<?, ?>> screens = new LinkedHashMap<>();

    /**
     * Adds {@code id} to the graph.
     * @param id identity to register, never null
     * @return this registry for chaining
     * @throws IllegalArgumentException when the route is already registered
     */
    public synchronized ScreenRegistry register(ScreenId<?, ?> id) {
        Objects.requireNonNull(id, "id");
        if (screens.containsKey(id.route())) {
            throw new IllegalArgumentException("Duplicate screen route: " + id.route());
        }
        screens.put(id.route(), id);
        return this;
    }

    /**
     * Returns the registered identity for {@code route}.
     * @param route route to look up, never null
     * @return the registered identity
     * @throws IllegalArgumentException when the route is unknown
     */
    public synchronized ScreenId<?, ?> require(String route) {
        ScreenId<?, ?> id = screens.get(Objects.requireNonNull(route, "route"));
        if (id == null) {
            throw new IllegalArgumentException("Unknown screen route: " + route);
        }
        return id;
    }

    /**
     * Verifies that {@code id} is the registered instance for its route.
     * @param id identity to check, never null
     * @throws IllegalArgumentException when the route is unknown or the instance differs
     */
    void requireRegistered(ScreenId<?, ?> id) {
        Objects.requireNonNull(id, "id");
        if (require(id.route()) != id) {
            throw new IllegalArgumentException("ScreenId is not the registered instance: " + id.route());
        }
    }

    /**
     * Decodes one saved entry produced by {@link ScreenKey#save()}.
     * @param saved arguments holding exactly route and params, never null
     * @return the restored stack key
     * @throws IllegalArgumentException when the entry is malformed or params fail to round-trip
     */
    public ScreenKey<?, ?> restore(Params.Args saved) {
        Objects.requireNonNull(saved, "saved");
        if (saved.asMap().size() != 2) {
            throw new IllegalArgumentException("Saved entry must contain only route and params");
        }
        return require(saved.requireString("route")).restore(saved.requireArgs("params"));
    }
}
