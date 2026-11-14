package cn.lineai.feature.settings;

import cn.lineai.core.service.SettingsAction;
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

    /** Host callbacks reflecting the real system state behind the switches. */
    public interface Listener {
        /**
         * Returns whether the app is currently exempt from battery optimizations.
         *
         * @return true when the exemption is granted
         */
        boolean ignoringBatteryOptimizations();

        /** Requests the battery optimization exemption via the system dialog. */
        void onRequestBatteryOptimizationExemption();

        /** Notifies the host that a keep-alive service toggle changed so services can reapply. */
        void onServiceChanged();
    }

    private final PreferenceRow wakeLock;
    private final PreferenceRow foreground;
    private final PreferenceRow fakeAudio;
    private final PreferenceRow battery;
    private final Listener listener;

    /**
     * Builds the keep-alive page with its service and battery sections.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param dispatch consumer receiving settings actions for the service switches
     * @param listener host callbacks for battery state and service changes
     */
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

    /** Adds a service switch row that dispatches the change and notifies the host. */
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

    /**
     * Syncs all switches to the given state and the real battery exemption; rows stay disabled
     * until preferences load.
     *
     * @param state current settings state from the presenter
     */
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

    /** Re-reads the battery exemption state on every show, since the system dialog may change it. */
    @Override public void onShown() {
        battery.checked(listener.ignoringBatteryOptimizations());
    }
}
