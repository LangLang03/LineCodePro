package cn.lineai.feature.settings;

import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.UiState;
import cn.lineai.core.kernel.error.ErrorCode;

public final class SettingsState implements UiState {
    public final UiPreferences values;
    public final UiPreferences saved;
    public final Field writing;
    public final boolean loaded;
    public final ErrorCode error;

    public SettingsState(UiPreferences values, UiPreferences saved, Field writing,
                         boolean loaded, ErrorCode error) {
        this.values = values;
        this.saved = saved;
        this.writing = writing;
        this.loaded = loaded;
        this.error = error;
    }
}
