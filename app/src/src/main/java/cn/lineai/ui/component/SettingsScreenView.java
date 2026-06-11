package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class SettingsScreenView extends LinearLayout {
    public interface Listener {
        void onBack();

        void onItem(String id);
    }

    private final Listener listener;

    public SettingsScreenView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, "设置", listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 0, 0, 0, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        addSection(content, "AI 与模型", new RowSpec[] {
                new RowSpec("models", "模型管理", "供应商、密钥、模型 ID 和默认模型", IconButtonView.BOX),
                new RowSpec("llm", "AI 行为", "思考强度、交流语气、提示词和 reasoning 保留", IconButtonView.BRAIN),
        });
        addSection(content, "工具与执行", new RowSpec[] {
                new RowSpec("mcp", "工具与执行", "工作区工具开关、SSH 执行和本地执行目标", IconButtonView.MCP),
                new RowSpec("toolSettings", "工具设置", "图片理解模型和搜索 API", IconButtonView.SLIDERS_HORIZONTAL),
                new RowSpec("extensions", "扩展", "Agent、MCP、Skills 和 LineCode 扩展", IconButtonView.PACKAGE),
        });
        addSection(content, "界面与输出", new RowSpec[] {
                new RowSpec("input", "输入设置", "回车键发送或换行", IconButtonView.MESSAGE_SQUARE_TEXT),
                new RowSpec("theme", "主题与外观", "主题模式、自定义颜色和高对比外观", IconButtonView.PALETTE),
                new RowSpec("output", "输出与浏览", "代码换行、网页打开方式和内置浏览器 JS", IconButtonView.MONITOR),
                new RowSpec("experimental", "实验性渲染", "仍在验证的消息渲染能力", IconButtonView.FLASK_CONICAL),
        });
        addSection(content, "数据与系统", new RowSpec[] {
                new RowSpec("storage", "存储管理", "聊天、配置、diff 和工作区占用", IconButtonView.DATABASE),
                new RowSpec("memory", "记忆", "查看和添加长期记忆、项目记忆、短期记忆", IconButtonView.BOOK_OPEN),
                new RowSpec("data", "数据管理", "完整导出、.linecode 导入和数据归档", IconButtonView.ARCHIVE),
                new RowSpec("keepAlive", "后台保活", "Wake Lock、前台服务和电池白名单", IconButtonView.BATTERY_CHARGING),
        });
        addSection(content, "信息", new RowSpec[] {
                new RowSpec("about", "关于 LineCode", "版本、诊断和开源许可", IconButtonView.CPU),
        });
    }

    private void addSection(LinearLayout content, String title, RowSpec[] rows) {
        Context context = getContext();
        TextView sectionTitle = LineTheme.textMedium(context, title.toUpperCase(java.util.Locale.ROOT), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY);
        sectionTitle.setLetterSpacing(0.05f);
        LineTheme.padding(sectionTitle, LineTheme.LG, 0, LineTheme.LG, 0);
        LinearLayout.LayoutParams sectionParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        sectionParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        sectionParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(sectionTitle, sectionParams);

        LinearLayout group = new LinearLayout(context);
        group.setOrientation(VERTICAL);
        group.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        groupParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        groupParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        content.addView(group, groupParams);

        for (int i = 0; i < rows.length; i++) {
            group.addView(rowView(rows[i], i < rows.length - 1), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
    }

    private View rowView(RowSpec row, boolean divider) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(HORIZONTAL);
        item.setGravity(Gravity.CENTER_VERTICAL);
        item.setClickable(true);
        item.setOnClickListener(v -> listener.onItem(row.id));
        LineTheme.padding(item, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));
        IconButtonView icon = new IconButtonView(context, row.icon);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(36, 20);
        icon.setClickable(false);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36), Gravity.CENTER));
        item.addView(iconWrap, new LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 36)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        labelParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        item.addView(labels, labelParams);

        TextView label = LineTheme.textMedium(context, row.label, LineTheme.FONT_MD, LineTheme.TEXT);
        labels.addView(label, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView desc = LineTheme.text(context, row.desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, 2);
        labels.addView(desc, descParams);

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(20, 16);
        chevron.setClickable(false);
        item.addView(chevron, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        if (!divider) {
            return item;
        }

        LinearLayout wrapper = new LinearLayout(context);
        wrapper.setOrientation(VERTICAL);
        wrapper.addView(item, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        View line = new View(context);
        line.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams lineParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1);
        lineParams.leftMargin = LineTheme.dp(context, 68);
        wrapper.addView(line, lineParams);
        return wrapper;
    }

    private static final class RowSpec {
        final String id;
        final String label;
        final String desc;
        final int icon;

        RowSpec(String id, String label, String desc, int icon) {
            this.id = id;
            this.label = label;
            this.desc = desc;
            this.icon = icon;
        }
    }
}
