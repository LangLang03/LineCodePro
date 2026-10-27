package cn.lineai.runtime;

import cn.lineai.data.api.SettingsStore;
import cn.lineai.engine.prompt.PromptOverrides;

final class SettingsPromptOverrides implements PromptOverrides {
    private final SettingsStore settings;

    SettingsPromptOverrides(SettingsStore settings) {
        this.settings = settings;
    }

    @Override public String get(String id, String fallback) {
        return settings.get(id, fallback);
    }

    @Override public void put(String id, String value) {
        settings.put(id, value);
    }

    @Override public void remove(String id) {
        settings.remove(id);
    }
}
