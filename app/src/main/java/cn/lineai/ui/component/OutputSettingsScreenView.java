package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.R;
import cn.lineai.model.OutputSettings;
import cn.lineai.ui.markdown.MarkdownView;

public final class OutputSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onCodeWrapChanged(boolean enabled);

        void onProcessAutoExpandChanged(boolean enabled);

        void onBrowserModeChanged(String mode);

        void onBrowserJavaScriptChanged(boolean enabled);

        void onToolCallPreviewClicked();
    }

    private final String PREVIEW_MARKDOWN;
    private final Listener listener;
    private final MarkdownView previewView;
    private OptionRowView builtinBrowserRow;
    private OptionRowView externalBrowserRow;
    private boolean codeWrapEnabled;
    private String browserMode;

    public OutputSettingsScreenView(Context context, OutputSettings settings, Listener listener) {
        super(context, context.getString(R.string.screen_output_title), listener::onBack, null);
        this.listener = listener;
        PREVIEW_MARKDOWN = context.getString(R.string.screen_output_preview_markdown);
        OutputSettings safeSettings = settings == null
                ? new OutputSettings(false, OutputSettings.BROWSER_BUILTIN)
                : settings;
        codeWrapEnabled = safeSettings.isCodeWrapEnabled();
        browserMode = safeSettings.getBrowserMode();
        boolean browserJavaScriptEnabled = safeSettings.isBrowserJavaScriptEnabled();
        LinearLayout content = getContent();

        SettingsSectionView conversation = new SettingsSectionView(context, context.getString(R.string.screen_output_section_conversation));
        conversation.addRow(new SwitchRowView(context, IconButtonView.EXPAND,
                context.getString(R.string.screen_output_process_auto_expand_label),
                context.getString(R.string.screen_output_process_auto_expand_desc),
                safeSettings.isProcessAutoExpandEnabled(),
                (buttonView, isChecked) -> listener.onProcessAutoExpandChanged(isChecked)), false);
        content.addView(conversation, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        previewView = new MarkdownView(context);
        previewView.setCodeWrapEnabled(codeWrapEnabled);
        previewView.setLinkHandler(url -> {});
        previewView.setMarkdown(PREVIEW_MARKDOWN);

        SettingsSectionView code = new SettingsSectionView(context, context.getString(R.string.screen_output_section_code));
        code.addRow(new SwitchRowView(context, IconButtonView.SCROLL_TEXT, context.getString(R.string.screen_output_code_wrap_label), context.getString(R.string.screen_output_code_wrap_desc),
                codeWrapEnabled,
                (buttonView, isChecked) -> {
                    codeWrapEnabled = isChecked;
                    previewView.setCodeWrapEnabled(isChecked);
                    listener.onCodeWrapChanged(isChecked);
                }), false);
        content.addView(code, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView browser = new SettingsSectionView(context, context.getString(R.string.screen_output_section_browser));
        builtinBrowserRow = new OptionRowView(context, IconButtonView.GLOBE, context.getString(R.string.screen_output_browser_internal_label), context.getString(R.string.screen_output_browser_internal_desc),
                OutputSettings.BROWSER_BUILTIN.equals(browserMode),
                () -> setBrowserMode(OutputSettings.BROWSER_BUILTIN));
        externalBrowserRow = new OptionRowView(context, IconButtonView.EXTERNAL_LINK, context.getString(R.string.screen_output_browser_external_label), context.getString(R.string.screen_output_browser_external_desc),
                OutputSettings.BROWSER_EXTERNAL.equals(browserMode),
                () -> setBrowserMode(OutputSettings.BROWSER_EXTERNAL));
        browser.addRow(builtinBrowserRow, true, 52);
        browser.addRow(externalBrowserRow, false, 52);
        content.addView(browser, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView preview = new SettingsSectionView(context, context.getString(R.string.screen_output_section_preview));
        LinearLayout previewBox = new LinearLayout(context);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(previewBox, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        previewBox.addView(previewView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        preview.addRow(previewBox, false);
        content.addView(preview, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView toolCall = new SettingsSectionView(context, context.getString(R.string.screen_output_section_toolcall));
        toolCall.addRow(new ActionRowView(context, IconButtonView.FILE_CODE, context.getString(R.string.screen_output_toolcall_preview_label),
                context.getString(R.string.screen_output_toolcall_preview_desc), false, true,
                () -> listener.onToolCallPreviewClicked()), false);
        content.addView(toolCall, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void setBrowserMode(String mode) {
        String normalized = OutputSettings.normalizeBrowserMode(mode);
        if (normalized.equals(browserMode)) {
            return;
        }
        browserMode = normalized;
        builtinBrowserRow.setActive(OutputSettings.BROWSER_BUILTIN.equals(browserMode));
        externalBrowserRow.setActive(OutputSettings.BROWSER_EXTERNAL.equals(browserMode));
        listener.onBrowserModeChanged(browserMode);
    }
}
