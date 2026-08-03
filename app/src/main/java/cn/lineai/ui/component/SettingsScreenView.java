package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;

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

        addView(new ScreenHeaderView(context, context.getString(R.string.screen_settings_title), listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 0, 0, 0, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(new ActionRowView(context, IconButtonView.SPARKLES,
                context.getString(R.string.settings_row_tutorial_title),
                context.getString(R.string.settings_row_tutorial_desc),
                false, true,
                () -> listener.onItem("tutorialFromSettings")),
                new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addSection(content, context.getString(R.string.screen_settings_section_ai), new RowSpec[] {
                new RowSpec("models", context.getString(R.string.settings_row_models_title), context.getString(R.string.settings_row_models_desc), IconButtonView.BOX),
                new RowSpec("llm", context.getString(R.string.screen_llm_title), context.getString(R.string.settings_row_llm_desc), IconButtonView.BRAIN),
        });
        addSection(content, context.getString(R.string.screen_settings_section_tools), new RowSpec[] {
                new RowSpec("mcp", context.getString(R.string.settings_row_mcp_title), context.getString(R.string.settings_row_mcp_desc), IconButtonView.MCP),
                new RowSpec("toolSettings", context.getString(R.string.settings_row_tool_settings_title), context.getString(R.string.settings_row_tool_settings_desc), IconButtonView.SLIDERS_HORIZONTAL),
                new RowSpec("extensions", context.getString(R.string.settings_row_extensions_title), context.getString(R.string.settings_row_extensions_desc), IconButtonView.PACKAGE),
                new RowSpec("advancedFeatures", context.getString(R.string.settings_row_advanced_title), context.getString(R.string.settings_row_advanced_desc), IconButtonView.ZAP),
        });
        addSection(content, context.getString(R.string.screen_settings_section_ui), new RowSpec[] {
                new RowSpec("input", context.getString(R.string.screen_input_title), context.getString(R.string.settings_row_input_desc), IconButtonView.MESSAGE_SQUARE_TEXT),
                new RowSpec("theme", context.getString(R.string.settings_row_theme_title), context.getString(R.string.settings_row_theme_desc), IconButtonView.PALETTE),
                new RowSpec("output", context.getString(R.string.settings_row_output_title), context.getString(R.string.settings_row_output_desc), IconButtonView.MONITOR),
        });
        addSection(content, context.getString(R.string.screen_settings_section_security), new RowSpec[] {
                new RowSpec("security", context.getString(R.string.screen_settings_section_security), context.getString(R.string.settings_row_security_desc), IconButtonView.SHIELD_CHECK),
        });
        addSection(content, context.getString(R.string.screen_settings_section_data), new RowSpec[] {
                new RowSpec("storage", context.getString(R.string.settings_row_storage_title), context.getString(R.string.settings_row_storage_desc), IconButtonView.DATABASE),
                new RowSpec("memory", context.getString(R.string.settings_row_memory_title), context.getString(R.string.settings_row_memory_desc), IconButtonView.BOOK_OPEN),
                new RowSpec("data", context.getString(R.string.settings_row_data_title), context.getString(R.string.settings_row_data_desc), IconButtonView.ARCHIVE),
                new RowSpec("errorLogs", context.getString(R.string.settings_row_error_logs_title), context.getString(R.string.settings_row_error_logs_desc), IconButtonView.BUG),
                new RowSpec("keepAlive", context.getString(R.string.settings_row_keep_alive_title), context.getString(R.string.settings_row_keep_alive_desc), IconButtonView.BATTERY_CHARGING),
        });
        addSection(content, context.getString(R.string.screen_settings_section_info), new RowSpec[] {
                new RowSpec("about", context.getString(R.string.settings_row_about_title), context.getString(R.string.settings_row_about_desc), IconButtonView.CPU),
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

        item.setOnClickListener(v -> listener.onItem(row.id));
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
