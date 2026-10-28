package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** About screen: app identity, developer contacts and legal entry. */
public final class AboutPage extends SettingsPage {

    public AboutPage(Context context, Tokens tokens, Runnable back, String appLabel,
                     String versionName, long versionCode, Runnable github, Runnable licenses) {
        super(context, R.string.screen_about_title, tokens, back);
        Ui.padding(content, 16, 16, 16, 100);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(LinearLayout.VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.padding(header, 0, 20, 0, 20);

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 44));
        IconButton logo = new IconButton(context, Icons.CODE);
        logo.setIconColor(tokens.accent);
        logo.setIconSizeDp(88, 48);
        logo.setClickable(false);
        iconBox.addView(logo, new FrameLayout.LayoutParams(
                Ui.dp(context, 88), Ui.dp(context, 88), Gravity.CENTER));
        header.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 88), Ui.dp(context, 88)));

        TextView name = Ui.bold(context, appLabel, Tokens.FONT_XL, tokens.text);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = Ui.dp(context, 12);
        header.addView(name, nameParams);
        TextView version = Ui.text(context,
                context.getString(R.string.screen_about_apk_label, versionName, versionCode),
                Tokens.FONT_MD, tokens.textSecondary, Typeface.NORMAL);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = Ui.dp(context, 4);
        header.addView(version, versionParams);
        content.addView(header, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        theme.bind(t -> {
            iconBox.setBackground(Ui.rounded(context, t.accentMuted, 44));
            logo.setIconColor(t.accent);
            name.setTextColor(t.text);
            version.setTextColor(t.textSecondary);
        });

        group(R.string.screen_about_section_version);
        item(Icons.PACKAGE, R.string.screen_about_apk_version,
                context.getString(R.string.screen_about_version_value, versionName, versionCode), null);

        group(R.string.screen_about_section_developer);
        item(Icons.USER, R.string.screen_about_developer_label,
                context.getString(R.string.screen_about_developer_value), null);
        item(Icons.MESSAGE_CIRCLE, R.string.screen_about_qq_label,
                context.getString(R.string.screen_about_qq_value), null);
        item(Icons.GIT_BRANCH, R.string.screen_about_github_label,
                context.getString(R.string.screen_about_github_value), github);

        group(R.string.screen_about_section_legal);
        item(Icons.FILE_TEXT, R.string.screen_about_open_source_licenses,
                context.getString(R.string.screen_about_legal_value), licenses);

        TextView footer = Ui.text(context, context.getString(R.string.screen_about_copyright),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        theme.bind(t -> footer.setTextColor(t.textTertiary));
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = Ui.dp(context, 20);
        content.addView(footer, footerParams);
    }

    private void group(int title) {
        TextView label = Ui.text(getContext(), getContext().getString(title),
                Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
        theme.bind(t -> label.setTextColor(t.textTertiary));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = Ui.dp(getContext(), 16);
        params.bottomMargin = Ui.dp(getContext(), 8);
        params.leftMargin = Ui.dp(getContext(), 4);
        content.addView(label, params);
    }

    private void item(int icon, int label, String value, Runnable action) {
        Context context = getContext();
        PreferenceRow row = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                icon, label, R.string.screen_about_default_label);
        row.descriptionText(value);
        row.chevron(action != null);
        theme.bind(t -> row.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        if (action != null) row.setOnClickListener(v -> action.run());
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(row, params);
    }
}
