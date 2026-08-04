package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;

import android.content.Context;
import android.os.Build;
import android.os.PowerManager;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.KeepAliveSettings;

public final class KeepAliveSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onSettingsChanged();
        KeepAliveSettings onLoadSettings();
        void onSetWakeLockEnabled(boolean enabled);
        void onSetForegroundEnabled(boolean enabled);
        void onSetFakeAudioEnabled(boolean enabled);
        void onUpdateService();
        void onUpdateServiceStatus(String status);
        void onRequestIgnoreBatteryOptimizations();
    }

    private final Context context;
    private final Listener listener;
    private final PermissionUiHelper permissionUiHelper;

    public KeepAliveSettingsScreenView(Context context, Listener listener) {
        this(context, listener, null);
    }

    public KeepAliveSettingsScreenView(Context context, Listener listener, PermissionUiHelper permissionUiHelper) {
        super(context, context.getString(R.string.screen_keep_alive_title), listener::onBack, null);
        this.context = context;
        this.listener = listener;
        this.permissionUiHelper = permissionUiHelper;
        LinearLayout content = getContent();

        KeepAliveSettings settings = listener.onLoadSettings();

        SettingsSectionView coding = new SettingsSectionView(context, context.getString(R.string.screen_keep_alive_section_coding));

        SwitchRowView wakeLockSwitch = new SwitchRowView(context, IconButtonView.ZAP, context.getString(R.string.screen_keep_alive_wake_lock_label), context.getString(R.string.screen_keep_alive_wake_lock_desc), settings.wakeLockEnabled, (buttonView, enabled) -> {
            listener.onSetWakeLockEnabled(enabled);
            listener.onUpdateService();
            listener.onSettingsChanged();
        });
        coding.addRow(wakeLockSwitch, true);

        SwitchRowView foregroundSwitch = new SwitchRowView(context, IconButtonView.BELL, context.getString(R.string.screen_keep_alive_foreground_label), context.getString(R.string.screen_keep_alive_foreground_desc), settings.foregroundEnabled, (buttonView, enabled) -> {
            listener.onSetForegroundEnabled(enabled);
            requestNotificationPermissionIfNeeded(enabled);
            listener.onUpdateService();
            listener.onSettingsChanged();
        });
        coding.addRow(foregroundSwitch, true);

        SwitchRowView fakeAudioSwitch = new SwitchRowView(context, IconButtonView.MUSIC, context.getString(R.string.screen_keep_alive_fake_music_label), context.getString(R.string.screen_keep_alive_fake_music_desc), settings.fakeAudioEnabled, (buttonView, enabled) -> {
            listener.onSetFakeAudioEnabled(enabled);
            requestNotificationPermissionIfNeeded(enabled);
            listener.onUpdateService();
            listener.onSettingsChanged();
        });
        coding.addRow(fakeAudioSwitch, false);
        content.addView(coding, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, context.getString(R.string.screen_keep_alive_section_system));
        SwitchRowView batterySwitch = new SwitchRowView(context, IconButtonView.BATTERY_CHARGING, context.getString(R.string.screen_keep_alive_ignore_battery_label), context.getString(R.string.screen_keep_alive_ignore_battery_desc), isIgnoringBatteryOptimizations(), (buttonView, enabled) -> {
            if (enabled && !isIgnoringBatteryOptimizations()) {
                listener.onRequestIgnoreBatteryOptimizations();
            }
        });
        system.addRow(batterySwitch, false);
        content.addView(system, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private boolean isIgnoringBatteryOptimizations() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            PowerManager pm = (PowerManager) context.getSystemService(Context.POWER_SERVICE);
            if (pm != null) {
                return pm.isIgnoringBatteryOptimizations(context.getPackageName());
            }
        }
        return true;
    }

    private void requestNotificationPermissionIfNeeded(boolean enabled) {
        if (!enabled || permissionUiHelper == null || permissionUiHelper.hasPostNotificationsPermission()) {
            return;
        }
        permissionUiHelper.requestPostNotificationsPermission();
        Toast.makeText(context, R.string.screen_keep_alive_notification_permission_hint, Toast.LENGTH_SHORT).show();
    }

    public void updateStatus(String status) {
        listener.onUpdateServiceStatus(status);
    }
}
