package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class StorageManagementScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public StorageManagementScreenView(Context context, Listener listener) {
        super(context, "存储管理", listener::onBack, refreshButton(context));
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout summary = new LinearLayout(context);
        summary.setOrientation(VERTICAL);
        summary.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(summary, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        TextView label = LineTheme.textMedium(context, "已统计使用量", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY);
        summary.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView value = LineTheme.text(context, "128 MB", LineTheme.FONT_XXL, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        valueParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        summary.addView(value, valueParams);
        TextView time = LineTheme.text(context, "本机 Java UI 预览数据", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams timeParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        timeParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        summary.addView(time, timeParams);
        LinearLayout.LayoutParams summaryParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        summaryParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(summary, summaryParams);

        addStorageRow(content, IconButtonView.GIT_COMPARE, "Diff 缓存", "工具调用生成的补丁和比较结果", "42 MB", "18 项");
        addStorageRow(content, IconButtonView.MESSAGE_SQUARE, "聊天记录", "对话、消息和索引摘要", "36 MB", "128 项");
        addStorageRow(content, IconButtonView.SETTINGS, "配置文件", "模型、主题、MCP 和系统设置", "4 MB", "12 项");
        addStorageRow(content, IconButtonView.FOLDER, "Home 目录", "项目文件、Skills 和扩展数据", "46 MB", "256 项");
    }

    private static View refreshButton(Context context) {
        return new RefreshCwButtonView(context, 18);
    }

    private void addStorageRow(LinearLayout content, int iconType, String title, String desc, String size, String count) {
        Context context = content.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(row, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 19));
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(38, 19);
        icon.setClickable(false);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38), Gravity.CENTER));
        row.addView(iconWrap, new LinearLayout.LayoutParams(LineTheme.dp(context, 38), LineTheme.dp(context, 38)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        row.addView(text, textParams);
        text.addView(LineTheme.text(context, title, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        text.addView(descView, descParams);

        LinearLayout meta = new LinearLayout(context);
        meta.setOrientation(VERTICAL);
        meta.setGravity(Gravity.END);
        meta.addView(LineTheme.text(context, size, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.BOLD), new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView countView = LineTheme.text(context, count, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        countParams.topMargin = LineTheme.dp(context, 2);
        meta.addView(countView, countParams);
        row.addView(meta, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        rowParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(row, rowParams);
    }
}
