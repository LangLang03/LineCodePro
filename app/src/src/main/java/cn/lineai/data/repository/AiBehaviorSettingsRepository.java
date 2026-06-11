package cn.lineai.data.repository;

import android.content.Context;
import cn.lineai.model.AiBehaviorSettings;

public final class AiBehaviorSettingsRepository {
    public static final String KEY_TONE = "@lineai_tone";
    public static final String KEY_THINKING_SCROLL = "@lineai_thinking_scroll";
    public static final String KEY_THINKING_AUTO_EXPAND = "@lineai_thinking_auto_expand";
    public static final String KEY_REASONING_EFFORT = "@lineai_reasoning_effort";
    public static final String KEY_PRESERVE_REASONING = "@lineai_preserve_reasoning";
    public static final String KEY_LEARNING_MODE = "@linecode_learning_mode_enabled";

    private final SettingsRepository settingsRepository;

    public AiBehaviorSettingsRepository(Context context) {
        settingsRepository = new SettingsRepository(context);
    }

    public synchronized AiBehaviorSettings get() {
        return new AiBehaviorSettings(
                settingsRepository.getString(KEY_TONE, AiBehaviorSettings.TONE_CODING),
                settingsRepository.getBoolean(KEY_THINKING_SCROLL, true),
                settingsRepository.getBoolean(KEY_THINKING_AUTO_EXPAND, false),
                settingsRepository.getString(KEY_REASONING_EFFORT, AiBehaviorSettings.REASONING_MEDIUM),
                settingsRepository.getBoolean(KEY_PRESERVE_REASONING, false),
                settingsRepository.getBoolean(KEY_LEARNING_MODE, false)
        );
    }

    public synchronized void setToneMode(String value) {
        settingsRepository.setString(KEY_TONE, AiBehaviorSettings.normalizeTone(value));
    }

    public synchronized void setThinkingScrollEnabled(boolean value) {
        settingsRepository.setBoolean(KEY_THINKING_SCROLL, value);
    }

    public synchronized void setThinkingAutoExpandEnabled(boolean value) {
        settingsRepository.setBoolean(KEY_THINKING_AUTO_EXPAND, value);
    }

    public synchronized void setReasoningEffort(String value) {
        settingsRepository.setString(KEY_REASONING_EFFORT, AiBehaviorSettings.normalizeReasoningEffort(value));
    }

    public synchronized void setPreserveReasoningEnabled(boolean value) {
        settingsRepository.setBoolean(KEY_PRESERVE_REASONING, value);
    }

    public synchronized void setLearningModeEnabled(boolean value) {
        settingsRepository.setBoolean(KEY_LEARNING_MODE, value);
    }
}
