package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class PluginPageScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public PluginPageScreenView(Context context, String title, Listener listener) {
        super(context, title == null ? "插件页面" : title, listener::onBack, null);
        LinearLayout content = getContent();
        content.setGravity(Gravity.CENTER);
        LineTheme.padding(content, LineTheme.XL, LineTheme.XL, LineTheme.XL, LineTheme.XL);
        TextView status = LineTheme.text(context, "正在打开插件页面...", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        content.addView(status, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView desc = LineTheme.text(context, "Java 原生版本保留 LineML 插件页面容器；后续接入插件运行时后在此渲染 document。", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        desc.setGravity(Gravity.CENTER);
        desc.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(desc, descParams);
    }
}
