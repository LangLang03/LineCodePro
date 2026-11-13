package cn.lineai.core.service;

import cn.lineai.core.kernel.error.ErrorCode;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.Action;

/** Actions driving the settings screen: load, change, persist and report failures. */
public interface SettingsAction extends Action {
    /** Requests preferences from storage. */
    final class Load implements SettingsAction { }
    /** Requests a reload after external changes. */
    final class Reload implements SettingsAction { }
    /** Carries the preferences read from storage. */
    final class Loaded implements SettingsAction {
        public final UiPreferences values;
        /**
         * Creates the result of a successful load.
         * @param values validated preference values
         */
        public Loaded(UiPreferences values) { this.values = values; }
    }
    /** Requests a preference change; the value must pass {@link Field#accepts(String)}. */
    final class Change implements SettingsAction {
        public final Field field;
        public final String value;
        /**
         * Creates a change request.
         * @param field preference to change
         * @param value candidate value
         */
        public Change(Field field, String value) { this.field = field; this.value = value; }
    }
    /** Confirms that a change was persisted to storage. */
    final class Saved implements SettingsAction {
        public final Field field;
        public final String value;
        /**
         * Creates a persistence confirmation.
         * @param field preference that was written
         * @param value value that was written
         */
        public Saved(Field field, String value) { this.field = field; this.value = value; }
    }
    /** Reports a storage failure while loading or saving. */
    final class Failed implements SettingsAction {
        public final ErrorCode error;
        /**
         * Creates a failure report.
         * @param error machine-readable error code
         */
        public Failed(ErrorCode error) { this.error = error; }
    }
}
