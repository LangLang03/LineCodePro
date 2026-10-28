package cn.lineai.feature.settings;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Entry cards for the five extension families. */
public final class ExtensionsPage extends SettingsPage {

    public ExtensionsPage(Context context, Tokens tokens, Runnable back, Consumer<String> open) {
        super(context, R.string.screen_extensions_title, tokens, back);
        Ui.padding(content, 16, 16, 16, 100);
        card("agent", R.string.screen_extensions_section_agent, R.string.screen_extensions_desc_agent,
                R.string.screen_extensions_badge_can_add, Icons.BRAIN, open);
        card("mcp", R.string.screen_extensions_section_mcp, R.string.screen_extensions_desc_mcp,
                R.string.screen_extensions_badge_https, Icons.MCP, open);
        card("skills", R.string.screen_extensions_section_skills, R.string.screen_extensions_desc_skills,
                R.string.screen_extensions_badge_zip, Icons.ARCHIVE, open);
        card("linecode", R.string.screen_extensions_section_linecode,
                R.string.screen_extensions_desc_linecode, R.string.screen_extensions_badge_lip,
                Icons.PACKAGE, open);
        card("terminalProvider", R.string.screen_extensions_section_terminal_provider,
                R.string.screen_extensions_desc_terminal_provider,
                R.string.screen_extensions_badge_terminal_provider, Icons.TERMINAL, open);
    }

    private void card(String id, int title, int description, int badge, int icon,
                      Consumer<String> open) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> open.accept(id));
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(card, 16, 12, 12, 12);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(context, 8);
        content.addView(card, params);

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 12));
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(tokens.accent);
        glyph.setIconSizeDp(44, 22);
        glyph.setClickable(false);
        iconBox.addView(glyph, new FrameLayout.LayoutParams(
                Ui.dp(context, 44), Ui.dp(context, 44), Gravity.CENTER));
        card.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 44), Ui.dp(context, 44)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = textParams.rightMargin = Ui.dp(context, 12);
        card.addView(text, textParams);
        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(titleRow, new LinearLayout.LayoutParams(-1, -2));
        titleRow.addView(Ui.bold(context, context.getString(title), Tokens.FONT_LG, tokens.text),
                new LinearLayout.LayoutParams(-2, -2));
        TextView badgeView = Ui.bold(context, context.getString(badge), Tokens.FONT_XS, tokens.accent);
        badgeView.setBackground(Ui.rounded(context, tokens.accentMuted, 999));
        Ui.padding(badgeView, 8, 3, 8, 3);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(-2, -2);
        badgeParams.leftMargin = Ui.dp(context, 8);
        titleRow.addView(badgeView, badgeParams);
        TextView desc = Ui.text(context, context.getString(description), Tokens.FONT_SM,
                tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        text.addView(desc, descParams);
        IconButton chevron = new IconButton(context, Icons.CHEVRON_RIGHT);
        chevron.setIconColor(tokens.textTertiary);
        chevron.setIconSizeDp(20, 17);
        chevron.setClickable(false);
        card.addView(chevron, new LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));

        theme.bind(t -> {
            card.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.border));
            iconBox.setBackground(Ui.rounded(context, t.accentMuted, 12));
            glyph.setIconColor(t.accent);
            badgeView.setBackground(Ui.rounded(context, t.accentMuted, 999));
        });
    }
}
