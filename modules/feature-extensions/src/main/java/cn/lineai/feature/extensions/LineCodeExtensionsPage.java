package cn.lineai.feature.extensions;

import android.content.Context;
import android.widget.LinearLayout;
import android.widget.Toast;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.tokens.Tokens;

/** LineCode native extension family; the package format is not available yet. */
public final class LineCodeExtensionsPage extends ExtensionPage {

    /**
     * Builds the placeholder page for the not-yet-released package format.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     */
    public LineCodeExtensionsPage(Context context, Tokens tokens, Runnable back) {
        super(context, tokens, back, R.string.screen_extensions_section_linecode);
        header(R.string.screen_extensions_section_linecode,
                ExtensionKit.addAction(context, theme,
                        R.string.screen_extension_detail_inline_title_linecode, this::unavailable));
        SettingsSection create = section(R.string.screen_extension_detail_section_install_other);
        create.row(ExtensionKit.action(getContext(), theme, Icons.PACKAGE,
                R.string.screen_extension_detail_inline_title_linecode,
                R.string.screen_extension_detail_inline_desc_linecode, 68, this::unavailable),
                false, 0);
        SettingsSection installed = section(R.string.screen_extension_detail_section_installed);
        installed.row(ExtensionKit.empty(getContext(), theme,
                R.string.screen_extension_detail_empty_linecode), false, 0);
    }

    /** Reports the unavailable package format with a toast. */
    private void unavailable() {
        Toast.makeText(getContext(), R.string.screen_extension_detail_empty_linecode,
                Toast.LENGTH_SHORT).show();
    }
}
