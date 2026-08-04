package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.ipc.IpcProviderType;
import cn.lineai.ipc.ScannedProvider;
import java.util.List;

public final class TerminalProviderDetailScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onScanProviders();

        void onProviderAddConfirmed(IpcProviderConfig config);

        void onEnabledChanged(String id, boolean enabled);

        void onDelete(String id);
    }

    private final Listener listener;
    private final List<ScannedProvider> scanResults;
    private final List<IpcProviderConfig> installed;
    private final boolean hasScanned;

    public TerminalProviderDetailScreenView(Context context,
                                             List<ScannedProvider> scanResults,
                                             List<IpcProviderConfig> installed,
                                             boolean hasScanned,
                                             Listener listener) {
        super(context, context.getString(R.string.screen_terminal_provider_title), listener::onBack, null);
        this.listener = listener;
        this.scanResults = scanResults;
        this.installed = installed;
        this.hasScanned = hasScanned;
        LinearLayout content = getContent();
        LineTheme.padding(content, 0, 0, 0, 100);

        addScanSection(content);
        addScanResultsSection(content);
        addInstalledSection(content);
    }

    private void addScanSection(LinearLayout content) {
        Context context = content.getContext();
        SettingsSectionView section = new SettingsSectionView(context, context.getString(R.string.screen_terminal_provider_scan));
        section.addRow(new ActionRowView(context, IconButtonView.SEARCH,
                context.getString(R.string.screen_terminal_provider_scan),
                context.getString(R.string.screen_terminal_provider_scan_desc),
                false, false, () -> listener.onScanProviders()), false);
        content.addView(section, sectionParams());
    }

    private void addScanResultsSection(LinearLayout content) {
        if (!hasScanned) {
            return;
        }
        Context context = content.getContext();
        SettingsSectionView section = new SettingsSectionView(context, context.getString(R.string.screen_terminal_provider_scan_results));
        if (scanResults == null || scanResults.isEmpty()) {
            section.addRow(emptyText(context.getString(R.string.screen_terminal_provider_scan_empty)), false);
        } else {
            for (int i = 0; i < scanResults.size(); i++) {
                ScannedProvider provider = scanResults.get(i);
                section.addRow(scanResultRow(provider), i < scanResults.size() - 1);
            }
        }
        content.addView(section, sectionParams());
    }

    private void addInstalledSection(LinearLayout content) {
        Context context = content.getContext();
        SettingsSectionView section = new SettingsSectionView(context, context.getString(R.string.screen_terminal_provider_installed));
        if (installed == null || installed.isEmpty()) {
            section.addRow(emptyText(context.getString(R.string.screen_terminal_provider_empty)), false);
        } else {
            for (int i = 0; i < installed.size(); i++) {
                IpcProviderConfig config = installed.get(i);
                section.addRow(installedRow(config), i < installed.size() - 1);
            }
        }
        content.addView(section, sectionParams());
    }

    private LinearLayout scanResultRow(ScannedProvider provider) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> showAddConfirmDialog(provider));
        row.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 8, LineTheme.BORDER_LIGHT));
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        IconButtonView icon = new IconButtonView(context, IconButtonView.TERMINAL);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(32, 16);
        icon.setClickable(false);
        icon.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 16));
        row.addView(icon, new LayoutParams(LineTheme.dp(context, 32), LineTheme.dp(context, 32)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        labelParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        labelParams.rightMargin = LineTheme.dp(context, LineTheme.SM);
        row.addView(labels, labelParams);
        labels.addView(LineTheme.textMedium(context, provider.getLabel(), LineTheme.FONT_MD, LineTheme.TEXT));
        labels.addView(LineTheme.text(context, provider.getPackageName(), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL));

        IconButtonView chevron = new IconButtonView(context, IconButtonView.PLUS);
        chevron.setIconColor(LineTheme.ACCENT);
        chevron.setIconSizeDp(20, 20);
        chevron.setClickable(false);
        row.addView(chevron, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));
        return row;
    }

    private LinearLayout installedRow(IpcProviderConfig config) {
        SwitchRowView row = new SwitchRowView(getContext(), IconButtonView.TERMINAL, config.getName(),
                config.getPackageName(), config.isEnabled(),
                (button, checked) -> listener.onEnabledChanged(config.getId(), checked));
        row.setOnLongClickListener(v -> {
            showDeleteDialog(config);
            return true;
        });
        return row;
    }

    private TextView emptyText(String text) {
        TextView view = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        LineTheme.padding(view, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return view;
    }

    private void showAddConfirmDialog(ScannedProvider provider) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_terminal_provider_add_confirm, provider.getLabel()));
        TextView desc = LineTheme.text(getContext(), getContext().getString(R.string.screen_terminal_provider_add_confirm_desc),
                LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(panel, getContext().getString(R.string.common_add), getContext().getString(R.string.screen_terminal_provider_add_confirm_desc), () -> {
            dialog.dismiss();
            IpcProviderConfig config = IpcProviderConfig.builder()
                    .providerType(IpcProviderType.TERMINAL.getId())
                    .name(provider.getLabel())
                    .packageName(provider.getPackageName())
                    .serviceClass(provider.getServiceClass())
                    .enabled(true)
                    .build();
            listener.onProviderAddConfirmed(config);
        }, LineTheme.ACCENT);
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showDeleteDialog(IpcProviderConfig config) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_terminal_provider_delete_title));
        TextView desc = LineTheme.text(getContext(), getContext().getString(R.string.screen_terminal_provider_delete_confirm, config.getName()),
                LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(panel, getContext().getString(R.string.common_delete), "", () -> {
            dialog.dismiss();
            listener.onDelete(config.getId());
        }, LineTheme.DANGER);
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private LinearLayout.LayoutParams sectionParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(getContext(), LineTheme.MD);
        return params;
    }

    private Dialog createBottomDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        return dialog;
    }

    private LinearLayout createBottomPanel() {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        return panel;
    }

    private void showBottomDialog(Dialog dialog, LinearLayout panel) {
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window == null) {
            return;
        }
        window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        window.setGravity(Gravity.BOTTOM);
    }

    private void addHandle(LinearLayout panel) {
        View handle = new View(panel.getContext());
        handle.setBackground(LineTheme.rounded(panel.getContext(), LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(panel.getContext(), 36), LineTheme.dp(panel.getContext(), 4));
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = LineTheme.dp(panel.getContext(), LineTheme.SM);
        params.bottomMargin = LineTheme.dp(panel.getContext(), LineTheme.XS);
        panel.addView(handle, params);
    }

    private void addSheetTitle(LinearLayout panel, String title) {
        TextView titleView = LineTheme.text(panel.getContext(), title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LineTheme.padding(titleView, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addDivider(LinearLayout panel) {
        View divider = new View(panel.getContext());
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action) {
        addActionRow(panel, label, desc, action, LineTheme.TEXT);
    }

    private void addActionRow(LinearLayout panel, String label, String desc, Runnable action, int labelColor) {
        Context context = panel.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> action.run());
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(LineTheme.text(context, label, LineTheme.FONT_MD, labelColor, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (desc != null && desc.length() > 0) {
            TextView descView = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(descView, descParams);
        }
        panel.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addBottomInset(LinearLayout panel) {
        panel.addView(new View(panel.getContext()), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(panel.getContext(), 34)));
    }
}
