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

public final class SettingsScreenView extends ScreenSurfaceView {
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
        LineTheme.padding(content, 0, 0, 0, 48);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        content.addView(new ActionRowView(context, IconButtonView.SPARKLES,
                context.getString(R.string.settings_row_tutorial_title),
                null,
                false, true,
                () -> listener.onItem("tutorialFromSettings")),
                tutorialParams(context));

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

    private LayoutParams tutorialParams(Context context) {
        LayoutParams p = new LayoutParams(-1, -2);p.leftMargin=p.rightMargin=LineTheme.dp(context,12);return p;
    }
    private void addSection(LinearLayout content, String title, RowSpec[] rows) {
        SettingsSectionView section = new SettingsSectionView(getContext(), title);
        for (RowSpec row : rows) {
            section.addRow(new ActionRowView(getContext(), row.icon, row.label, null,
                    false, true, () -> listener.onItem(row.id)), false);
        }
        content.addView(section, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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
