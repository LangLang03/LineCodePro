package cn.lineai.core.mvi.present;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/** The outcome of one reduction: the next state plus fire-and-forget effects. */
public final class Transition<S extends UiState> {
    private final S state;
    private final List<Effect> effects;

    /** Creates a transition with a defensive copy of {@code effects}. */
    private Transition(S state, List<Effect> effects) {
        this.state = Objects.requireNonNull(state, "state");
        List<Effect> snapshot = new ArrayList<>(effects);
        for (Effect effect : snapshot) {
            Objects.requireNonNull(effect, "effect");
        }
        this.effects = Collections.unmodifiableList(snapshot);
    }

    /**
     * Creates a transition that only changes state.
     * @param state next state, never null
     * @param <S> state type
     * @return an effect-free transition
     */
    public static <S extends UiState> Transition<S> state(S state) {
        return new Transition<>(state, Collections.emptyList());
    }

    /**
     * Creates a transition carrying effects to run after the state lands.
     * @param state next state, never null
     * @param effects effects to run in order, each never null
     * @param <S> state type
     * @return the transition
     */
    public static <S extends UiState> Transition<S> with(S state, Effect... effects) {
        Objects.requireNonNull(effects, "effects");
        return new Transition<>(state, Arrays.asList(effects));
    }

    /** Returns the next state to publish. */
    public S state() {
        return state;
    }

    /** Returns the effects to run after the state is published, in order. */
    public List<Effect> effects() {
        return effects;
    }
}
