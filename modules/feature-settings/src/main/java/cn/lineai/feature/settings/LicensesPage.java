package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Open-source licence attributions. */
public final class LicensesPage extends SettingsPage {

    public LicensesPage(Context context, Tokens tokens, Runnable back) {
        super(context, R.string.screen_licenses_title, tokens, back);
        Ui.padding(content, 12, 12, 12, 100);
        license(R.string.screen_licenses_commonmark_core,
                "org.commonmark:commonmark:0.28.0 · BSD-2-Clause",
                R.string.screen_licenses_commonmark_core_desc);
        license(R.string.screen_licenses_commonmark_gfm,
                "org.commonmark:commonmark-ext-gfm-tables:0.28.0 · BSD-2-Clause",
                R.string.screen_licenses_commonmark_gfm_desc);
        license(R.string.screen_licenses_jsch,
                "com.github.mwiede:jsch:2.28.2 · Revised BSD / ISC",
                R.string.screen_licenses_jsch_desc);
        license(R.string.screen_licenses_lucide,
                "lucide-react-native:1.14.0 · ISC / MIT",
                R.string.screen_licenses_lucide_desc);
    }

    private void license(int name, String meta, int description) {
        Context context = getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(item, 12, 12, 12, 12);
        TextView title = Ui.text(context, context.getString(name), Tokens.FONT_MD,
                theme.current().text, Typeface.NORMAL);
        TextView coordinates = Ui.text(context, meta, Tokens.FONT_SM,
                theme.current().textSecondary, Typeface.NORMAL);
        TextView text = Ui.text(context, context.getString(description), Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        text.setLineSpacing(Ui.dp(context, 3), 1f);
        item.addView(title, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        item.addView(coordinates, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        item.addView(text, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        theme.bind(t -> {
            item.setBackground(Ui.rounded(context, t.surfaceElevated, 12));
            title.setTextColor(t.text);
            coordinates.setTextColor(t.textSecondary);
            text.setTextColor(t.textTertiary);
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(item, params);
    }
}
