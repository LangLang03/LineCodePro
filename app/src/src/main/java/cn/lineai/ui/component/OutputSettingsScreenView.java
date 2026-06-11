package cn.lineai.ui.component;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.model.OutputSettings;
import cn.lineai.ui.markdown.MarkdownView;
import cn.lineai.ui.theme.LineTheme;

public final class OutputSettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onCodeWrapChanged(boolean enabled);

        void onBrowserModeChanged(String mode);

        void onBrowserJavaScriptChanged(boolean enabled);
    }

    private static final String PREVIEW_MARKDOWN =
            "### Markdown 预览\n"
                    + "- 列表、粗体、链接会按消息样式渲染\n"
                    + "- 代码换行开关会立即影响下面的代码块\n\n"
                    + "| 类型 | 状态 |\n"
                    + "| --- | --- |\n"
                    + "| 表格 | 已启用 |\n"
                    + "| 链接 | [LineCode](https://line.local) |\n\n"
                    + "```java\n"
                    + "String path = \"/storage/emulated/0/.linecode/home/very/long/workspace/path/that/needs/wrap/Main.java\";\n"
                    + "System.out.println(\"code wrap preview: \" + path);\n"
                    + "```";

    private final Listener listener;
    private final MarkdownView previewView;
    private OptionRowView builtinBrowserRow;
    private OptionRowView externalBrowserRow;
    private boolean codeWrapEnabled;
    private String browserMode;

    public OutputSettingsScreenView(Context context, OutputSettings settings, Listener listener) {
        super(context, "输出与浏览", listener::onBack, null);
        this.listener = listener;
        OutputSettings safeSettings = settings == null
                ? new OutputSettings(false, OutputSettings.BROWSER_BUILTIN)
                : settings;
        codeWrapEnabled = safeSettings.isCodeWrapEnabled();
        browserMode = safeSettings.getBrowserMode();
        boolean browserJavaScriptEnabled = safeSettings.isBrowserJavaScriptEnabled();
        LinearLayout content = getContent();

        previewView = new MarkdownView(context);
        previewView.setCodeWrapEnabled(codeWrapEnabled);
        previewView.setLinkHandler(url -> {});
        previewView.setMarkdown(PREVIEW_MARKDOWN);

        SettingsSectionView code = new SettingsSectionView(context, "代码显示");
        code.addRow(new SwitchRowView(context, IconButtonView.SCROLL_TEXT, "代码自动换行", "关闭时代码可水平滚动",
                codeWrapEnabled,
                (buttonView, isChecked) -> {
                    codeWrapEnabled = isChecked;
                    previewView.setCodeWrapEnabled(isChecked);
                    listener.onCodeWrapChanged(isChecked);
                }), false);
        content.addView(code, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView browser = new SettingsSectionView(context, "网页打开方式");
        builtinBrowserRow = new OptionRowView(context, IconButtonView.GLOBE, "内置浏览器", "在应用内打开网页",
                OutputSettings.BROWSER_BUILTIN.equals(browserMode),
                () -> setBrowserMode(OutputSettings.BROWSER_BUILTIN));
        externalBrowserRow = new OptionRowView(context, IconButtonView.EXTERNAL_LINK, "外部浏览器", "使用系统浏览器打开",
                OutputSettings.BROWSER_EXTERNAL.equals(browserMode),
                () -> setBrowserMode(OutputSettings.BROWSER_EXTERNAL));
        browser.addRow(builtinBrowserRow, true, 52);
        browser.addRow(externalBrowserRow, true, 52);
        browser.addRow(new SwitchRowView(context, IconButtonView.CODE, "内置浏览器 JavaScript", "关闭后应用内网页不会执行脚本",
                browserJavaScriptEnabled,
                (buttonView, isChecked) -> listener.onBrowserJavaScriptChanged(isChecked)), false);
        content.addView(browser, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView preview = new SettingsSectionView(context, "预览");
        LinearLayout previewBox = new LinearLayout(context);
        previewBox.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(previewBox, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        previewBox.addView(previewView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        preview.addRow(previewBox, false);
        content.addView(preview, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
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
