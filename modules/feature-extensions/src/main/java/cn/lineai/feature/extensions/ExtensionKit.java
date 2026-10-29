package cn.lineai.feature.extensions;

import android.app.Dialog;
import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Legacy settings vocabulary shared by the extension family: sections, rows, pills, buttons and
 * bottom sheets. Everything restyles in place through the page {@link ThemeBinder}.
 */
final class ExtensionKit {

    /** One bottom-sheet action row. */
    static final class SheetItem {
        final int title;
        final int description;
        final boolean danger;
        final Runnable action;

        SheetItem(int title, int description, boolean danger, Runnable action) {
            this.title = title;
            this.description = description;
            this.danger = danger;
            this.action = action;
        }
    }

    private ExtensionKit() {
    }

    static SettingsSection section(Context context, ThemeBinder theme, int title) {
        return new SettingsSection(context, title, theme);
    }

    static SettingsSection section(Context context, ThemeBinder theme, CharSequence title) {
        return new SettingsSection(context, title, theme);
    }

    /** One 68dp action row: accent-muted glyph slot, title + description and a trailing chevron. */
    static PreferenceRow action(Context context, ThemeBinder theme, int glyph, int title,
                                int description, int minHeightDp, Runnable click) {
        PreferenceRow row = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                glyph, title, description);
        row.setMinimumHeight(Ui.dp(context, minHeightDp));
        row.setOnClickListener(v -> click.run());
        return row;
    }

    static PreferenceRow plainAction(Context context, ThemeBinder theme, int glyph, int title,
                                     CharSequence description, int minHeightDp) {
        PreferenceRow row = new PreferenceRow(context, theme, PreferenceRow.Style.ACTION,
                glyph, title, R.string.screen_extension_detail_section_install_desc);
        row.descriptionText(description);
        row.setMinimumHeight(Ui.dp(context, minHeightDp));
        return row;
    }

    /** A 56dp centred empty / state row. */
    static TextView empty(Context context, ThemeBinder theme, CharSequence message) {
        TextView view = Ui.text(context, message, Tokens.FONT_SM,
                theme.current().textTertiary, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        view.setMinimumHeight(Ui.dp(context, 56));
        Ui.padding(view, 16, 16, 16, 16);
        theme.bind(t -> view.setTextColor(t.textTertiary));
        return view;
    }

    static TextView empty(Context context, ThemeBinder theme, int message) {
        return empty(context, theme, context.getString(message));
    }

    /** Small rounded tag, e.g. download counts and version badges. */
    static TextView tag(Context context, ThemeBinder theme, CharSequence text, boolean accent) {
        Tokens tokens = theme.current();
        TextView view = Ui.medium(context, text, Tokens.FONT_XS,
                accent ? tokens.accent : tokens.textSecondary);
        view.setBackground(Ui.rounded(context,
                accent ? tokens.accentMuted : tokens.surfaceLight, 8));
        Ui.padding(view, 8, 3, 8, 3);
        theme.bind(t -> {
            view.setTextColor(accent ? t.accent : t.textSecondary);
            view.setBackground(Ui.rounded(context,
                    accent ? t.accentMuted : t.surfaceLight, 8));
        });
        return view;
    }

    static TextView muted(Context context, ThemeBinder theme, int text, int sizeSp) {
        TextView view = Ui.text(context, context.getString(text), sizeSp,
                theme.current().textTertiary, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(context, 3), 1f);
        theme.bind(t -> view.setTextColor(t.textTertiary));
        return view;
    }

    static TextView title(Context context, ThemeBinder theme, CharSequence text, int sizeSp) {
        TextView view = Ui.medium(context, text, sizeSp, theme.current().text);
        theme.bind(t -> view.setTextColor(t.text));
        return view;
    }

    /** Legacy dialog button: 44dp tall, 10dp radius, centred 13sp label. */
    static LinearLayout button(Context context, ThemeBinder theme, int label, boolean primary,
                               Runnable click) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setMinimumHeight(Ui.dp(context, 44));
        button.setClickable(true);
        Ui.padding(button, 12, 12, 12, 12);
        TextView text = Ui.medium(context, context.getString(label), Tokens.FONT_SM,
                primary ? theme.current().textOnColor : theme.current().text);
        text.setGravity(Gravity.CENTER);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            text.setTextColor(primary ? t.textOnColor : t.text);
            button.setBackground(primary
                    ? Ui.rounded(context, t.accent, 10)
                    : Ui.roundedStroke(context, t.surfaceLight, 10, t.borderLight));
        });
        button.setOnClickListener(v -> click.run());
        return button;
    }

    /** Full-width primary action button. */
    static LinearLayout primaryButton(Context context, ThemeBinder theme, int label,
                                      int minHeightDp, Runnable click) {
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setMinimumHeight(Ui.dp(context, minHeightDp));
        Ui.padding(button, 12, 8, 12, 8);
        TextView text = Ui.medium(context, context.getString(label), Tokens.FONT_MD,
                theme.current().textOnColor);
        text.setGravity(Gravity.CENTER);
        button.addView(text, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            text.setTextColor(t.textOnColor);
            text.setTextSize(Tokens.FONT_MD);
            button.setBackground(Ui.rounded(context, t.accent, 12));
        });
        button.setOnClickListener(v -> click.run());
        return button;
    }

    static LinearLayout dangerButton(Context context, ThemeBinder theme, int label,
                                     Runnable click) {
        return button(context, theme, label, false, click);
    }

    /** Lays two or more buttons side by side with 8dp gaps and equal widths. */
    static LinearLayout buttonRow(Context context, View... buttons) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        for (int index = 0; index < buttons.length; index++) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, -2, 1f);
            if (index > 0) params.leftMargin = Ui.dp(context, 8);
            row.addView(buttons[index], params);
        }
        return row;
    }

    /** C++ SkillHubSection: elevated, bordered card with a 28dp glyph + 16sp heading. */
    static LinearLayout hubSection(Context context, ThemeBinder theme, int title, int glyph,
                                   List<View> content) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(card, 16, 12, 16, 12);
        LinearLayout heading = new LinearLayout(context);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        heading.setMinimumHeight(Ui.dp(context, 28));
        IconButton icon = new IconButton(context, glyph);
        icon.setIconSizeDp(28, 17);
        icon.setClickable(false);
        heading.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));
        TextView label = Ui.medium(context, context.getString(title), Tokens.FONT_MD,
                theme.current().text);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        heading.addView(label, labelParams);
        card.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        for (View child : content) {
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(context, 8);
            card.addView(child, params);
        }
        theme.bind(t -> {
            label.setTextColor(t.text);
            icon.setIconColor(t.accent);
            card.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.border));
        });
        return card;
    }

    static LinearLayout card(Context context, ThemeBinder theme, boolean bordered) {
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(bordered
                ? Ui.roundedStroke(context, t.surfaceElevated, 12, t.border)
                : Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    /** Rounded notice pill used by the SkillHub login / publish screens. */
    static LinearLayout notice(Context context, ThemeBinder theme, int glyph, int text) {
        LinearLayout notice = new LinearLayout(context);
        notice.setOrientation(LinearLayout.HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(notice, 12, 8, 12, 8);
        IconButton icon = new IconButton(context, glyph);
        icon.setIconSizeDp(28, 17);
        icon.setClickable(false);
        notice.addView(icon, new LinearLayout.LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));
        TextView label = Ui.text(context, context.getString(text), Tokens.FONT_XS,
                theme.current().textSecondary, Typeface.NORMAL);
        label.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        notice.addView(label, labelParams);
        theme.bind(t -> {
            icon.setIconColor(t.accent);
            label.setTextColor(t.textSecondary);
            notice.setBackground(Ui.rounded(context, t.accentMuted, 10));
        });
        return notice;
    }

    static void sheet(Context context, ThemeBinder theme, CharSequence title, List<SheetItem> items) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipToOutline(true);
        View handle = new View(context);
        handle.setBackground(Ui.rounded(context, theme.current().textTertiary, 2));
        FrameLayout handleRow = new FrameLayout(context);
        Ui.padding(handleRow, 0, 8, 0, 4);
        FrameLayout.LayoutParams handleParams = new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 4), Gravity.CENTER);
        handleRow.addView(handle, handleParams);
        panel.addView(handleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = Ui.bold(context, title, Tokens.FONT_LG, theme.current().text);
        Ui.padding(heading, 16, 0, 16, 12);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(context);
        divider.setBackgroundColor(theme.current().border);
        panel.addView(divider, new LinearLayout.LayoutParams(-1, Math.max(1, Ui.dp(context, 0.5f))));
        final Dialog[] holder = new Dialog[1];
        for (SheetItem item : items) {
            panel.addView(sheetRow(context, theme, item, holder),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(new View(context), new LinearLayout.LayoutParams(-1, Ui.dp(context, 34)));
        theme.bind(t -> {
            panel.setBackground(Ui.roundedTop(context, t.surfaceElevated, 16));
            heading.setTextColor(t.text);
            divider.setBackgroundColor(t.border);
            handle.setBackground(Ui.rounded(context, t.textTertiary, 2));
        });
        holder[0] = Sheets.showBottom(context, theme.current(), panel);
    }

    private static View sheetRow(Context context, ThemeBinder theme, SheetItem item,
                                 Dialog[] holder) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 52));
        Ui.padding(row, 16, 14, 16, 14);
        TextView title = Ui.text(context, context.getString(item.title), Tokens.FONT_MD,
                item.danger ? theme.current().danger : theme.current().text, Typeface.NORMAL);
        row.addView(title, new LinearLayout.LayoutParams(-1, -2));
        if (item.description != 0) {
            TextView description = Ui.text(context, context.getString(item.description),
                    Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(context, 2);
            row.addView(description, descParams);
            theme.bind(t -> description.setTextColor(t.textTertiary));
        }
        theme.bind(t -> title.setTextColor(item.danger ? t.danger : t.text));
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (holder[0] != null) holder[0].dismiss();
            item.action.run();
        });
        return row;
    }

    static void confirm(Context context, ThemeBinder theme, int title, CharSequence message,
                        Runnable confirmed) {
        Dialogs.confirm(context, theme.current(), context.getString(title), message,
                context.getString(R.string.common_cancel), context.getString(R.string.common_delete),
                accepted -> {
                    if (accepted) confirmed.run();
                });
    }

    static FormField field(Context context, ThemeBinder theme, int label, String value, int hint,
                           boolean multiline) {
        FormField field = new FormField(context, theme, label, value, context.getString(hint),
                false, multiline);
        field.input().setTextSize(Tokens.FONT_SM);
        field.input().setMinHeight(Ui.dp(context, multiline ? 120 : 44));
        return field;
    }

    static void hint(Context context, ThemeBinder theme, LinearLayout parent, int text) {
        TextView view = Ui.text(context, context.getString(text), Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        view.setLineSpacing(Ui.dp(context, 3), 1f);
        theme.bind(t -> view.setTextColor(t.textTertiary));
        parent.addView(view, new LinearLayout.LayoutParams(-1, -2));
    }

    /** Text action in the editor header (16sp medium accent, 42x39 minimum). */
    static TextView saveAction(Context context, ThemeBinder theme, Runnable save) {
        TextView action = Ui.medium(context, context.getString(R.string.screen_agent_save),
                Tokens.FONT_MD, theme.current().accent);
        action.setGravity(Gravity.CENTER);
        action.setMinimumWidth(Ui.dp(context, 42));
        action.setMinimumHeight(Ui.dp(context, 39));
        action.setClickable(true);
        theme.bind(t -> action.setTextColor(t.accent));
        action.setOnClickListener(v -> save.run());
        return action;
    }

    /** Circular accent-muted plus used as the detail page trailing action. */
    static View addAction(Context context, ThemeBinder theme, int description, Runnable action) {
        FrameLayout slot = new FrameLayout(context);
        slot.setBackground(Ui.rounded(context, theme.current().accentMuted, 18));
        IconButton plus = new IconButton(context, Icons.PLUS);
        plus.setIconSizeDp(36, 19);
        plus.setContentDescription(context.getString(description));
        slot.addView(plus, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        theme.bind(t -> {
            slot.setBackground(Ui.rounded(context, t.accentMuted, 18));
            plus.setIconColor(t.accent);
        });
        slot.setClickable(true);
        slot.setOnClickListener(v -> action.run());
        return slot;
    }

    /** Destructive action row (delete) inside a section card. */
    static LinearLayout dangerAction(Context context, ThemeBinder theme, int label,
                                     Runnable click) {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(context, 56));
        Ui.padding(row, 16, 12, 16, 12);
        IconButton icon = new IconButton(context, Icons.TRASH_2);
        icon.setIconSizeDp(36, 20);
        icon.setClickable(false);
        FrameLayout slot = new FrameLayout(context);
        slot.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        row.addView(slot, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        TextView text = Ui.medium(context, context.getString(label), Tokens.FONT_MD,
                theme.current().danger);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = Ui.dp(context, 12);
        row.addView(text, textParams);
        theme.bind(t -> {
            slot.setBackground(Ui.rounded(context, t.dangerMuted, 8));
            icon.setIconColor(t.danger);
            text.setTextColor(t.danger);
        });
        row.setClickable(true);
        row.setOnClickListener(v -> click.run());
        return row;
    }

    static void switchTint(Switch toggle, Tokens tokens) {
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
    }

    static void topMargin(Context context, View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(context, topDp);
        view.setLayoutParams(params);
    }
}
