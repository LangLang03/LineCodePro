package cn.lineai.feature.settings;

import cn.lineai.core.service.SettingsAction;
import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.Effect;
import cn.lineai.core.mvi.present.Emitter;
import cn.lineai.core.mvi.present.Presenter;
import cn.lineai.core.mvi.present.Transition;
import cn.lineai.data.api.SettingsStore;

/**
 * Presenter backing the generic settings screens.
 *
 * <p>It follows a serialized-write invariant: edits update the working values immediately, while
 * persistence happens one field at a time in the order the fields diverge from the saved snapshot.
 * A new edit is accepted while a write is in flight but queued until the current write completes.</p>
 */
public final class SettingsPresenter implements Presenter<SettingsState, SettingsAction> {
    private final SettingsStore settings;

    /**
     * Creates a presenter bound to the given settings store.
     *
     * @param settings backing key-value store used for loads and writes
     */
    public SettingsPresenter(SettingsStore settings) { this.settings = settings; }

    /** {@inheritDoc} Starts with factory-default preferences and an empty write queue. */
    @Override public SettingsState initial() {
        UiPreferences defaults = UiPreferences.defaults();
        return new SettingsState(defaults, defaults, null, false, null);
    }

    /**
     * Applies a settings action to the current state.
     *
     * <p>Reload and load actions trigger storage reads, change actions update working values, and
     * saved/failed actions settle the in-flight write. Edits received before the first successful
     * load are ignored so edits cannot clobber unloaded persisted values.</p>
     *
     * @param state current UI state
     * @param action incoming action
     * @return transition carrying the next state and, when applicable, an effect to run
     */
    @Override public Transition<SettingsState> reduce(SettingsState state, SettingsAction action) {
        if (action instanceof SettingsAction.Reload) {
            return Transition.with(new SettingsState(state.saved, state.saved, null, false, null),
                    new Load());
        }
        if (action instanceof SettingsAction.Load) {
            return state.loaded ? Transition.state(state) : Transition.with(state, new Load());
        }
        if (action instanceof SettingsAction.Loaded) {
            UiPreferences values = ((SettingsAction.Loaded) action).values;
            return Transition.state(new SettingsState(values, values, null, true, null));
        }
        if (action instanceof SettingsAction.Failed) {
            return Transition.state(new SettingsState(state.saved, state.saved, null,
                    state.loaded, ((SettingsAction.Failed) action).error));
        }
        if (!state.loaded) return Transition.state(state);
        if (action instanceof SettingsAction.Change) {
            SettingsAction.Change change = (SettingsAction.Change) action;
            UiPreferences values = state.values.with(change.field, change.value);
            return next(new SettingsState(values, state.saved, state.writing, true, null));
        }
        if (action instanceof SettingsAction.Saved) {
            SettingsAction.Saved saved = (SettingsAction.Saved) action;
            return next(new SettingsState(state.values, state.saved.with(saved.field, saved.value),
                    null, true, null));
        }
        return Transition.state(state);
    }

    /**
     * Advances the write queue: starts a save for the first field that diverges from the saved
     * snapshot, or settles the state once all writes have completed.
     *
     * @param state state with the updated working values
     * @return transition starting the next write, or a settled state transition
     */
    private Transition<SettingsState> next(SettingsState state) {
        if (state.writing != null) return Transition.state(state);
        for (Field field : Field.values()) {
            if (!state.values.get(field).equals(state.saved.get(field))) {
                SettingsState writing = new SettingsState(state.values, state.saved, field, true, null);
                return Transition.with(writing, new Save(field, state.values.get(field)));
            }
        }
        return Transition.state(state);
    }

    /**
     * Executes storage effects on the caller thread, emitting loaded or saved actions on success
     * and a failed action with the error code on a storage failure.
     *
     * @param effect effect to execute
     * @param out emitter for the resulting actions
     */
    @Override public void run(Effect effect, Emitter<SettingsAction> out) {
        try {
            if (effect instanceof Load) {
                out.emit(new SettingsAction.Loaded(UiPreferences.from(settings.all())));
            } else if (effect instanceof Save) {
                Save save = (Save) effect;
                settings.put(save.field.key, save.value);
                out.emit(new SettingsAction.Saved(save.field, save.value));
            }
        } catch (DomainException error) {
            out.emit(new SettingsAction.Failed(error.code()));
        }
    }

    /** Effect requesting a load of persisted preferences from storage. */
    private static final class Load implements Effect { }
    /** Effect persisting a single field value to storage. */
    private static final class Save implements Effect {
        /** Field being written. */
        final Field field;
        /** Serialized value to persist. */
        final String value;
        /** Creates a save effect for one field. */
        Save(Field field, String value) { this.field = field; this.value = value; }
    }
}
