package cn.lineai.runtime;

import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.prompt.PromptOverrides;

/** Persists prompt template overrides in the settings table, keyed by prompt id. */
final class SettingsPromptOverrides implements PromptOverrides {
    private final SettingsStore settings;

    /** Creates an adapter over the given settings store. */
    SettingsPromptOverrides(SettingsStore settings) {
        this.settings = settings;
    }

    /** Reads the override for a prompt id, falling back when none was saved. */
    @Override public String get(String id, String fallback) {
        return settings.get(id, fallback);
    }

    /** Writes or overwrites the override for a prompt id. */
    @Override public void put(String id, String value) {
        settings.put(id, value);
    }

    /** Deletes the override so the built-in template takes effect again. */
    @Override public void remove(String id) {
        settings.remove(id);
    }
}
