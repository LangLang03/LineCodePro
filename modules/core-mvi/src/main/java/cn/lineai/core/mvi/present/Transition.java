package cn.lineai.core.mvi.present;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

public final class Transition<S extends UiState> {
    private final S state;
    private final List<Effect> effects;

    private Transition(S state, List<Effect> effects) {
        this.state = Objects.requireNonNull(state, "state");
        List<Effect> snapshot = new ArrayList<>(effects);
        for (Effect effect : snapshot) {
            Objects.requireNonNull(effect, "effect");
        }
        this.effects = Collections.unmodifiableList(snapshot);
    }

    public static <S extends UiState> Transition<S> state(S state) {
        return new Transition<>(state, Collections.emptyList());
    }

    public static <S extends UiState> Transition<S> with(S state, Effect... effects) {
        Objects.requireNonNull(effects, "effects");
        return new Transition<>(state, Arrays.asList(effects));
    }

    public S state() {
        return state;
    }

    public List<Effect> effects() {
        return effects;
    }
}
