package cn.lineai.feature.settings;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.Effect;
import cn.lineai.core.mvi.present.Emitter;
import cn.lineai.core.mvi.present.Presenter;
import cn.lineai.core.mvi.present.Transition;
import cn.lineai.data.api.SettingsStore;

public final class SettingsPresenter implements Presenter<SettingsState, SettingsAction> {
    private final SettingsStore settings;

    public SettingsPresenter(SettingsStore settings) { this.settings = settings; }

    @Override public SettingsState initial() {
        UiPreferences defaults = UiPreferences.defaults();
        return new SettingsState(defaults, defaults, null, false, null);
    }

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

    private static final class Load implements Effect { }
    private static final class Save implements Effect {
        final Field field;
        final String value;
        Save(Field field, String value) { this.field = field; this.value = value; }
    }
}
