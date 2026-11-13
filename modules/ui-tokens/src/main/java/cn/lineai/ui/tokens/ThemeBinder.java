package cn.lineai.ui.tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

/**
 * Replays token snapshots to a fixed set of style bindings.
 *
 * <p>Binding happens eagerly: registering a consumer immediately applies the current snapshot, so
 * a bound view is styled before it is shown. {@link #apply} then restyles everything in place on
 * every theme swap. The binder is not thread-safe and is meant for the main thread only.</p>
 */
public final class ThemeBinder {

    private final List<Consumer<Tokens>> bindings = new ArrayList<>();
    private Tokens current;

    /**
     * Creates the binder with an initial snapshot.
     *
     * @param initial token snapshot applied to every future binding on registration
     */
    public ThemeBinder(Tokens initial) {
        current = initial;
    }

    /**
     * Registers a style binding and immediately applies the current snapshot to it.
     *
     * @param binding consumer receiving each token snapshot
     */
    public void bind(Consumer<Tokens> binding) {
        bindings.add(binding);
        binding.accept(current);
    }

    /**
     * Returns the active snapshot.
     *
     * @return the token snapshot currently held by this binder
     */
    public Tokens current() {
        return current;
    }

    /**
     * Replaces the snapshot and restyles every registered binding in place.
     *
     * @param next token snapshot to apply to all bindings
     */
    public void apply(Tokens next) {
        current = next;
        for (Consumer<Tokens> binding : bindings) {
            binding.accept(next);
        }
    }
}
