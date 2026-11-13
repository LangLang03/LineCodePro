package cn.lineai.feature.settings;

import android.content.Context;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;
import java.util.function.Consumer;

/**
 * Landing page of the settings feature, listing the available settings destinations as
 * navigation groups.
 */
public final class SettingsHome extends SettingsPage {
    /** Destinations reachable from the settings home screen. */
    public enum Destination {
        TUTORIAL, MODELS, BEHAVIOR, MCP, TOOLS, EXTENSIONS, ADVANCED,
        INPUT, THEME, OUTPUT, SECURITY, STORAGE, MEMORY, DATA, LOGS, KEEP_ALIVE, ABOUT
    }

    private final Consumer<Destination> navigate;

    /**
     * Builds the settings home screen.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param navigate consumer notified with the chosen destination
     */
    public SettingsHome(Context context, Tokens tokens, Runnable back, Consumer<Destination> navigate) {
        super(context, R.string.screen_settings_title, tokens, back);
        this.navigate = navigate;
        PreferenceRow tutorial = row(new Item(Destination.TUTORIAL, Icons.SPARKLES,
                R.string.settings_row_tutorial_title, R.string.settings_row_tutorial_desc), PreferenceRow.Style.ACTION);
        content.addView(tutorial, new LayoutParams(-1, -2));
        ai();
        tools();
        appearance();
        group(R.string.screen_settings_section_security,
                new Item(Destination.SECURITY, Icons.SHIELD_CHECK, R.string.screen_settings_section_security, R.string.settings_row_security_desc));
        data();
        group(R.string.screen_settings_section_info,
                new Item(Destination.ABOUT, Icons.CPU, R.string.settings_row_about_title, R.string.settings_row_about_desc));
    }

    /** Adds the AI section with model and behavior destinations. */
    private void ai() {
        group(R.string.screen_settings_section_ai,
                new Item(Destination.MODELS, Icons.BOX, R.string.settings_row_models_title, R.string.settings_row_models_desc),
                new Item(Destination.BEHAVIOR, Icons.BRAIN, R.string.screen_llm_title, R.string.settings_row_llm_desc));
    }

    /** Adds the tools section with MCP, tool, extension, and advanced destinations. */
    private void tools() {
        group(R.string.screen_settings_section_tools,
                new Item(Destination.MCP, Icons.MCP, R.string.settings_row_mcp_title, R.string.settings_row_mcp_desc),
                new Item(Destination.TOOLS, Icons.SLIDERS_HORIZONTAL, R.string.settings_row_tool_settings_title, R.string.settings_row_tool_settings_desc),
                new Item(Destination.EXTENSIONS, Icons.PACKAGE, R.string.settings_row_extensions_title, R.string.settings_row_extensions_desc),
                new Item(Destination.ADVANCED, Icons.ZAP, R.string.settings_row_advanced_title, R.string.settings_row_advanced_desc));
    }

    /** Adds the appearance section with input, theme, and output destinations. */
    private void appearance() {
        group(R.string.screen_settings_section_ui,
                new Item(Destination.INPUT, Icons.MESSAGE_SQUARE_TEXT, R.string.screen_input_title, R.string.settings_row_input_desc),
                new Item(Destination.THEME, Icons.PALETTE, R.string.settings_row_theme_title, R.string.settings_row_theme_desc),
                new Item(Destination.OUTPUT, Icons.MONITOR, R.string.settings_row_output_title, R.string.settings_row_output_desc));
    }

    /** Adds the data section with storage, memory, data, logs, and keep-alive destinations. */
    private void data() {
        group(R.string.screen_settings_section_data,
                new Item(Destination.STORAGE, Icons.DATABASE, R.string.settings_row_storage_title, R.string.settings_row_storage_desc),
                new Item(Destination.MEMORY, Icons.BOOK_OPEN, R.string.settings_row_memory_title, R.string.settings_row_memory_desc),
                new Item(Destination.DATA, Icons.ARCHIVE, R.string.settings_row_data_title, R.string.settings_row_data_desc),
                new Item(Destination.LOGS, Icons.BUG, R.string.settings_row_error_logs_title, R.string.settings_row_error_logs_desc),
                new Item(Destination.KEEP_ALIVE, Icons.BATTERY_CHARGING, R.string.settings_row_keep_alive_title, R.string.settings_row_keep_alive_desc));
    }

    /**
     * Adds a titled section containing one navigation row per item.
     *
     * @param title string resource of the section heading
     * @param items destinations listed inside the section
     */
    private void group(int title, Item... items) {
        SettingsSection section = section(title);
        for (int i = 0; i < items.length; i++) {
            section.row(row(items[i], PreferenceRow.Style.NAVIGATION), i + 1 < items.length, 68);
        }
    }

    /**
     * Builds a row that navigates to the item destination when clicked.
     *
     * @param item destination descriptor
     * @param style visual style of the row
     * @return the configured row
     */
    private PreferenceRow row(Item item, PreferenceRow.Style style) {
        PreferenceRow row = new PreferenceRow(getContext(), theme, style, item.icon, item.title, item.description);
        row.setOnClickListener(v -> navigate.accept(item.destination));
        return row;
    }

    /** Immutable descriptor linking a settings row to its destination. */
    private static final class Item {
        final Destination destination;
        final int icon;
        final int title;
        final int description;
        Item(Destination destination, int icon, int title, int description) {
            this.destination = destination;
            this.icon = icon;
            this.title = title;
            this.description = description;
        }
    }
}
