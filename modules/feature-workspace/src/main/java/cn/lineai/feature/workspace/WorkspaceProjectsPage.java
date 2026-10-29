package cn.lineai.feature.workspace;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.lineai.core.model.Project;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Workspace project list with selection and creation. */
public final class WorkspaceProjectsPage extends SettingsPage {

    public interface Listener {
        List<Project> list();

        void select(String id);

        void delete(String id);

        Project create(String name);

        void openExternal();

        void onChanges();

        /** Address {@code user@host:port} shown for SSH projects instead of their path. */
        default String sshAddress() {
            return "";
        }

        /** True when the current conversation already has messages. */
        default boolean conversationActive() {
            return false;
        }
    }

    private final Listener listener;
    private final FormField nameField;

    public WorkspaceProjectsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.sheet_title_workspace, tokens, back);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        nameField = new FormField(context, theme, R.string.workspace_field_name, "",
                context.getString(R.string.workspace_hint_name), false);
        reload();
    }

    public void reload() {
        content.removeAllViews();
        List<Project> projects = listener.list();
        if (projects.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.workspace_empty),
                    Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            Ui.padding(empty, 16, 32, 16, 32);
            content.addView(empty, new LayoutParams(-1, -2));
        } else {
            for (Project project : projects) {
                content.addView(row(project), rowParams());
            }
        }
        content.addView(changesCard(), cardParams());
        content.addView(externalCard(), cardParams());
        content.addView(createCard(), cardParams());
    }

    private LinearLayout row(Project project) {
        Context context = getContext();
        Tokens tokens = theme.current();
        boolean ssh = isSsh(project);
        String address = listener.sshAddress();
        String subtitle = ssh && !address.isEmpty() ? address : project.path;
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context,
                project.selected ? tokens.inputBg : tokens.surfaceElevated, 12,
                project.selected ? tokens.accent : tokens.border));
        Ui.padding(row, 14, 12, 12, 12);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (project.selected) {
                return;
            }
            if (listener.conversationActive()) {
                showSwitchBlocked();
                return;
            }
            listener.select(project.id);
            reload();
        });
        row.setOnLongClickListener(v -> {
            if (Project.DEFAULT_ID.equals(project.id)) return true;
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.drawer_project_remove_title),
                    context.getString(R.string.drawer_project_remove_message, project.name),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_delete),
                    confirmed -> {
                        if (confirmed) {
                            listener.delete(project.id);
                            reload();
                        }
                    });
            return true;
        });

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, Icons.FOLDER);
        icon.setIconColor(tokens.accent);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        iconBox.addView(icon, new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        row.addView(iconBox, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = labelParams.rightMargin = Ui.dp(context, 12);
        row.addView(labels, labelParams);
        labels.addView(Ui.bold(context, project.name, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView path = Ui.text(context, subtitle, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        path.setSingleLine(true);
        path.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LinearLayout.LayoutParams pathParams = new LinearLayout.LayoutParams(-1, -2);
        pathParams.topMargin = Ui.dp(context, 2);
        labels.addView(path, pathParams);

        if (project.selected) {
            TextView selected = Ui.medium(context,
                    context.getString(R.string.workspace_selected),
                    Tokens.FONT_XS, tokens.accent);
            selected.setBackground(Ui.rounded(context, tokens.accentMuted, 999));
            Ui.padding(selected, 8, 3, 8, 3);
            row.addView(selected, new LinearLayout.LayoutParams(-2, -2));
        }
        theme.bind(t -> row.setBackground(Ui.roundedStroke(context,
                project.selected ? t.inputBg : t.surfaceElevated, 12,
                project.selected ? t.accent : t.border)));
        return row;
    }

    private LinearLayout changesCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_changes_open),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.workspace_changes_open_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        card.setClickable(true);
        card.setOnClickListener(v -> listener.onChanges());
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private LinearLayout externalCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_open_folder),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.workspace_open_folder_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 10, tokens.border));
        button.addView(Ui.bold(context, context.getString(R.string.workspace_open_folder),
                Tokens.FONT_SM, tokens.textSecondary), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> listener.openExternal());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 10);
        card.addView(button, buttonParams);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private LinearLayout createCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(R.string.workspace_create_title),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams fieldParams = new LinearLayout.LayoutParams(-1, -2);
        fieldParams.topMargin = Ui.dp(context, 10);
        // reload() rebuilds the cards, and the field instance is reused across reloads: it must be
        // detached from the previous card or addView throws "child already has a parent".
        if (nameField.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) nameField.getParent()).removeView(nameField);
        }
        card.addView(nameField, fieldParams);
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 12));
        button.addView(Ui.bold(context, context.getString(R.string.workspace_create),
                Tokens.FONT_MD, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> create());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
        buttonParams.topMargin = Ui.dp(context, 12);
        card.addView(button, buttonParams);
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void create() {
        if (listener.conversationActive()) {
            showSwitchBlocked();
            return;
        }
        String name = nameField.value();
        if (name.isEmpty()) {
            Toast.makeText(getContext(), R.string.workspace_create_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        Project created = listener.create(name);
        if (created != null) {
            Toast.makeText(getContext(), R.string.workspace_created, Toast.LENGTH_SHORT).show();
            nameField.input().setText("");
            reload();
        }
    }

    private static boolean isSsh(Project project) {
        String id = project.id == null ? "" : project.id;
        String path = project.path == null ? "" : project.path;
        return id.startsWith("ssh:") || path.startsWith("ssh://");
    }

    private void showSwitchBlocked() {
        Dialogs.confirm(getContext(), theme.current(),
                getContext().getString(R.string.workspace_switch_blocked_title),
                getContext().getString(R.string.workspace_switch_blocked_message),
                getContext().getString(R.string.common_cancel),
                getContext().getString(R.string.common_confirm),
                confirmed -> { });
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }

    private LinearLayout.LayoutParams cardParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 12);
        return params;
    }
}
