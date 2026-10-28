package cn.lineai.runtime;

import android.app.Dialog;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.feature.chat.ToolModes;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Permission, chat-mode and context-usage sheets. */
public final class DialogFactory {

    private DialogFactory() {
    }

    public static void showPermission(PageHost host) {
        Tokens tokens = host.tokens();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(tokens.bg);
        Ui.padding(panel, 20, 20, 20, 28);
        panel.addView(Ui.bold(host.activity(),
                host.activity().getString(R.string.header_permission_desc), 18, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        final Dialog[] holder = new Dialog[1];
        String[] modes = new String[] {ToolModes.AUTO, ToolModes.CONFIRM, ToolModes.READONLY};
        int[] titles = new int[] {R.string.permission_mode_auto,
                R.string.permission_mode_confirm, R.string.permission_mode_readonly};
        int[] descriptions = new int[] {R.string.permission_mode_auto_desc,
                R.string.permission_mode_confirm_desc, R.string.permission_mode_readonly_desc};
        String current = ToolModes.mode(host.graph().settings());
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setGravity(Gravity.CENTER_VERTICAL);
            row.setClickable(true);
            boolean active = mode.equals(current);
            row.setBackground(Ui.roundedStroke(host.activity(),
                    active ? tokens.inputBg : tokens.surfaceElevated, 12,
                    active ? tokens.accent : tokens.border));
            Ui.padding(row, 14, 12, 14, 12);
            LinearLayout labels = new LinearLayout(host.activity());
            labels.setOrientation(LinearLayout.VERTICAL);
            row.addView(labels, new LinearLayout.LayoutParams(0, -2, 1f));
            labels.addView(Ui.bold(host.activity(),
                    host.activity().getString(titles[i]), 15, tokens.text),
                    new LinearLayout.LayoutParams(-1, -2));
            TextView desc = Ui.text(host.activity(),
                    host.activity().getString(descriptions[i]), 12,
                    tokens.textTertiary, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(host.activity(), 3);
            labels.addView(desc, descParams);
            row.setOnClickListener(v -> {
                ToolModes.setMode(host.graph().settings(), mode);
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = Ui.dp(host.activity(), 8);
            panel.addView(row, rowParams);
        }
        holder[0] = Sheets.showBottom(host.activity(), tokens, panel);
    }

    public static void showReasoning(PageHost host) {
        Tokens tokens = host.tokens();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(tokens.bg);
        Ui.padding(panel, 20, 20, 20, 28);
        panel.addView(Ui.bold(host.activity(),
                host.activity().getString(R.string.composer_reasoning_title), 18, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView subtitle = Ui.text(host.activity(),
                host.activity().getString(R.string.composer_reasoning_picker_subtitle), 12,
                tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(-1, -2);
        subtitleParams.topMargin = Ui.dp(host.activity(), 4);
        subtitleParams.bottomMargin = Ui.dp(host.activity(), 8);
        panel.addView(subtitle, subtitleParams);

        String[] levels = {"off", "auto", "low", "medium", "high", "max"};
        int[] labels = {R.string.screen_llm_thinking_off_label,
                R.string.screen_llm_thinking_auto_label, R.string.screen_llm_thinking_low_label,
                R.string.screen_llm_thinking_medium_label, R.string.screen_llm_thinking_high_label,
                R.string.screen_llm_thinking_max_label};
        int[] descriptions = {R.string.screen_llm_thinking_off_desc,
                R.string.screen_llm_thinking_auto, R.string.screen_llm_thinking_low,
                R.string.screen_llm_thinking_medium, R.string.screen_llm_thinking_high,
                R.string.screen_llm_thinking_max};
        final Dialog[] holder = new Dialog[1];
        String current = host.graph().settings().get(
                cn.lineai.core.model.UiPreferences.Field.REASONING.key, "medium");
        for (int i = 0; i < levels.length; i++) {
            final String level = levels[i];
            boolean active = level.equals(current);
            LinearLayout row = sheetRow(host, 0, host.activity().getString(labels[i]),
                    host.activity().getString(descriptions[i]), active);
            row.setOnClickListener(v -> {
                host.graph().settings().put(
                        cn.lineai.core.model.UiPreferences.Field.REASONING.key, level);
                host.graph().preferences().dispatch(
                        new cn.lineai.feature.settings.SettingsAction.Change(
                                cn.lineai.core.model.UiPreferences.Field.REASONING, level));
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
            params.topMargin = Ui.dp(host.activity(), 8);
            panel.addView(row, params);
        }
        holder[0] = Sheets.showBottom(host.activity(), tokens, panel);
    }

    public static void showMore(PageHost host) {
        Tokens tokens = host.tokens();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(tokens.bg);
        Ui.padding(panel, 20, 20, 20, 28);
        panel.addView(Ui.bold(host.activity(),
                host.activity().getString(R.string.common_more), 18, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        final Dialog[] holder = new Dialog[1];
        addMoreRow(host, panel, holder, Icons.MESSAGE_SQUARE,
                R.string.drawer_title_conversations, R.string.drawer_tab_conversations,
                () -> host.openDrawer());
        addMoreRow(host, panel, holder, Icons.SPARKLES,
                R.string.sheet_more_tutorial, R.string.sheet_more_tutorial_desc,
                () -> host.navigation().push(cn.lineai.feature.settings.SettingsScreens.TUTORIAL
                        .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addMoreRow(host, panel, holder, Icons.SETTINGS,
                R.string.screen_settings_title, R.string.sheet_more_settings_desc,
                () -> host.navigation().push(cn.lineai.feature.settings.SettingsScreens.HOME
                        .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addMoreRow(host, panel, holder, Icons.TRASH_2,
                R.string.sheet_more_clear, R.string.sheet_more_clear_desc,
                () -> {
                    String id = host.graph().chat().conversationId();
                    if (!id.isEmpty()) host.graph().chat().deleteConversation(id);
                    host.graph().chat().newConversation();
                });
        holder[0] = Sheets.showBottom(host.activity(), tokens, panel);
    }

    private static void addMoreRow(PageHost host, LinearLayout panel, Dialog[] holder,
                                   int icon, int title, int description, Runnable action) {
        LinearLayout row = sheetRow(host, icon, host.activity().getString(title),
                host.activity().getString(description), false);
        row.setOnClickListener(v -> {
            action.run();
            if (holder[0] != null) holder[0].dismiss();
        });
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(host.activity(), 8);
        panel.addView(row, params);
    }

    private static LinearLayout sheetRow(PageHost host, int icon, String title,
                                         String description, boolean active) {
        Tokens tokens = host.tokens();
        LinearLayout row = new LinearLayout(host.activity());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setBackground(Ui.roundedStroke(host.activity(),
                active ? tokens.inputBg : tokens.surfaceElevated, 12,
                active ? tokens.accent : tokens.border));
        Ui.padding(row, 14, 12, 14, 12);
        if (icon != 0) {
            android.widget.ImageView image = new android.widget.ImageView(host.activity());
            image.setImageResource(cn.lineai.ui.kit.Icons.drawable(icon));
            image.setColorFilter(tokens.textSecondary);
            row.addView(image, new LinearLayout.LayoutParams(
                    Ui.dp(host.activity(), 20), Ui.dp(host.activity(), 20)));
        }
        LinearLayout labels = new LinearLayout(host.activity());
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelsParams = new LinearLayout.LayoutParams(0, -2, 1f);
        if (icon != 0) labelsParams.leftMargin = Ui.dp(host.activity(), 12);
        row.addView(labels, labelsParams);
        labels.addView(Ui.bold(host.activity(), title, 15, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(host.activity(), description, 12, tokens.textTertiary,
                Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(host.activity(), 3);
        labels.addView(desc, descParams);
        return row;
    }

    public static void showChatMode(PageHost host) {
        Tokens tokens = host.tokens();
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackgroundColor(tokens.bg);
        Ui.padding(panel, 20, 20, 20, 28);
        panel.addView(Ui.bold(host.activity(),
                host.activity().getString(R.string.header_permission_desc), 18, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        String[] modes = new String[] {
                cn.lineai.core.model.ChatMode.CHAT, cn.lineai.core.model.ChatMode.PLAN,
                cn.lineai.core.model.ChatMode.AGENT, cn.lineai.core.model.ChatMode.CONTROL
        };
        int[] labels = new int[] {R.string.chat_mode_chat, R.string.chat_mode_plan,
                R.string.chat_mode_agent, R.string.chat_mode_control};
        int[] descriptions = new int[] {R.string.slash_command_chat_desc,
                R.string.slash_command_plan_desc, R.string.slash_command_agent_desc,
                R.string.slash_command_control_desc};
        final Dialog[] holder = new Dialog[1];
        String current = ToolModes.chatMode(host.graph().settings());
        for (int i = 0; i < modes.length; i++) {
            final String mode = modes[i];
            boolean active = mode.equals(current);
            LinearLayout row = new LinearLayout(host.activity());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setClickable(true);
            row.setBackground(Ui.roundedStroke(host.activity(),
                    active ? tokens.inputBg : tokens.surfaceElevated, 12,
                    active ? tokens.accent : tokens.border));
            Ui.padding(row, 14, 12, 14, 12);
            row.addView(Ui.bold(host.activity(),
                    host.activity().getString(labels[i]), 15, tokens.text),
                    new LinearLayout.LayoutParams(-1, -2));
            TextView desc = Ui.text(host.activity(),
                    host.activity().getString(descriptions[i]), 12,
                    tokens.textTertiary, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
            descParams.topMargin = Ui.dp(host.activity(), 3);
            row.addView(desc, descParams);
            row.setOnClickListener(v -> {
                ToolModes.setChatMode(host.graph().settings(), mode);
                host.graph().chat().refreshModel();
                if (holder[0] != null) holder[0].dismiss();
            });
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = Ui.dp(host.activity(), 8);
            panel.addView(row, rowParams);
        }
        holder[0] = Sheets.showBottom(host.activity(), tokens, panel);
    }

    public static Dialog showContextUsage(PageHost host) {
        Tokens tokens = host.tokens();
        Dialog dialog = new Dialog(host.activity());
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(host.activity());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(host.activity(), tokens.bg, 24));
        Ui.padding(panel, 24, 22, 24, 24);
        panel.addView(Ui.medium(host.activity(),
                host.activity().getString(R.string.context_usage_title), 18, tokens.text),
                new LinearLayout.LayoutParams(-1, Ui.dp(host.activity(), 42)));
        usage(host, panel, R.string.context_usage_used,
                String.format(java.util.Locale.ROOT, "%,d", host.graph().chat().usedTokens()));
        usage(host, panel, R.string.context_usage_limit,
                String.format(java.util.Locale.ROOT, "%,d", host.graph().chat().maxTokens()));
        usage(host, panel, R.string.context_usage_percent,
                host.activity().getString(R.string.context_usage_percent_value,
                        host.graph().chat().usedTokens() * 100
                                / host.graph().chat().maxTokens()));
        dialog.setContentView(panel);
        dialog.show();
        dialog.getWindow().setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        dialog.getWindow().setLayout(-1, -2);
        dialog.getWindow().setGravity(Gravity.BOTTOM);
        return dialog;
    }

    private static void usage(PageHost host, LinearLayout parent, int label, String value) {
        LinearLayout row = new LinearLayout(host.activity());
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(host.activity(), 42));
        TextView name = Ui.text(host.activity(), host.activity().getString(label), 14,
                host.tokens().textSecondary, Typeface.NORMAL);
        row.addView(name, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(Ui.medium(host.activity(), value, 14, host.tokens().text),
                new LinearLayout.LayoutParams(-2, -2));
        parent.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
}
