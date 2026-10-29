package cn.lineai.runtime;

import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Environment;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.Toast;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.feature.chat.ToolModes;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Chat menu sheets from C++ {@code chat_overlays.cpp}: the more menu (6 described rows), the
 * permission menu (3 modes + manage-all-files + revoke grants), the compaction confirmation and
 * the context menu. All use {@link Sheets#showBottom} framing.
 */
public final class ChatMenuSheets {

    private static final int DESCRIBED_ROW_MIN_DP = 69;
    private static final int PLAIN_ROW_MIN_DP = 52;
    private static final int PERMISSION_STORAGE_ROW_MIN_DP = 86;
    private static final int CONTEXT_TITLE_HEIGHT_DP = 52;

    private ChatMenuSheets() {
    }

    // ------------------------------------------------------------------ more

    public static void showMore(PageHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = ChatSheets.standardPanel(host,
                context.getString(R.string.common_more));
        addRow(host, panel, R.string.sheet_more_tutorial, R.string.sheet_more_tutorial_desc,
                () -> host.navigation().push(cn.lineai.feature.settings.SettingsScreens.TUTORIAL
                        .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addRow(host, panel, R.string.screen_settings_title, R.string.sheet_more_settings_desc,
                () -> host.navigation().push(cn.lineai.feature.settings.SettingsScreens.HOME
                        .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addRow(host, panel, R.string.sheet_more_export, R.string.sheet_more_export_desc,
                host::requestExport);
        // Message multi-select lives on the timeline; until it lands the row reports unavailable.
        addRow(host, panel, R.string.sheet_more_select_export,
                R.string.sheet_more_select_export_desc, host::unavailable);
        addRow(host, panel, R.string.sheet_more_compact, R.string.sheet_more_compact_desc,
                () -> showCompaction(host, host::unavailable));
        addRow(host, panel, R.string.sheet_more_clear, R.string.sheet_more_clear_desc, () -> {
            String id = host.graph().chat().conversationId();
            if (!id.isEmpty()) {
                host.graph().chat().deleteConversation(id);
            }
            host.graph().chat().newConversation();
        });
        panel.addView(ChatSheets.bottomSpacer(host, 12));
        Sheets.showBottom(context, tokens, panel);
    }

    private static void addRow(PageHost host, LinearLayout panel, int title, int detail,
                               Runnable action) {
        Context context = host.activity();
        LinearLayout row = ChatSheets.describedRow(host, context.getString(title),
                context.getString(detail), false, DESCRIBED_ROW_MIN_DP);
        row.setOnClickListener(v -> {
            ChatSheets.dismiss(row);
            action.run();
        });
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    // ------------------------------------------------------------ permissions

    public static void showPermission(PageHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = ChatSheets.standardPanel(host,
                context.getString(R.string.sheet_title_permissions));
        String current = ChatSheets.permissionMode(host);
        String[] modes = {ToolModes.AUTO, ToolModes.CONFIRM, ToolModes.READONLY};
        int[] labels = {R.string.permission_mode_auto, R.string.permission_mode_confirm,
                R.string.permission_mode_readonly};
        int[] descriptions = {R.string.permission_mode_auto_desc,
                R.string.permission_mode_confirm_desc,
                R.string.permission_mode_readonly_desc};
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            LinearLayout row = ChatSheets.describedRow(host, context.getString(labels[i]),
                    context.getString(descriptions[i]), mode.equals(current),
                    DESCRIBED_ROW_MIN_DP);
            row.setOnClickListener(v -> {
                ToolModes.setMode(host.graph().settings(), mode);
                ChatSheets.dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        boolean granted = storageGranted();
        LinearLayout storage = ChatSheets.describedRow(host,
                context.getString(R.string.permission_mode_manage_all_files),
                context.getString(granted ? R.string.permission_mode_storage_granted
                        : R.string.permission_mode_storage_required),
                granted, PERMISSION_STORAGE_ROW_MIN_DP);
        storage.setOnClickListener(v -> openStorageSettings(host));
        panel.addView(storage, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout revoke = ChatSheets.describedRow(host,
                context.getString(R.string.chat_permissions_clear), null, false,
                PLAIN_ROW_MIN_DP);
        revoke.setOnClickListener(v -> {
            ChatSheets.dismiss(revoke);
            Toast.makeText(context, R.string.chat_permissions_cleared, Toast.LENGTH_SHORT).show();
        });
        panel.addView(revoke, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(ChatSheets.bottomSpacer(host, 12));
        Sheets.showBottom(context, tokens, panel);
    }

    private static boolean storageGranted() {
        return Build.VERSION.SDK_INT < 30 || Environment.isExternalStorageManager();
    }

    private static void openStorageSettings(PageHost host) {
        if (Build.VERSION.SDK_INT < 30) {
            host.unavailable();
            return;
        }
        Intent intent = new Intent(
                android.provider.Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                Uri.parse("package:" + host.activity().getPackageName()));
        try {
            host.activity().startActivity(intent);
        } catch (ActivityNotFoundException error) {
            host.unavailable();
        }
    }

    // ------------------------------------------------------------- compaction

    public static void showCompaction(PageHost host, Runnable onConfirm) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = ChatSheets.standardPanel(host,
                context.getString(R.string.sheet_more_compact));
        LinearLayout confirm = ChatSheets.describedRow(host,
                context.getString(R.string.context_compact_confirm),
                context.getString(R.string.context_compact_confirm_desc), false,
                DESCRIBED_ROW_MIN_DP);
        confirm.setOnClickListener(v -> {
            ChatSheets.dismiss(confirm);
            if (onConfirm != null) {
                onConfirm.run();
            }
        });
        panel.addView(confirm, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout cancel = ChatSheets.describedRow(host,
                context.getString(R.string.common_cancel),
                context.getString(R.string.context_compact_cancel_desc), false,
                DESCRIBED_ROW_MIN_DP);
        cancel.setOnClickListener(v -> ChatSheets.dismiss(cancel));
        panel.addView(cancel, new LinearLayout.LayoutParams(-1, -2));
        panel.addView(ChatSheets.bottomSpacer(host, 12));
        Sheets.showBottom(context, tokens, panel);
    }

    // ----------------------------------------------------------- context menu

    public static void showContextMenu(PageHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = ChatSheets.column(context);
        Ui.padding(panel, 28, 24, 28, 28);
        android.widget.TextView title = Ui.medium(context,
                context.getString(R.string.chat_context_title), 22, tokens.text);
        panel.addView(title, new LinearLayout.LayoutParams(-1, ChatSheets.dp(context,
                CONTEXT_TITLE_HEIGHT_DP)));
        ModelConfig model = host.graph().models().selected();
        String modelLabel = model == null ? context.getString(R.string.slash_command_model_title)
                : model.displayName();
        addPlainRow(host, panel, context.getString(R.string.chat_context_files),
                () -> host.showChatAttachmentPicker());
        addPlainRow(host, panel, context.getString(R.string.chat_context_image),
                host::pickChatImage);
        addPlainRow(host, panel, modelLabel, () -> ChatSheets.showModelPicker(host));
        addPlainRow(host, panel, context.getString(R.string.chat_context_mode,
                        chatModeLabel(context, ToolModes.chatMode(host.graph().settings()))),
                () -> DialogFactory.showChatMode(host));
        addPlainRow(host, panel, context.getString(R.string.chat_context_workspace),
                () -> host.navigation().push(
                        cn.lineai.feature.workspace.WorkspaceScreens.PROJECTS
                                .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addPlainRow(host, panel, context.getString(R.string.header_permission_desc),
                () -> ChatSheets.showPermission(host));
        addPlainRow(host, panel, context.getString(R.string.chat_context_settings),
                () -> host.navigation().push(
                        cn.lineai.feature.settings.SettingsScreens.HOME
                                .key(cn.lineai.core.mvi.nav.Params.Empty.INSTANCE)));
        addPlainRow(host, panel, context.getString(R.string.common_more),
                () -> ChatSheets.showMore(host));
        Sheets.showBottom(context, tokens, panel);
    }

    private static void addPlainRow(PageHost host, LinearLayout panel, String label,
                                    Runnable action) {
        LinearLayout row = ChatSheets.plainRow(host, label);
        row.setOnClickListener(v -> {
            ChatSheets.dismiss(row);
            action.run();
        });
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    // -------------------------------------------------------------- chat mode

    public static void showChatMode(PageHost host) {
        Context context = host.activity();
        Tokens tokens = host.tokens();
        LinearLayout panel = ChatSheets.pickerPanel(host, R.string.chat_mode_title,
                context.getString(R.string.composer_model_picker_subtitle));
        String current = ToolModes.chatMode(host.graph().settings());
        String[] modes = {cn.lineai.core.model.ChatMode.CHAT, cn.lineai.core.model.ChatMode.PLAN,
                cn.lineai.core.model.ChatMode.AGENT, cn.lineai.core.model.ChatMode.CONTROL};
        int[] labels = {R.string.chat_mode_chat, R.string.chat_mode_plan,
                R.string.chat_mode_agent, R.string.chat_mode_control};
        int[] descriptions = {R.string.slash_command_chat_desc,
                R.string.slash_command_plan_desc, R.string.slash_command_agent_desc,
                R.string.slash_command_control_desc};
        for (int i = 0; i < modes.length; i++) {
            String mode = modes[i];
            LinearLayout row = ChatSheets.describedRow(host, context.getString(labels[i]),
                    context.getString(descriptions[i]), mode.equals(current),
                    DESCRIBED_ROW_MIN_DP);
            row.setOnClickListener(v -> {
                ToolModes.setChatMode(host.graph().settings(), mode);
                host.graph().chat().refreshModel();
                ChatSheets.dismiss(row);
            });
            panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
        panel.addView(ChatSheets.bottomSpacer(host, 12));
        Sheets.showBottom(context, tokens, panel, ChatSheets.PICKER_MAX_HEIGHT_DP);
    }

    private static String chatModeLabel(Context context, String mode) {
        String normalized = cn.lineai.core.model.ChatMode.normalize(mode);
        if (cn.lineai.core.model.ChatMode.CHAT.equals(normalized)) {
            return context.getString(R.string.chat_mode_chat);
        }
        if (cn.lineai.core.model.ChatMode.PLAN.equals(normalized)) {
            return context.getString(R.string.chat_mode_plan);
        }
        if (cn.lineai.core.model.ChatMode.CONTROL.equals(normalized)) {
            return context.getString(R.string.chat_mode_control);
        }
        return context.getString(R.string.chat_mode_agent);
    }

    /** Resaved reasoning helper used by the settings screen when it re-opens the picker. */
    static String reasoning(PageHost host) {
        return host.graph().settings().get(UiPreferences.Field.REASONING.key, "medium");
    }
}
