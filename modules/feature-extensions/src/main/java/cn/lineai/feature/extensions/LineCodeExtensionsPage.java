package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** LineCode native extension family; the format is not available yet. */
public final class LineCodeExtensionsPage extends SettingsPage {

    public LineCodeExtensionsPage(Context context, Tokens tokens, Runnable back) {
        super(context, R.string.screen_extensions_section_linecode, tokens, back,
                Icons.PLUS, R.string.screen_extension_detail_inline_title_linecode,
                () -> Toast.makeText(context,
                        R.string.screen_extension_detail_empty_linecode,
                        Toast.LENGTH_SHORT).show());
        Ui.padding(content, 16, 16, 16, 100);
        content.addView(infoCard(), new LayoutParams(-1, -2));
    }

    private LinearLayout infoCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);

        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        IconButton icon = new IconButton(context, Icons.PACKAGE);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        icon.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        heading.addView(icon, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        TextView title = Ui.bold(context,
                context.getString(R.string.screen_extension_detail_inline_title_linecode),
                Tokens.FONT_MD, tokens.text);
        LinearLayout.LayoutParams titleParams = new LayoutParams(-2, -2);
        titleParams.leftMargin = Ui.dp(context, 12);
        heading.addView(title, titleParams);
        card.addView(heading, new LayoutParams(-1, -2));

        TextView desc = Ui.text(context,
                context.getString(R.string.screen_extension_detail_inline_desc_linecode),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 12);
        card.addView(desc, descParams);

        TextView empty = Ui.text(context,
                context.getString(R.string.screen_extension_detail_empty_linecode),
                Tokens.FONT_SM, tokens.textTertiary, Typeface.NORMAL);
        empty.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams emptyParams = new LayoutParams(-1, -2);
        emptyParams.topMargin = Ui.dp(context, 24);
        card.addView(empty, emptyParams);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }
}
