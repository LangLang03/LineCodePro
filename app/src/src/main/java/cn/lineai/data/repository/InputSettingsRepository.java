package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.InputSettings;

public final class InputSettingsRepository {
    public static final String KEY_ENTER_KEY_BEHAVIOR = "@lineai_enter_key_behavior";

    private final SettingsRepository settingsRepository;

    public InputSettingsRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized InputSettings get() {
        return new InputSettings(settingsRepository.getString(KEY_ENTER_KEY_BEHAVIOR, InputSettings.ENTER_SEND));
    }

    public synchronized void setEnterKeyBehavior(String behavior) {
        settingsRepository.setString(KEY_ENTER_KEY_BEHAVIOR, InputSettings.normalizeEnterKeyBehavior(behavior));
    }
}
