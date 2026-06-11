package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;

public final class ExperimentalSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public ExperimentalSettingsScreenView(Context context, Listener listener) {
        super(context, "实验性功能", listener::onBack, null);
        LinearLayout content = getContent();

        SettingsSectionView chat = new SettingsSectionView(context, "对话");
        chat.addRow(new SwitchRowView(context, IconButtonView.MESSAGE_SQUARE_TEXT, "启动时进入上一次对话", "默认关闭。关闭时每次打开应用进入新对话，可从侧边栏手动选择历史对话", false), false);
        content.addView(chat, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView system = new SettingsSectionView(context, "系统兼容");
        system.addRow(new SwitchRowView(context, IconButtonView.SMARTPHONE, "实验性键盘避让", "默认关闭，优先使用系统输入法避让。开启后使用旧版自定义键盘测量方案", false), false);
        content.addView(system, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView render = new SettingsSectionView(context, "渲染");
        render.addRow(new SwitchRowView(context, IconButtonView.SQUARE_FUNCTION, "数学公式渲染", "默认关闭，开启后解析 LaTeX 公式", false), false);
        content.addView(render, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
