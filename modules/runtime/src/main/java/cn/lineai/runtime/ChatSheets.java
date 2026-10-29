package cn.lineai.runtime;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Locale;
import java.util.function.Consumer;
import java.util.function.Supplier;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Project;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.DrawerPanel;
import cn.lineai.feature.chat.ToolModes;
import cn.lineai.feature.settings.SettingsAction;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Chat overlay sheets matching the C++ {@code chat_overlays.cpp} framing.
 *
 * <p>Every sheet uses {@link Sheets#showBottom} (24dp radius, 16dp side inset and bottom gap),
 * the shared 17sp bold title header with a 48dp close action, 56dp picker rows with a 15sp medium
 * title and 11sp tertiary detail, and the 52dp / 68.57dp menu rows used by the more, permission
 * and compaction menus.</p>
 */
public final class ChatSheets {

    /** C++ kPickerPanelMaximumHeight: the model/reasoning pickers cap at 400dp. */
    static final int PICKER_MAX_HEIGHT_DP = 400;
    /** C++ PickerSheetPanel min_height: a short picker still reads as a drawer. */
    static final int PICKER_MIN_HEIGHT_DP = 240;

    private ChatSheets() {
    }

    // ------------------------------------------------------------- entry points

    public static void showModelPicker(PageHost host) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        List<ModelConfig> models = host.graph().models().list();
        ModelConfig selected = host.graph().models().selected();
        String selectedId = selected == null ? "" : selected.id;

        LinearLayout panel = pickerPanel(host, R.string.slash_command_model_title,
                context.getString(R.string.composer_model_picker_subtitle));
        for (ModelConfig model : models) {
            String title = model.name == null || model.name.isEmpty()
                    ? model.modelId : model.name;
            String detail = model.providerLabel == null || model.providerLabel.isEmpty()
                    ? model.modelId : model.providerLabel + " · " + model.modelId;
            LinearLayout row = pickerRow(host, title, detail, model.id.equals(selectedId));
            row.setOnClickListener(v -> {
                host.graph().models().select(model.id);
                host.graph().chat().refreshModel();
                dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        if (models.isEmpty()) {
            panel.addView(emptyRow(host, R.string.composer_model_picker_empty),
                    new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(bottomSpacer(host));
        Sheets.showBottom(context, tokens, panel, PICKER_MAX_HEIGHT_DP,
                PICKER_MIN_HEIGHT_DP);
    }

    public static void showReasoningPicker(PageHost host) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        ModelConfig model = host.graph().models().selected();
        String modelName = model == null ? "" : model.displayName();
        String title = context.getString(R.string.slash_command_reasoning_title, modelName);
        LinearLayout panel = pickerPanelText(host, title,
                context.getString(R.string.composer_reasoning_picker_subtitle));
        String current = host.graph().settings().get(
                UiPreferences.Field.REASONING.key, "medium");
        String[] levels = Reasoning.LEVELS;
        for (int i = 0; i < levels.length; i++) {
            String level = levels[i];
            LinearLayout row = pickerRow(host, context.getString(Reasoning.label(level)),
                    context.getString(Reasoning.detail(level)), level.equals(current));
            row.setOnClickListener(v -> {
                host.graph().settings().put(UiPreferences.Field.REASONING.key, level);
                host.graph().preferences().dispatch(new SettingsAction.Change(
                        UiPreferences.Field.REASONING, level));
                dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(bottomSpacer(host));
        Sheets.showBottom(context, tokens, panel, PICKER_MAX_HEIGHT_DP,
                PICKER_MIN_HEIGHT_DP);
    }

    public static Dialog showContextUsage(PageHost host) {
        Tokens tokens = host.tokens();
        Context context = host.activity();
        LinearLayout panel = column(context);
        Ui.padding(panel, 24, 22, 24, 24);
        TextView title = Ui.medium(context, context.getString(R.string.context_usage_title),
                Tokens.FONT_LG, tokens.text);
        panel.addView(title, new LinearLayout.LayoutParams(-1, dp(context, 42)));
        int used = host.graph().chat().usedTokens();
        int max = Math.max(1, host.graph().chat().maxTokens());
        usageRow(host, panel, R.string.context_usage_used,
                String.format(Locale.ROOT, "%,d", used));
        usageRow(host, panel, R.string.context_usage_limit,
                String.format(Locale.ROOT, "%,d", max));
        usageRow(host, panel, R.string.context_usage_percent,
                context.getString(R.string.context_usage_percent_value, used * 100 / max));
        return Sheets.showBottom(context, tokens, panel);
    }

    public static void showMore(PageHost host) {
        ChatMenuSheets.showMore(host);
    }

    public static void showPermission(PageHost host) {
        ChatMenuSheets.showPermission(host);
    }

    public static void showContextMenu(PageHost host) {
        ChatMenuSheets.showContextMenu(host);
    }

    public static void showCompaction(PageHost host, Runnable onConfirm) {
        ChatMenuSheets.showCompaction(host, onConfirm);
    }

    public static void showAttachmentPicker(PageHost host, DrawerPanel.FileNode tree,
                                            List<String> selected, Consumer<String> onToggle) {
        ChatAttachmentSheet.show(host, tree, selected, onToggle);
    }

    /** Loads the tree, then opens the attachment picker with the current composer selection. */
    public static void showAttachmentTree(PageHost host,
                                          Supplier<List<String>> selected,
                                          Consumer<String> onToggle) {
        loadWorkspaceTree(host, tree -> ChatAttachmentSheet.show(host, tree, selected.get(),
                onToggle));
    }

    /**
     * Loads the whole workspace tree off the main thread for the drawer and the attachment
     * picker. Directories first, then case-insensitive names; the node budget keeps huge
     * workspaces from blocking the drawer.
     */
    public static void loadWorkspaceTree(PageHost host, Consumer<DrawerPanel.FileNode> callback) {
        Project project = host.graph().projects().selected();
        if (project == null) {
            callback.accept(null);
            return;
        }
        final java.io.File root = new java.io.File(project.path);
        Scheduler scheduler = host.graph().graph().get(Scheduler.class);
        scheduler.io(() -> {
            DrawerPanel.FileNode tree = buildTree(root, "", 0, new int[] {4000});
            scheduler.main(() -> callback.accept(tree));
        });
    }

    private static DrawerPanel.FileNode buildTree(java.io.File dir, String relative, int depth,
                                                  int[] budget) {
        java.util.List<DrawerPanel.FileNode> children = new java.util.ArrayList<>();
        java.io.File[] files = dir.listFiles();
        if (files != null) {
            Arrays.sort(files, (a, b) -> a.isDirectory() != b.isDirectory()
                    ? (a.isDirectory() ? -1 : 1)
                    : a.getName().compareToIgnoreCase(b.getName()));
            for (java.io.File child : files) {
                if (budget[0]-- <= 0) {
                    break;
                }
                String path = relative.isEmpty()
                        ? child.getName() : relative + "/" + child.getName();
                children.add(child.isDirectory()
                        ? buildTree(child, path, depth + 1, budget)
                        : new DrawerPanel.FileNode(child.getName(), path, false,
                                Collections.<DrawerPanel.FileNode>emptyList()));
            }
        }
        return new DrawerPanel.FileNode(dir.getName(), relative, true, children);
    }

    /** Writes the chosen reasoning level so a reopened picker shows the current one. */
    static boolean isCurrentReasoning(PageHost host, String level) {
        return level.equals(host.graph().settings().get(UiPreferences.Field.REASONING.key,
                "medium"));
    }

    static String permissionMode(PageHost host) {
        return ToolModes.mode(host.graph().settings());
    }

    // ------------------------------------------------------------------ framing

    static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    static int dp(Context context, float value) {
        return Ui.dp(context, value);
    }

    static TextView text(Context context, String value, int sizeSp, int color) {
        return Ui.text(context, value, sizeSp, color, Typeface.NORMAL);
    }

    /** Shared 36x4 grabber + 17sp bold title + hairline of the C++ {@code StandardSheet}. */
    static LinearLayout standardPanel(PageHost host, String title) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = column(context);
        View handle = new View(context);
        handle.setBackground(Ui.rounded(context, tokens.textTertiary, 2));
        LinearLayout handleRow = row(context);
        handleRow.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.padding(handleRow, 0, 8, 0, 4);
        handleRow.addView(handle, new LinearLayout.LayoutParams(dp(context, 36), dp(context, 4)));
        panel.addView(handleRow, new LinearLayout.LayoutParams(-1, -2));
        TextView heading = Ui.bold(context, title, Tokens.FONT_LG, tokens.text);
        Ui.padding(heading, 24, 12, 24, 22);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(divider(context, tokens));
        return panel;
    }

    /** Picker header from C++ {@code PickerSheetPanel}: title/subtitle + 48dp close action. */
    static LinearLayout pickerPanel(PageHost host, int titleRes, String subtitle) {
        return pickerPanelText(host, host.activity().getString(titleRes), subtitle);
    }

    static LinearLayout pickerPanelText(PageHost host, String title, String subtitle) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = column(context);
        LinearLayout header = row(context);
        Ui.padding(header, 20, 14, 20, 10);
        LinearLayout labels = column(context);
        labels.addView(Ui.bold(context, title, Tokens.FONT_LG, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView detail = text(context, subtitle, Tokens.FONT_XS, tokens.textTertiary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(context, 3);
        labels.addView(detail, detailParams);
        header.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        header.addView(new View(context), new LinearLayout.LayoutParams(dp(context, 12), 1));
        header.addView(closeButton(host), new LinearLayout.LayoutParams(
                dp(context, 48), dp(context, 48)));
        panel.addView(header, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(divider(context, tokens));
        panel.setMinimumHeight(dp(context, PICKER_MIN_HEIGHT_DP));
        return panel;
    }

    /** C++ {@code PickerSheetRow}: 56dp, 15sp medium title, 11sp tertiary detail, 16dp check. */
    static LinearLayout pickerRow(PageHost host, String title, String detail, boolean selected) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, 56));
        Ui.padding(row, 20, 12, 20, 12);
        LinearLayout labels = column(context);
        labels.addView(Ui.medium(context, title, 15, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView detailView = text(context, detail, Tokens.FONT_XS, tokens.textTertiary);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(-1, -2);
        detailParams.topMargin = dp(context, 2);
        labels.addView(detailView, detailParams);
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selected) {
            android.widget.ImageView check = new android.widget.ImageView(context);
            check.setImageResource(Icons.drawable(Icons.CHECK));
            check.setColorFilter(tokens.accent);
            row.addView(check, new LinearLayout.LayoutParams(dp(context, 16), dp(context, 16)));
        }
        return row;
    }

    /** C++ {@code DescribedMenuRow}: 16sp title + 11sp tertiary detail, optional 18dp check. */
    static LinearLayout describedRow(PageHost host, String title, String detail,
                                     boolean selected, int minimumHeightDp) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, minimumHeightDp));
        Ui.padding(row, 16, 14, 16, 14);
        row.setBackground(selected ? Ui.rounded(context, tokens.accentMuted, 0) : null);
        LinearLayout labels = column(context);
        labels.addView(text(context, title, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        if (detail != null && !detail.isEmpty()) {
            TextView detailView = text(context, detail, Tokens.FONT_XS, tokens.textTertiary);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = dp(context, 2);
            labels.addView(detailView, params);
        }
        row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
        if (selected) {
            android.widget.ImageView check = new android.widget.ImageView(context);
            check.setImageResource(Icons.drawable(Icons.CHECK));
            check.setColorFilter(tokens.accent);
            row.addView(check, new LinearLayout.LayoutParams(dp(context, 18), dp(context, 18)));
        }
        return row;
    }

    /** C++ {@code PlainMenuRow}: bare 15sp label with a 52dp minimum height. */
    static LinearLayout plainRow(PageHost host, String label) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.setMinimumHeight(dp(context, 52));
        row.addView(text(context, label, 15, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    static LinearLayout emptyRow(PageHost host, int message) {
        Context context = host.activity();
        LinearLayout wrapper = column(context);
        Ui.padding(wrapper, 20, 24, 20, 24);
        wrapper.addView(text(context, context.getString(message), Tokens.FONT_SM,
                host.tokens().textTertiary), new LinearLayout.LayoutParams(-1, -2));
        return wrapper;
    }

    static View divider(Context context, Tokens tokens) {
        View divider = new View(context);
        divider.setBackgroundColor(tokens.borderLight);
        divider.setLayoutParams(new LinearLayout.LayoutParams(-1, Math.max(1,
                Math.round(context.getResources().getDisplayMetrics().density / 2.625f))));
        return divider;
    }

    /** C++ SheetBottomSpacer: keeps the last row clear of the system navigation bar. */
    static View bottomSpacer(PageHost host) {
        return bottomSpacer(host, 16);
    }

    static View bottomSpacer(PageHost host, int heightDp) {
        View spacer = new View(host.activity());
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, dp(host.activity(), heightDp)));
        return spacer;
    }

    static View closeButton(PageHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        android.widget.ImageView close = new android.widget.ImageView(context);
        close.setImageResource(Icons.drawable(Icons.CLOSE));
        close.setColorFilter(tokens.textSecondary);
        close.setScaleType(android.widget.ImageView.ScaleType.FIT_CENTER);
        Ui.padding(close, 15, 15, 15, 15);
        close.setClickable(true);
        close.setFocusable(true);
        close.setOnClickListener(v -> dismiss(close));
        return close;
    }

    static void dismiss(View view) {
        Sheets.dismiss(view);
    }

    private static void usageRow(PageHost host, LinearLayout parent, int label, String value) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout row = row(context);
        row.addView(text(context, context.getString(label), 14, tokens.textSecondary),
                new LinearLayout.LayoutParams(0, -2, 1f));
        row.addView(Ui.medium(context, value, 14, tokens.text),
                new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, dp(context, 42)));
    }

    /** Reasoning level tokens, prices and descriptions shared by the picker and the pill. */
    static final class Reasoning {
        static final String[] LEVELS = {"off", "auto", "low", "medium", "high", "max"};

        private Reasoning() {
        }

        static int label(String level) {
            if ("off".equals(level)) return R.string.screen_llm_thinking_off_label;
            if ("auto".equals(level)) return R.string.screen_llm_thinking_auto_label;
            if ("low".equals(level)) return R.string.screen_llm_thinking_low_label;
            if ("high".equals(level)) return R.string.screen_llm_thinking_high_label;
            if ("max".equals(level)) return R.string.screen_llm_thinking_max_label;
            return R.string.screen_llm_thinking_medium_label;
        }

        static int detail(String level) {
            if ("off".equals(level)) return R.string.screen_llm_thinking_off_desc;
            if ("auto".equals(level)) return R.string.screen_llm_thinking_auto;
            if ("low".equals(level)) return R.string.screen_llm_thinking_low;
            if ("high".equals(level)) return R.string.screen_llm_thinking_high;
            if ("max".equals(level)) return R.string.screen_llm_thinking_max;
            return R.string.screen_llm_thinking_medium;
        }
    }
}
