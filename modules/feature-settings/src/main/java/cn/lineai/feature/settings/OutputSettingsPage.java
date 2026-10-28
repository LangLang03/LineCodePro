package cn.lineai.feature.settings;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;
import java.util.function.Consumer;

public final class OutputSettingsPage extends SettingsPage {
    private final PreferenceRow process;
    private final PreferenceRow wrap;
    private final PreferenceRow builtin;
    private final PreferenceRow external;
    private final MarkdownLite preview;
    private final String sample;
    private final Consumer<SettingsAction> dispatch;

    public OutputSettingsPage(Context context, Tokens tokens, Runnable back,
                              Consumer<SettingsAction> dispatch, Runnable toolPreview) {
        super(context, R.string.screen_output_title, tokens, back);
        this.dispatch = dispatch;
        process = toggle(Icons.EXPAND, R.string.screen_output_process_auto_expand_label,
                R.string.screen_output_process_auto_expand_desc, Field.PROCESS_AUTO_EXPAND);
        section(R.string.screen_output_section_conversation).row(process, false, 0);
        wrap = toggle(Icons.SCROLL_TEXT, R.string.screen_output_code_wrap_label,
                R.string.screen_output_code_wrap_desc, Field.CODE_WRAP);
        section(R.string.screen_output_section_code).row(wrap, false, 0);
        SettingsSection browser = section(R.string.screen_output_section_browser);
        builtin = browser(Icons.GLOBE, R.string.screen_output_browser_internal_label,
                R.string.screen_output_browser_internal_desc, "builtin");
        external = browser(Icons.EXTERNAL_LINK, R.string.screen_output_browser_external_label,
                R.string.screen_output_browser_external_desc, "external");
        browser.row(builtin, true, 52);
        browser.row(external, false, 52);
        sample = context.getString(R.string.screen_output_preview_markdown);
        preview = new MarkdownLite(context, tokens);
        preview.setLinkHandler(url -> { });
        LinearLayout box = new LinearLayout(context);
        box.setOrientation(VERTICAL);
        Ui.padding(box, 16, 16, 16, 16);
        box.addView(preview, new LayoutParams(-1, -2));
        section(R.string.screen_output_section_preview).row(box, false, 0);
        PreferenceRow tools = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                Icons.FILE_CODE, R.string.screen_output_toolcall_preview_label, R.string.screen_output_toolcall_preview_desc);
        tools.setOnClickListener(v -> toolPreview.run());
        section(R.string.screen_output_section_toolcall).row(tools, false, 0);
        theme.bind(t -> preview.render(sample, t));
    }

    private PreferenceRow toggle(int icon, int label, int desc, Field field) {
        PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.SWITCH, icon, label, desc);
        row.onToggle(value -> dispatch.accept(new SettingsAction.Change(field, value.toString())));
        return row;
    }

    private PreferenceRow browser(int icon, int label, int desc, String mode) {
        PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.OPTION, icon, label, desc);
        row.setOnClickListener(v -> dispatch.accept(new SettingsAction.Change(Field.BROWSER_MODE, mode)));
        return row;
    }

    public void render(SettingsState state) {
        process.setEnabled(state.loaded);
        wrap.setEnabled(state.loaded);
        builtin.setEnabled(state.loaded);
        external.setEnabled(state.loaded);
        process.checked(state.values.processAutoExpand());
        wrap.checked(state.values.codeWrap());
        builtin.active(!state.values.externalBrowser());
        external.active(state.values.externalBrowser());
        preview.setCodeWrapEnabled(state.values.codeWrap());
    }
}
