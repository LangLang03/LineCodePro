package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;

public final class KeepAliveSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public KeepAliveSettingsScreenView(Context context, Listener listener) {
        super(context, "保活设置", listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView coding = new SettingsSectionView(context, "编码任务保活");
        coding.addRow(new SwitchRowView(context, IconButtonView.ZAP, "Wake Lock", "对话生成和压缩时保持 CPU 与屏幕唤醒", true), true);
        coding.addRow(new SwitchRowView(context, IconButtonView.BELL, "前台服务通知", "开启后常驻显示“正在编码”通知", false), true);
        coding.addRow(new SwitchRowView(context, IconButtonView.MUSIC, "假音乐播放", "后台任务期间启动静音 AudioTrack", false), false);
        content.addView(coding, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, "系统白名单");
        system.addRow(new SwitchRowView(context, IconButtonView.BATTERY_CHARGING, "忽略电池优化", "打开 Android 白名单申请页面", false), false);
        content.addView(system, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
