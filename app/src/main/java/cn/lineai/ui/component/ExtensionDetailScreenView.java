package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Environment;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.ExtensionItemUiModel;
import cn.lineai.model.ExtensionKindUiModel;
import cn.lineai.model.SkillRecord;
import cn.lineai.ui.MainChatView;
import java.util.ArrayList;
import java.util.List;

public final class ExtensionDetailScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onAddAgent();

        void onEditAgent(String id);

        void onAddMcp();

        void onEditMcp(String id);

        void onCreateSkill(String location, String name, String description, String content);

        void onInstallSkill(String location, String sourcePath, String name);

        void onInstallSkillFromUri(String location, String uri, String displayName);

        void onInstallSkillFromGitHub(String location, String githubUrl);

        void onEnabledChanged(String kind, String id, boolean enabled);

        void onDelete(String kind, String id);

        void onDeleteMany(String kind, List<String> ids);

        void onShareWorkspace();
    }

    private final String kind;
    private final Listener listener;
    private final ExtensionKindUiModel uiModel;
    private final java.util.LinkedHashSet<String> multiSelectedIds = new java.util.LinkedHashSet<>();
    private final android.widget.FrameLayout headerHost;
    private final LinearLayout installedHost;

    public ExtensionDetailScreenView(Context context, ExtensionKindUiModel uiModel, Listener listener) {
        super(context, uiModel != null ? uiModel.getTitle() : context.getString(R.string.screen_extensions_section_linecode), listener::onBack, addButton(context, uiModel, listener));
        this.kind = uiModel != null ? uiModel.getKind() : "";
        this.listener = listener;
        this.uiModel = uiModel;
        getRightAction().setOnClickListener(v -> handleAdd());
        LinearLayout content = getContent();
        LineTheme.padding(content, 0, 0, 0, 100);

        headerHost = new android.widget.FrameLayout(context);
        content.addView(headerHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        String sectionTitle = uiModel != null ? uiModel.getSectionTitle() : context.getString(R.string.screen_extension_detail_section_install_other);
        int sectionIcon = uiModel != null ? uiModel.getIconType() : IconButtonView.PACKAGE;
        String sectionInlineTitle = uiModel != null ? uiModel.getInlineTitle() : context.getString(R.string.screen_extension_detail_inline_title_linecode);
        String sectionInlineDesc = uiModel != null ? uiModel.getInlineDesc() : context.getString(R.string.screen_extension_detail_inline_desc_linecode);

        SettingsSectionView add = new SettingsSectionView(context, sectionTitle);
        add.addRow(new ActionRowView(context, sectionIcon, sectionInlineTitle, sectionInlineDesc, false, true, this::handleAdd), false);
        content.addView(add, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if ("skills".equals(kind)) {
            SettingsSectionView workspace = new SettingsSectionView(context, context.getString(R.string.screen_extension_detail_workspace_share));
            workspace.addRow(new ActionRowView(
                    context,
                    IconButtonView.FOLDER_OPEN,
                    context.getString(R.string.screen_extension_detail_workspace_share),
                    context.getString(R.string.screen_extension_detail_workspace_share_desc),
                    false,
                    true,
                    () -> listener.onShareWorkspace()
            ), false);
            content.addView(workspace, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        installedHost = new LinearLayout(context);
        installedHost.setOrientation(VERTICAL);
        content.addView(installedHost, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        renderInstalledSection();
    }

    private void handleAdd() {
        if (uiModel != null) {
            switch (uiModel.getAddActionType()) {
                case ExtensionKindUiModel.ADD_ACTION_AGENT:
                    listener.onAddAgent();
                    return;
                case ExtensionKindUiModel.ADD_ACTION_MCP:
                    listener.onAddMcp();
                    return;
                case ExtensionKindUiModel.ADD_ACTION_SKILL:
                    showSkillActions();
                    return;
                default:
                    break;
            }
        }
        Toast.makeText(getContext(), getContext().getString(R.string.screen_extension_detail_empty_linecode), Toast.LENGTH_SHORT).show();
    }

    private void renderInstalledSection() {
        headerHost.removeAllViews();
        installedHost.removeAllViews();
        if (!multiSelectedIds.isEmpty() && "skills".equals(kind)) {
            LinearLayout bar = new LinearLayout(getContext());
            bar.setOrientation(HORIZONTAL);
            bar.setGravity(Gravity.CENTER_VERTICAL);
            LineTheme.padding(bar, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.SM);
            TextView count = LineTheme.text(
                    getContext(),
                    getContext().getString(R.string.screen_extension_detail_selected_count, multiSelectedIds.size()),
                    LineTheme.FONT_MD,
                    LineTheme.TEXT,
                    Typeface.BOLD
            );
            bar.addView(count, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
            TextView cancel = LineTheme.textMedium(getContext(), getContext().getString(R.string.common_cancel), LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
            cancel.setOnClickListener(v -> {
                multiSelectedIds.clear();
                renderInstalledSection();
            });
            bar.addView(cancel, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
            TextView delete = LineTheme.textMedium(getContext(), getContext().getString(R.string.common_delete), LineTheme.FONT_SM, LineTheme.DANGER);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            deleteParams.leftMargin = LineTheme.dp(getContext(), LineTheme.MD);
            delete.setOnClickListener(v -> showDeleteManyConfirm());
            bar.addView(delete, deleteParams);
            headerHost.addView(bar, new android.widget.FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        SettingsSectionView installed = new SettingsSectionView(
                getContext(),
                getContext().getString(R.string.screen_extension_detail_section_installed)
        );
        renderInstalled(installed);
        installedHost.addView(installed, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void renderInstalled(SettingsSectionView installed) {
        if (uiModel == null) {
            installed.addRow(empty(getContext().getString(R.string.screen_extension_detail_empty_linecode)), false);
            return;
        }
        List<ExtensionItemUiModel> items = uiModel.getInstalledItems();
        if (items == null || items.isEmpty()) {
            installed.addRow(empty(uiModel.getEmptyMessage()), false);
            return;
        }
        boolean multi = !multiSelectedIds.isEmpty() && "skills".equals(kind);
        for (int i = 0; i < items.size(); i++) {
            ExtensionItemUiModel item = items.get(i);
            installed.addRow(
                    extensionRow(
                            uiModel.getKind(),
                            item.getId(),
                            uiModel.getIconType(),
                            item.getName(),
                            item.getDescription(),
                            item.isEnabled(),
                            multi
                    ),
                    i < items.size() - 1
            );
        }
    }

    private LinearLayout extensionRow(
            String rowKind,
            String id,
            int iconType,
            String title,
            String desc,
            boolean enabled,
            boolean multiMode
    ) {
        if (multiMode) {
            boolean selected = multiSelectedIds.contains(id);
            OptionRowView row = new OptionRowView(getContext(), iconType, title, desc, selected, () -> {
                if (multiSelectedIds.contains(id)) {
                    multiSelectedIds.remove(id);
                } else {
                    multiSelectedIds.add(id);
                }
                renderInstalledSection();
            });
            return row;
        }
        SwitchRowView row = new SwitchRowView(getContext(), iconType, title, desc, enabled,
                (button, checked) -> listener.onEnabledChanged(rowKind, id, checked));
        row.setOnLongClickListener(v -> {
            if ("skills".equals(rowKind)) {
                multiSelectedIds.clear();
                multiSelectedIds.add(id);
                renderInstalledSection();
            } else {
                confirmDelete(rowKind, id, title);
            }
            return true;
        });
        return row;
    }

    private void showDeleteManyConfirm() {
        if (multiSelectedIds.isEmpty()) {
            return;
        }
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_delete_multi_title));
        TextView desc = LineTheme.text(
                getContext(),
                getContext().getString(R.string.screen_extension_detail_delete_multi_message, multiSelectedIds.size()),
                LineTheme.FONT_SM,
                LineTheme.TEXT_TERTIARY,
                Typeface.NORMAL
        );
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(
                panel,
                getContext().getString(R.string.common_delete),
                getContext().getString(R.string.screen_extension_detail_delete_multi_warning),
                () -> {
                    ArrayList<String> ids = new ArrayList<>(multiSelectedIds);
                    dialog.dismiss();
                    multiSelectedIds.clear();
                    listener.onDeleteMany(kind, ids);
                },
                LineTheme.DANGER
        );
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private TextView empty(String text) {
        TextView view = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        LineTheme.padding(view, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return view;
    }

    private void showSkillActions() {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_sheet_title_skills));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_install_zip), getContext().getString(R.string.screen_extension_detail_install_zip_desc), () -> {
            dialog.dismiss();
            chooseSkillDocument();
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_install_github), getContext().getString(R.string.screen_extension_detail_install_github_desc), () -> {
            dialog.dismiss();
            showGitHubSkillDialog();
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_create_skill), getContext().getString(R.string.screen_extension_detail_create_skill_desc), () -> {
            dialog.dismiss();
            showCreateSkillDialog();
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_install_path), getContext().getString(R.string.screen_extension_detail_install_path_desc), () -> {
            dialog.dismiss();
            showInstallSkillDialog();
        });
        if (!multiSelectedIds.isEmpty() || (uiModel != null && uiModel.getInstalledItems() != null && !uiModel.getInstalledItems().isEmpty())) {
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_multi_select), getContext().getString(R.string.screen_extension_detail_multi_select_desc), () -> {
                dialog.dismiss();
                if (multiSelectedIds.isEmpty() && uiModel != null && !uiModel.getInstalledItems().isEmpty()) {
                    multiSelectedIds.add(uiModel.getInstalledItems().get(0).getId());
                }
                renderInstalledSection();
            });
        }
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showGitHubSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel(getContext().getString(R.string.screen_extension_detail_dialog_github_skill));
        FormTextFieldView url = new FormTextFieldView(
                getContext(),
                getContext().getString(R.string.screen_extension_detail_field_github_url),
                "",
                getContext().getString(R.string.screen_extension_detail_hint_github_url),
                getContext().getString(R.string.screen_extension_detail_helper_github_url),
                false,
                false
        );
        RadioGroup scope = locationGroup();
        panel.addView(url);
        panel.addView(scope, top());
        panel.addView(actionButton(getContext().getString(R.string.common_install), () -> {
            String value = value(url);
            if (value.length() == 0) {
                Toast.makeText(getContext(), getContext().getString(R.string.skill_github_invalid_url), Toast.LENGTH_SHORT).show();
                return;
            }
            listener.onInstallSkillFromGitHub(checkedLocation(scope), value);
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private void chooseSkillDocument() {
        Context context = getContext();
        if (!(context instanceof MainChatView.WorkspaceHost)) {
            showInstallSkillDialog();
            return;
        }
        ((MainChatView.WorkspaceHost) context).openDocumentPicker("*/*", new String[] {"skill.zip"}, new MainChatView.DocumentPickCallback() {
            @Override
            public void onDocumentPicked(String uri, String displayName) {
                String lower = (displayName == null ? "" : displayName).toLowerCase(java.util.Locale.ROOT);
                if (!lower.endsWith(".zip") && !lower.endsWith(".md")) {
                    Toast.makeText(getContext(), getContext().getString(R.string.screen_extension_detail_pick_error), Toast.LENGTH_SHORT).show();
                    return;
                }
                showInstallTargetDialog(uri, displayName);
            }

            @Override
            public void onDocumentPickCancelled() {
            }
        });
    }

    private void showInstallTargetDialog(String uri, String displayName) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_sheet_title_install_target));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_target_project), getContext().getString(R.string.screen_extension_detail_target_project_desc), () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_PROJECT, uri, displayName);
        });
        addActionRow(panel, getContext().getString(R.string.screen_extension_detail_target_global), getContext().getString(R.string.screen_extension_detail_target_global_desc), () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_APP, uri, displayName);
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showCreateSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel(getContext().getString(R.string.screen_extension_detail_create_skill));
        FormTextFieldView name = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_name), "", "android-native-view", null, false, false);
        FormTextFieldView desc = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_desc), "", getContext().getString(R.string.screen_extension_detail_hint_skill_desc), null, false, false);
        FormTextFieldView body = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_content), "", getContext().getString(R.string.screen_extension_detail_hint_content), null, true, false);
        RadioGroup scope = locationGroup();
        panel.addView(name);
        panel.addView(desc, top());
        panel.addView(body, top());
        panel.addView(scope, top());
        panel.addView(actionButton(getContext().getString(R.string.common_create), () -> {
            listener.onCreateSkill(checkedLocation(scope), value(name), value(desc), value(body));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private void showInstallSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel(getContext().getString(R.string.screen_extension_detail_dialog_install_skill));
        String downloadExample = Environment.getExternalStorageDirectory().getPath() + "/Download/skill.zip";
        FormTextFieldView path = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_source_path), "", downloadExample, getContext().getString(R.string.screen_extension_detail_helper_source_path), false, false);
        FormTextFieldView name = new FormTextFieldView(getContext(), getContext().getString(R.string.screen_extension_detail_field_name_optional), "", getContext().getString(R.string.screen_extension_detail_hint_optional_name), null, false, false);
        RadioGroup scope = locationGroup();
        panel.addView(path);
        panel.addView(name, top());
        panel.addView(scope, top());
        panel.addView(actionButton(getContext().getString(R.string.common_install), () -> {
            listener.onInstallSkill(checkedLocation(scope), value(path), value(name));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private RadioGroup locationGroup() {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton project = locationButton(getContext().getString(R.string.screen_extension_detail_position_project), SkillRecord.LOCATION_PROJECT);
        RadioButton app = locationButton(getContext().getString(R.string.screen_extension_detail_position_global), SkillRecord.LOCATION_APP);
        group.addView(project, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        group.addView(app, new RadioGroup.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        project.setChecked(true);
        return group;
    }

    private RadioButton locationButton(String label, String location) {
        RadioButton button = new RadioButton(getContext());
        button.setId(android.view.View.generateViewId());
        button.setText(label);
        button.setTag(location);
        button.setTextColor(LineTheme.TEXT);
        button.setTextSize(LineTheme.FONT_SM);
        return button;
    }

    private String checkedLocation(RadioGroup group) {
        android.view.View checked = group.findViewById(group.getCheckedRadioButtonId());
        Object tag = checked == null ? null : checked.getTag();
        return tag == null ? SkillRecord.LOCATION_PROJECT : String.valueOf(tag);
    }

    private TextView actionButton(String title, Runnable action) {
        TextView view = LineTheme.textMedium(getContext(), title, LineTheme.FONT_MD, LineTheme.ACCENT);
        view.setGravity(Gravity.CENTER);
        view.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 8));
        LineTheme.padding(view, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private void confirmDelete(String rowKind, String id, String title) {
        if ("skills".equals(rowKind)) {
            Dialog dialog = createBottomDialog();
            LinearLayout panel = createBottomPanel();
            addHandle(panel);
            addSheetTitle(panel, title);
            addDivider(panel);
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_multi_select), getContext().getString(R.string.screen_extension_detail_multi_select_desc), () -> {
                dialog.dismiss();
                multiSelectedIds.clear();
                multiSelectedIds.add(id);
                renderInstalledSection();
            });
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_delete), getContext().getString(R.string.screen_extension_detail_delete_desc), () -> {
                dialog.dismiss();
                confirmDeleteOnly(rowKind, id, title);
            }, LineTheme.DANGER);
            addBottomInset(panel);
            showBottomDialog(dialog, panel);
            return;
        }
        if (uiModel != null && uiModel.hasModifyAction()) {
            Dialog dialog = createBottomDialog();
            LinearLayout panel = createBottomPanel();
            addHandle(panel);
            addSheetTitle(panel, title);
            addDivider(panel);
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_modify), getContext().getString(R.string.screen_extension_detail_modify_desc), () -> {
                dialog.dismiss();
                if (uiModel.getAddActionType() == ExtensionKindUiModel.ADD_ACTION_AGENT) {
                    listener.onEditAgent(id);
                } else {
                    listener.onEditMcp(id);
                }
            });
            addActionRow(panel, getContext().getString(R.string.screen_extension_detail_delete), getContext().getString(R.string.screen_extension_detail_delete_desc), () -> {
                dialog.dismiss();
                confirmDeleteOnly(rowKind, id, title);
            }, LineTheme.DANGER);
            addBottomInset(panel);
            showBottomDialog(dialog, panel);
            return;
        }
        confirmDeleteOnly(rowKind, id, title);
    }

    private void confirmDeleteOnly(String rowKind, String id, String title) {
        Dialog dialog = createBottomDialog();
        LinearLayout panel = createBottomPanel();
        addHandle(panel);
        addSheetTitle(panel, getContext().getString(R.string.screen_extension_detail_delete_title));
        TextView desc = LineTheme.text(getContext(), getContext().getString(R.string.screen_extension_detail_delete_confirm, title), LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, getContext().getString(R.string.common_cancel), "", dialog::dismiss);
        addActionRow(panel, getContext().getString(R.string.common_delete), getContext().getString(R.string.screen_extension_detail_delete_confirm_desc), () -> {
            dialog.dismiss();
            listener.onDelete(rowKind, id);
        }, LineTheme.DANGER);
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private Dialog createDialog() {
        Dialog dialog = new Dialog(getContext());
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    private LinearLayout panel(String title) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 16));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        panel.addView(LineTheme.textMedium(getContext(), title, LineTheme.FONT_LG, LineTheme.TEXT));
        return panel;
    }

    private void showPanel(Dialog dialog, LinearLayout panel) {
        dialog.setContentView(panel);
        Window window = dialog.getWindow();
        dialog.setOnShowListener(d -> {
            Window shown = dialog.getWindow();
            if (shown != null) {
                shown.setBackgroundDrawableResource(android.R.color.transparent);
                shown.setLayout(insetDialogWidth(), LayoutParams.WRAP_CONTENT);
            }
        });
        if (window != null) {
            window.setBackgroundDrawableResource(android.R.color.transparent);
        }
        dialog.show();
    }

    private int insetDialogWidth() {
        int width = getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 32);
        return Math.max(LineTheme.dp(getContext(), 280), width);
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

    private LinearLayout.LayoutParams top() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        return params;
    }

    private String value(FormTextFieldView field) {
        return field == null ? "" : field.getInput().getText().toString().trim();
    }

    private static IconButtonView addButton(Context context, ExtensionKindUiModel uiModel, Listener listener) {
        IconButtonView button = new IconButtonView(context, IconButtonView.PLUS);
        button.setIconColor(LineTheme.ACCENT);
        button.setIconSizeDp(36, 19);
        button.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));
        button.setOnClickListener(v -> {
            if (uiModel == null) return;
            switch (uiModel.getAddActionType()) {
                case ExtensionKindUiModel.ADD_ACTION_AGENT:
                    listener.onAddAgent();
                    break;
                case ExtensionKindUiModel.ADD_ACTION_MCP:
                    listener.onAddMcp();
                    break;
                case ExtensionKindUiModel.ADD_ACTION_SKILL:
                    Toast.makeText(context, context.getString(R.string.screen_extension_detail_add_button_toast), Toast.LENGTH_SHORT).show();
                    break;
                default:
                    break;
            }
        });
        return button;
    }
}
