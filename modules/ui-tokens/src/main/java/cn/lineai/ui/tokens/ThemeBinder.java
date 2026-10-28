package cn.lineai.ui.tokens;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

public final class ThemeBinder {
    private final List<Consumer<Tokens>> bindings = new ArrayList<>();
    private Tokens current;

    public ThemeBinder(Tokens initial) {
        current = initial;
    }

    public void bind(Consumer<Tokens> binding) {
        bindings.add(binding);
        binding.accept(current);
    }

    public Tokens current() {
        return current;
    }

    public void apply(Tokens next) {
        current = next;
        for (Consumer<Tokens> binding : bindings) {
            binding.accept(next);
        }
    }
}
