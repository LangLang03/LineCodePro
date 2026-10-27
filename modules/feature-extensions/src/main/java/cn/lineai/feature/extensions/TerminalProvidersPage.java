package cn.lineai.feature.extensions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.ScannedTerminalProvider;
import cn.lineai.core.model.TerminalProviderConfig;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Scan, add and manage external terminal provider apps (IPC). */
public final class TerminalProvidersPage extends SettingsPage {

    public interface Listener {
        List<TerminalProviderConfig> installed();

        List<ScannedTerminalProvider> scan();

        void add(ScannedTerminalProvider provider);

        void setEnabled(String id, boolean enabled);

        void delete(String id);
    }

    private final Listener listener;
    private List<ScannedTerminalProvider> scanResults = new ArrayList<>();
    private boolean hasScanned;

    public TerminalProvidersPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_terminal_provider_title, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        reload();
    }

    private void reload() {
        content.removeAllViews();
        addScanCard();
        if (hasScanned) addScanResults();
        addInstalled();
    }

    private void addScanCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card();
        card.addView(Ui.bold(context, context.getString(R.string.screen_terminal_provider_scan),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.screen_terminal_provider_scan_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        LinearLayout button = actionButton(R.string.screen_terminal_provider_scan,
                Icons.SEARCH, true, () -> {
                    scanResults = listener.scan();
                    hasScanned = true;
                    reload();
                });
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 12);
        card.addView(button, buttonParams);
        addCard(card);
    }

    private void addScanResults() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card();
        card.addView(Ui.bold(context,
                context.getString(R.string.screen_terminal_provider_scan_results),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        if (scanResults.isEmpty()) {
            card.addView(emptyRow(context.getString(R.string.screen_terminal_provider_scan_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (ScannedTerminalProvider provider : scanResults) {
                card.addView(scanRow(provider), new LinearLayout.LayoutParams(-1, -2));
            }
        }
        addCard(card);
    }

    private void addInstalled() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = card();
        card.addView(Ui.bold(context,
                context.getString(R.string.screen_terminal_provider_installed),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        List<TerminalProviderConfig> installed = listener.installed();
        if (installed.isEmpty()) {
            card.addView(emptyRow(context.getString(R.string.screen_terminal_provider_empty)),
                    new LinearLayout.LayoutParams(-1, -2));
        } else {
            for (TerminalProviderConfig provider : installed) {
                card.addView(installedRow(provider), new LinearLayout.LayoutParams(-1, -2));
            }
        }
        addCard(card);
    }

    private LinearLayout scanRow(ScannedTerminalProvider provider) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 8, tokens.borderLight));
        Ui.padding(row, 12, 8, 12, 8);
        row.setOnClickListener(v -> Dialogs.confirm(context, theme.current(),
                context.getString(R.string.screen_terminal_provider_add_confirm, provider.name),
                context.getString(R.string.screen_terminal_provider_add_confirm_desc),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_save),
                confirmed -> {
                    if (confirmed) {
                        listener.add(provider);
                        reload();
                    }
                }));
        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 16));
        IconButton icon = new IconButton(context, Icons.TERMINAL);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(32, 16);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 32), Ui.dp(context, 32), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 32), Ui.dp(context, 32)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 8);
        row.addView(labels, labelParams);
        labels.addView(Ui.medium(context, provider.name, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        labels.addView(Ui.text(context, provider.packageName, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL), new LinearLayout.LayoutParams(-1, -2));
        row.addView(chevron(), new LinearLayout.LayoutParams(Ui.dp(context, 20), Ui.dp(context, 20)));
        return row;
    }

    private LinearLayout installedRow(TerminalProviderConfig provider) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 0, 8, 0, 8);
        row.setOnLongClickListener(v -> {
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.screen_terminal_provider_delete_title),
                    context.getString(R.string.screen_terminal_provider_delete_confirm, provider.name),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_delete),
                    confirmed -> {
                        if (confirmed) {
                            listener.delete(provider.id);
                            reload();
                        }
                    });
            return true;
        });

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        labels.addView(Ui.bold(context, provider.name, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        labels.addView(Ui.text(context, provider.packageName, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL), new LinearLayout.LayoutParams(-1, -2));

        Switch toggle = new Switch(context);
        toggle.setChecked(provider.enabled);
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
        toggle.setOnCheckedChangeListener((button, checked) -> listener.setEnabled(provider.id, checked));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            toggle.setThumbTintList(new ColorStateList(states,
                    new int[] {t.accent, t.textTertiary}));
            toggle.setTrackTintList(new ColorStateList(states,
                    new int[] {t.accentDim, t.surfaceLight}));
        });
        return row;
    }

    private TextView emptyRow(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_SM,
                theme.current().textTertiary, Typeface.NORMAL);
        Ui.padding(view, 0, 12, 0, 4);
        return view;
    }

    private LinearLayout card() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 12);
        content.addView(card, params);
    }

    private LinearLayout actionButton(int label, int icon, boolean primary, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context,
                primary ? tokens.accent : tokens.surfaceLight, 8,
                primary ? tokens.accent : tokens.borderLight));
        button.setOnClickListener(v -> action.run());
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(primary ? tokens.textOnColor : tokens.textSecondary);
        glyph.setIconSizeDp(16, 16);
        glyph.setClickable(false);
        button.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_SM,
                primary ? tokens.textOnColor : tokens.textSecondary);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.leftMargin = Ui.dp(context, 4);
        button.addView(text, textParams);
        return button;
    }

    private IconButton chevron() {
        IconButton chevron = new IconButton(getContext(), Icons.PLUS);
        chevron.setIconColor(theme.current().accent);
        chevron.setIconSizeDp(20, 20);
        chevron.setClickable(false);
        return chevron;
    }
}
