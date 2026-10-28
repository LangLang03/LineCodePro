package cn.lineai.feature.settings;

import android.content.Context;

import java.util.function.Consumer;

import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** Wake lock, foreground service, silent audio and battery whitelist switches. */
public final class KeepAliveSettingsPage extends SettingsPage implements Refreshable {

    public interface Listener {
        boolean ignoringBatteryOptimizations();

        void onRequestBatteryOptimizationExemption();

        void onServiceChanged();
    }

    private final PreferenceRow wakeLock;
    private final PreferenceRow foreground;
    private final PreferenceRow fakeAudio;
    private final PreferenceRow battery;
    private final Listener listener;

    public KeepAliveSettingsPage(Context context, Tokens tokens, Runnable back,
                                 Consumer<SettingsAction> dispatch, Listener listener) {
        super(context, R.string.screen_keep_alive_title, tokens, back);
        this.listener = listener;
        SettingsSection coding = section(R.string.screen_keep_alive_section_coding);
        wakeLock = toggle(coding, Icons.ZAP, R.string.screen_keep_alive_wake_lock_label,
                R.string.screen_keep_alive_wake_lock_desc, Field.KEEP_ALIVE_WAKE_LOCK, dispatch, true);
        foreground = toggle(coding, Icons.BELL, R.string.screen_keep_alive_foreground_label,
                R.string.screen_keep_alive_foreground_desc, Field.KEEP_ALIVE_FOREGROUND, dispatch, true);
        fakeAudio = toggle(coding, Icons.MUSIC, R.string.screen_keep_alive_fake_music_label,
                R.string.screen_keep_alive_fake_music_desc, Field.KEEP_ALIVE_FAKE_AUDIO, dispatch, false);

        battery = new PreferenceRow(context, theme, PreferenceRow.Style.SWITCH, Icons.BATTERY_CHARGING,
                R.string.screen_keep_alive_ignore_battery_label,
                R.string.screen_keep_alive_ignore_battery_desc);
        battery.onToggle(value -> {
            if (value && !listener.ignoringBatteryOptimizations()) {
                listener.onRequestBatteryOptimizationExemption();
            }
            battery.checked(listener.ignoringBatteryOptimizations());
        });
        section(R.string.screen_keep_alive_section_system).row(battery, false, 0);
    }

    private PreferenceRow toggle(SettingsSection section, int icon, int label, int description,
                                 Field field, Consumer<SettingsAction> dispatch, boolean divider) {
        PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.SWITCH,
                icon, label, description);
        row.onToggle(value -> {
            dispatch.accept(new SettingsAction.Change(field, value.toString()));
            listener.onServiceChanged();
        });
        section.row(row, divider, 0);
        return row;
    }

    public void render(SettingsState state) {
        wakeLock.checked(state.values.keepAliveWakeLock());
        foreground.checked(state.values.keepAliveForeground());
        fakeAudio.checked(state.values.keepAliveFakeAudio());
        battery.checked(listener.ignoringBatteryOptimizations());
        wakeLock.setEnabled(state.loaded);
        foreground.setEnabled(state.loaded);
        fakeAudio.setEnabled(state.loaded);
        battery.setEnabled(state.loaded);
    }

    @Override public void onShown() {
        battery.checked(listener.ignoringBatteryOptimizations());
    }
}
