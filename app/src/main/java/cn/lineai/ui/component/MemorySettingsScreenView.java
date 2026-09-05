package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.model.MemoryOverviewState;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

public final class MemorySettingsScreenView extends LinearLayout {
    public interface Listener {
        void onBack();

        MemoryOverviewState getMemoryOverview();

        void onMemorySaved(String id, String scope, String content);

        void onMemoryDeleted(String id);

        void onMemoriesDeleted(List<String> ids);
    }

    private final Listener listener;
    private final FrameLayout headerHost;
    private final LinearLayout content;
    private final Set<String> multiSelectedIds = new HashSet<>();
    private MemoryOverviewState overview;

    public MemorySettingsScreenView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        headerHost = new FrameLayout(context);
        addView(headerHost, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        scrollView.setFillViewport(false);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 0, 0, 0, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        renderHeader();
        refresh();
    }

    private void renderHeader() {
        Context context = getContext();
        headerHost.removeAllViews();
        if (!multiSelectedIds.isEmpty()) {
            IconButtonView close = new IconButtonView(context, IconButtonView.CLOSE);
            close.setIconColor(LineTheme.TEXT);
            close.setIconSizeDp(36, 20);
            close.setOnClickListener(v -> {
                multiSelectedIds.clear();
                renderHeader();
                refresh();
            });

            IconButtonView trash = new IconButtonView(context, IconButtonView.TRASH_2);
            trash.setIconColor(LineTheme.DANGER);
            trash.setIconSizeDp(36, 20);
            trash.setOnClickListener(v -> showBatchDeleteConfirm());
            headerHost.addView(
                    new ScreenHeaderView(context, context.getString(R.string.screen_memory_selected_count, multiSelectedIds.size()), close, trash),
                    new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
            );
            return;
        }

        IconButtonView add = new IconButtonView(context, IconButtonView.PLUS);
        add.setIconColor(LineTheme.ACCENT);
        add.setIconSizeDp(36, 20);
        add.setOnClickListener(v -> showEditor(null));
        headerHost.addView(
                new ScreenHeaderView(context, context.getString(R.string.screen_memory_title), listener::onBack, add),
                new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        );
    }

    private void refresh() {
        overview = listener.getMemoryOverview();
        content.removeAllViews();
        addProjectHint(content);
        addMemorySection(getContext().getString(R.string.screen_memory_section_long_term), IconButtonView.DATABASE, overview.getLongTerm());
        addMemorySection(getContext().getString(R.string.screen_memory_section_project), IconButtonView.FOLDER_OPEN, overview.getProject());
        addMemorySection(getContext().getString(R.string.screen_memory_section_environment), IconButtonView.GLOBE, overview.getEnvironment());
        addWorkingMemorySection();
        addHistorySection();
    }

    private void addProjectHint(LinearLayout host) {
        Context context = host.getContext();
        String project = overview == null || overview.getProjectId().length() == 0
                ? context.getString(R.string.screen_memory_project_unselected)
                : overview.getProjectId();
        TextView hint = LineTheme.text(context,
                context.getString(R.string.screen_memory_current_project_prefix) + project,
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        hintParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        hintParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        host.addView(hint, hintParams);
    }

    private void addMemorySection(String title, int iconType, List<MemoryOverviewState.Memory> rows) {
        SettingsSectionView section = new SettingsSectionView(getContext(), title + "（" + rows.size() + "）");
        if (rows.isEmpty()) {
            section.addRow(emptyView(), false);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                MemoryOverviewState.Memory memory = rows.get(i);
                boolean checked = multiSelectedIds.contains(memory.getId());
                ActionRowView row = new ActionRowView(
                        getContext(),
                        iconType,
                        preview(memory.getContent(), 80),
                        memoryDesc(memory),
                        false,
                        multiSelectedIds.isEmpty(),
                        () -> {
                            if (!multiSelectedIds.isEmpty()) {
                                toggleMultiSelected(memory.getId());
                                return;
                            }
                            showTextDialog(title, memoryDetail(memory));
                        }
                );
                if (checked) {
                    row.setBackgroundColor(LineTheme.ACCENT_MUTED);
                }
                row.setOnLongClickListener(v -> {
                    if (!multiSelectedIds.isEmpty()) {
                        toggleMultiSelected(memory.getId());
                    } else {
                        showMemoryActions(memory);
                    }
                    return true;
                });
                section.addRow(row, i < rows.size() - 1);
            }
        }
        content.addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addWorkingMemorySection() {
        List<MemoryOverviewState.WorkingMemory> rows = overview.getShortTerm();
        SettingsSectionView section = new SettingsSectionView(getContext(), getContext().getString(R.string.screen_memory_section_short_term) + "（" + rows.size() + "）");
        if (rows.isEmpty()) {
            section.addRow(emptyView(), false);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                MemoryOverviewState.WorkingMemory memory = rows.get(i);
                ActionRowView row = new ActionRowView(
                        getContext(),
                        IconButtonView.CLOCK_3,
                        preview(memory.getContent(), 80),
                        time(memory.getUpdatedAt()),
                        false,
                        true,
                        () -> showTextDialog(getContext().getString(R.string.screen_memory_section_short_term), workingMemoryDetail(memory))
                );
                section.addRow(row, i < rows.size() - 1);
            }
        }
        content.addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addHistorySection() {
        List<MemoryOverviewState.HistoryEntry> rows = overview.getHistory();
        SettingsSectionView section = new SettingsSectionView(getContext(), getContext().getString(R.string.screen_memory_section_chat_index) + "（" + rows.size() + "）");
        if (rows.isEmpty()) {
            section.addRow(emptyView(), false);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                MemoryOverviewState.HistoryEntry entry = rows.get(i);
                ActionRowView row = new ActionRowView(
                        getContext(),
                        IconButtonView.BOOK_OPEN,
                        preview(entry.getText(), 80),
                        historyDesc(entry),
                        false,
                        true,
                        () -> showTextDialog(getContext().getString(R.string.screen_memory_section_chat_index), historyDetail(entry))
                );
                section.addRow(row, i < rows.size() - 1);
            }
        }
        content.addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private TextView emptyView() {
        TextView empty = LineTheme.text(getContext(), getContext().getString(R.string.screen_memory_empty_text),
                LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(empty, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return empty;
    }

    private void showMemoryActions(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(getContext().getString(R.string.screen_memory_action_sheet_title)));
        panel.addView(actionText(getContext().getString(R.string.screen_memory_action_edit), LineTheme.TEXT, () -> {
            dialog.dismiss();
            showEditor(memory);
        }));
        panel.addView(actionText(getContext().getString(R.string.screen_memory_action_multi_select), LineTheme.TEXT, () -> {
            dialog.dismiss();
            startMultiSelect(memory.getId());
        }));
        panel.addView(actionText(getContext().getString(R.string.screen_memory_action_delete), LineTheme.DANGER, () -> {
            dialog.dismiss();
            showDeleteConfirm(memory);
        }));
        panel.addView(actionText(getContext().getString(R.string.common_cancel), LineTheme.TEXT_SECONDARY, dialog::dismiss));
        showPanel(dialog, panel);
    }

    private void showDeleteConfirm(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(getContext().getString(R.string.screen_memory_delete_title)));
        TextView body = bodyView(getContext().getString(R.string.screen_memory_action_body, preview(memory.getContent(), 120)));
        panel.addView(body);
        LinearLayout actions = actionRow();
        actions.addView(dialogButton(getContext().getString(R.string.common_cancel), LineTheme.TEXT_SECONDARY, dialog::dismiss));
        actions.addView(dialogButton(getContext().getString(R.string.common_delete), LineTheme.DANGER, () -> {
            listener.onMemoryDeleted(memory.getId());
            dialog.dismiss();
            multiSelectedIds.remove(memory.getId());
            renderHeader();
            refresh();
        }));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private void showBatchDeleteConfirm() {
        if (multiSelectedIds.isEmpty()) {
            return;
        }
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(getContext().getString(R.string.screen_memory_delete_title)));
        TextView body = bodyView(getContext().getString(R.string.screen_memory_delete_selected_message, multiSelectedIds.size()));
        panel.addView(body);
        LinearLayout actions = actionRow();
        actions.addView(dialogButton(getContext().getString(R.string.common_cancel), LineTheme.TEXT_SECONDARY, dialog::dismiss));
        actions.addView(dialogButton(getContext().getString(R.string.common_delete), LineTheme.DANGER, () -> {
            ArrayList<String> ids = new ArrayList<>(multiSelectedIds);
            dialog.dismiss();
            listener.onMemoriesDeleted(ids);
            multiSelectedIds.clear();
            renderHeader();
            refresh();
        }));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private void startMultiSelect(String id) {
        multiSelectedIds.clear();
        multiSelectedIds.add(id);
        renderHeader();
        refresh();
    }

    private void toggleMultiSelected(String id) {
        if (multiSelectedIds.contains(id)) {
            multiSelectedIds.remove(id);
        } else {
            multiSelectedIds.add(id);
        }
        renderHeader();
        refresh();
    }

    private void showEditor(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(memory == null ? getContext().getString(R.string.screen_memory_editor_add) : getContext().getString(R.string.screen_memory_editor_edit)));

        RadioGroup scopeGroup = new RadioGroup(getContext());
        scopeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String scope = memory == null ? MemoryOverviewState.Memory.SCOPE_USER : memory.getScope();
        RadioButton user = scopeButton(getContext().getString(R.string.screen_memory_scope_user), MemoryOverviewState.Memory.SCOPE_USER);
        RadioButton project = scopeButton(getContext().getString(R.string.screen_memory_scope_project), MemoryOverviewState.Memory.SCOPE_PROJECT);
        RadioButton environment = scopeButton(getContext().getString(R.string.screen_memory_scope_environment), MemoryOverviewState.Memory.SCOPE_ENVIRONMENT);
        scopeGroup.addView(user);
        scopeGroup.addView(project);
        scopeGroup.addView(environment);
        if (MemoryOverviewState.Memory.SCOPE_PROJECT.equals(scope)) {
            project.setChecked(true);
        } else if (MemoryOverviewState.Memory.SCOPE_ENVIRONMENT.equals(scope)) {
            environment.setChecked(true);
        } else {
            user.setChecked(true);
        }
        panel.addView(scopeGroup, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        EditText input = new EditText(getContext());
        input.setText(memory == null ? "" : memory.getContent());
        input.setHint(getContext().getString(R.string.screen_memory_hint));
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(LineTheme.roundedStroke(getContext(), LineTheme.INPUT_BG, 8, LineTheme.BORDER_LIGHT));
        input.setPadding(LineTheme.dp(getContext(), LineTheme.MD), LineTheme.dp(getContext(), LineTheme.MD),
                LineTheme.dp(getContext(), LineTheme.MD), LineTheme.dp(getContext(), LineTheme.MD));
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(getContext(), 132));
        inputParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(input, inputParams);

        LinearLayout actions = actionRow();
        actions.addView(dialogButton(getContext().getString(R.string.common_cancel), LineTheme.TEXT_SECONDARY, dialog::dismiss));
        actions.addView(dialogButton(getContext().getString(R.string.common_save), LineTheme.ACCENT, () -> {
            String value = input.getText().toString().trim();
            if (value.length() == 0) {
                Toast.makeText(getContext(), getContext().getString(R.string.screen_memory_empty_toast), Toast.LENGTH_SHORT).show();
                return;
            }
            listener.onMemorySaved(memory == null ? "" : memory.getId(), checkedScope(scopeGroup), value);
            dialog.dismiss();
            refresh();
        }));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private RadioButton scopeButton(String label, String scope) {
        RadioButton button = new RadioButton(getContext());
        button.setText(label);
        button.setTextColor(LineTheme.TEXT_SECONDARY);
        button.setTextSize(LineTheme.FONT_SM);
        button.setTag(scope);
        button.setId(View.generateViewId());
        return button;
    }

    private String checkedScope(RadioGroup group) {
        View checked = group.findViewById(group.getCheckedRadioButtonId());
        Object tag = checked == null ? null : checked.getTag();
        return tag == null ? MemoryOverviewState.Memory.SCOPE_USER : String.valueOf(tag);
    }

    private Dialog createDialog() {
        Dialog dialog = new Dialog(getContext());
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        }
        return dialog;
    }

    private LinearLayout dialogPanel(Context context) {
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return panel;
    }

    private void showPanel(Dialog dialog, LinearLayout panel) {
        DialogBuilder.showInset(dialog, panel);
    }

    private TextView titleView(String text) {
        TextView title = LineTheme.text(getContext(), text, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(getContext(), LineTheme.MD);
        title.setLayoutParams(params);
        return title;
    }

    private TextView bodyView(String text) {
        TextView body = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        body.setLineSpacing(LineTheme.dp(getContext(), 4), 1f);
        return body;
    }

    private TextView actionText(String text, int color, Runnable action) {
        TextView view = LineTheme.text(getContext(), text, LineTheme.FONT_MD, color, Typeface.NORMAL);
        LineTheme.padding(view, LineTheme.SM, LineTheme.MD, LineTheme.SM, LineTheme.MD);
        view.setGravity(Gravity.CENTER_VERTICAL);
        view.setOnClickListener(v -> action.run());
        return view;
    }

    private LinearLayout actionRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        row.setLayoutParams(params);
        return row;
    }

    private TextView dialogButton(String text, int color, Runnable action) {
        TextView button = LineTheme.text(getContext(), text, LineTheme.FONT_MD, color, Typeface.BOLD);
        LineTheme.padding(button, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        button.setGravity(Gravity.CENTER);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void showTextDialog(String title, String body) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(title));
        panel.addView(bodyView(body));
        LinearLayout actions = actionRow();
        actions.addView(dialogButton(getContext().getString(R.string.common_close), LineTheme.ACCENT, dialog::dismiss));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private String memoryDesc(MemoryOverviewState.Memory memory) {
        return getContext().getString(R.string.screen_memory_field_source) + safe(memory.getSource())
                + " · " + getContext().getString(R.string.screen_memory_field_use_count_prefix) + memory.getUseCount()
                + " " + getContext().getString(R.string.screen_memory_field_use_count_suffix)
                + " · " + time(memory.getUpdatedAt());
    }

    private String memoryDetail(MemoryOverviewState.Memory memory) {
        return memory.getContent()
                + "\n\n" + getContext().getString(R.string.screen_memory_field_scope) + memory.getScope()
                + "\n" + getContext().getString(R.string.screen_memory_field_source) + safe(memory.getSource())
                + "\n" + getContext().getString(R.string.screen_memory_field_project) + (memory.getProjectId().length() == 0 ? getContext().getString(R.string.screen_memory_value_global) : memory.getProjectId())
                + "\n" + getContext().getString(R.string.screen_memory_field_confidence) + String.format(Locale.ROOT, "%.2f", memory.getConfidence())
                + "\n" + getContext().getString(R.string.screen_memory_field_use_count) + memory.getUseCount()
                + "\n" + getContext().getString(R.string.screen_memory_field_created) + time(memory.getCreatedAt())
                + "\n" + getContext().getString(R.string.screen_memory_field_updated) + time(memory.getUpdatedAt())
                + "\n" + getContext().getString(R.string.screen_memory_field_last_used) + (memory.getLastUsedAt() > 0 ? time(memory.getLastUsedAt()) : getContext().getString(R.string.screen_memory_value_not_used_yet));
    }

    private String workingMemoryDetail(MemoryOverviewState.WorkingMemory memory) {
        return memory.getContent()
                + "\n\n" + getContext().getString(R.string.screen_memory_field_source) + safe(memory.getSource())
                + "\n" + getContext().getString(R.string.screen_memory_field_project) + (memory.getProjectId().length() == 0 ? getContext().getString(R.string.screen_memory_value_global) : memory.getProjectId())
                + "\n" + getContext().getString(R.string.screen_memory_field_created) + time(memory.getCreatedAt())
                + "\n" + getContext().getString(R.string.screen_memory_field_updated) + time(memory.getUpdatedAt())
                + "\n" + getContext().getString(R.string.screen_memory_field_expires) + (memory.getExpiresAt() > 0 ? time(memory.getExpiresAt()) : getContext().getString(R.string.screen_memory_value_no_expiry));
    }

    private String historyDesc(MemoryOverviewState.HistoryEntry entry) {
        String title = entry.getTitle().length() == 0 ? entry.getConversationId() : entry.getTitle();
        return title + " · " + entry.getRole() + " · " + time(entry.getUpdatedAt());
    }

    private String historyDetail(MemoryOverviewState.HistoryEntry entry) {
        return (entry.getTitle().length() == 0 ? "" : getContext().getString(R.string.screen_memory_field_title) + entry.getTitle() + "\n")
                + entry.getRole() + ": " + entry.getText()
                + "\n\n" + getContext().getString(R.string.screen_memory_field_conversation) + entry.getConversationId()
                + "\n" + getContext().getString(R.string.screen_memory_field_message) + (entry.getMessageId().length() == 0 ? "-" : entry.getMessageId())
                + "\n" + getContext().getString(R.string.screen_memory_field_created) + time(entry.getCreatedAt())
                + "\n" + getContext().getString(R.string.screen_memory_field_updated) + time(entry.getUpdatedAt());
    }

    private String preview(String text, int maxChars) {
        String value = safe(text).replace('\n', ' ').replace('\r', ' ').trim();
        while (value.contains("  ")) {
            value = value.replace("  ", " ");
        }
        if (value.length() <= maxChars) {
            return value.length() == 0 ? getContext().getString(R.string.screen_memory_value_empty) : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String time(long value) {
        if (value <= 0) {
            return getContext().getString(R.string.screen_memory_value_unknown_time);
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
