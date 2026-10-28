package cn.lineai.feature.settings;

import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.Action;

public interface SettingsAction extends Action {
    final class Load implements SettingsAction { }
    final class Reload implements SettingsAction { }
    final class Loaded implements SettingsAction {
        public final UiPreferences values;
        public Loaded(UiPreferences values) { this.values = values; }
    }
    final class Change implements SettingsAction {
        public final Field field;
        public final String value;
        public Change(Field field, String value) { this.field = field; this.value = value; }
    }
    final class Saved implements SettingsAction {
        public final Field field;
        public final String value;
        public Saved(Field field, String value) { this.field = field; this.value = value; }
    }
    final class Failed implements SettingsAction {
        public final ErrorCode error;
        public Failed(ErrorCode error) { this.error = error; }
    }
}
