package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.ui.theme.LineTheme;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class MemorySettingsScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        MemoryOverviewState getMemoryOverview();

        void onMemorySaved(String id, String scope, String content);

        void onMemoryDeleted(String id);
    }

    private final Listener listener;
    private MemoryOverviewState overview;

    public MemorySettingsScreenView(Context context, Listener listener) {
        super(context, "记忆", listener::onBack, addButton(context));
        this.listener = listener;
        View rightAction = getRightAction();
        if (rightAction != null) {
            rightAction.setOnClickListener(v -> showEditor(null));
        }
        refresh();
    }

    private static IconButtonView addButton(Context context) {
        IconButtonView button = new IconButtonView(context, IconButtonView.PLUS);
        button.setIconColor(LineTheme.ACCENT);
        button.setIconSizeDp(36, 20);
        return button;
    }

    private void refresh() {
        overview = listener.getMemoryOverview();
        LinearLayout content = getContent();
        content.removeAllViews();
        addProjectHint(content);
        addMemorySection("长期记忆", IconButtonView.DATABASE, overview.getLongTerm());
        addMemorySection("项目记忆", IconButtonView.FOLDER_OPEN, overview.getProject());
        addMemorySection("环境记忆", IconButtonView.GLOBE, overview.getEnvironment());
        addWorkingMemorySection();
        addHistorySection();
    }

    private void addProjectHint(LinearLayout content) {
        Context context = content.getContext();
        String project = overview == null || overview.getProjectId().length() == 0 ? "未选择项目" : overview.getProjectId();
        TextView hint = LineTheme.text(context, "当前项目: " + project, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        hintParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        hintParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(hint, hintParams);
    }

    private void addMemorySection(String title, int iconType, List<MemoryOverviewState.Memory> rows) {
        SettingsSectionView section = new SettingsSectionView(getContext(), title + "（" + rows.size() + "）");
        if (rows.isEmpty()) {
            section.addRow(emptyView(), false);
        } else {
            for (int i = 0; i < rows.size(); i++) {
                MemoryOverviewState.Memory memory = rows.get(i);
                ActionRowView row = new ActionRowView(
                        getContext(),
                        iconType,
                        preview(memory.getContent(), 80),
                        memoryDesc(memory),
                        false,
                        true,
                        () -> showTextDialog(title, memoryDetail(memory))
                );
                row.setOnLongClickListener(v -> {
                    showMemoryActions(memory);
                    return true;
                });
                section.addRow(row, i < rows.size() - 1);
            }
        }
        getContent().addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addWorkingMemorySection() {
        List<MemoryOverviewState.WorkingMemory> rows = overview.getShortTerm();
        SettingsSectionView section = new SettingsSectionView(getContext(), "短期记忆（" + rows.size() + "）");
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
                        () -> showTextDialog("短期记忆", workingMemoryDetail(memory))
                );
                section.addRow(row, i < rows.size() - 1);
            }
        }
        getContent().addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addHistorySection() {
        List<MemoryOverviewState.HistoryEntry> rows = overview.getHistory();
        SettingsSectionView section = new SettingsSectionView(getContext(), "聊天索引（" + rows.size() + "）");
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
                        () -> showTextDialog("聊天索引", historyDetail(entry))
                );
                section.addRow(row, i < rows.size() - 1);
            }
        }
        getContent().addView(section, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private TextView emptyView() {
        TextView empty = LineTheme.text(getContext(), "暂无内容", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(empty, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        return empty;
    }

    private void showMemoryActions(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView("记忆操作"));
        panel.addView(actionText("编辑", LineTheme.TEXT, () -> {
            dialog.dismiss();
            showEditor(memory);
        }));
        panel.addView(actionText("删除", LineTheme.DANGER, () -> {
            dialog.dismiss();
            showDeleteConfirm(memory);
        }));
        panel.addView(actionText("取消", LineTheme.TEXT_SECONDARY, dialog::dismiss));
        showPanel(dialog, panel);
    }

    private void showDeleteConfirm(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView("删除记忆"));
        TextView body = bodyView("确定删除这条记忆？\n\n" + preview(memory.getContent(), 120));
        panel.addView(body);
        LinearLayout actions = actionRow();
        actions.addView(dialogButton("取消", LineTheme.TEXT_SECONDARY, dialog::dismiss));
        actions.addView(dialogButton("删除", LineTheme.DANGER, () -> {
            listener.onMemoryDeleted(memory.getId());
            dialog.dismiss();
            refresh();
        }));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private void showEditor(MemoryOverviewState.Memory memory) {
        Dialog dialog = createDialog();
        LinearLayout panel = dialogPanel(getContext());
        panel.addView(titleView(memory == null ? "添加记忆" : "编辑记忆"));

        RadioGroup scopeGroup = new RadioGroup(getContext());
        scopeGroup.setOrientation(RadioGroup.HORIZONTAL);
        String scope = memory == null ? MemoryOverviewState.Memory.SCOPE_USER : memory.getScope();
        RadioButton user = scopeButton("user", MemoryOverviewState.Memory.SCOPE_USER);
        RadioButton project = scopeButton("project", MemoryOverviewState.Memory.SCOPE_PROJECT);
        RadioButton environment = scopeButton("environment", MemoryOverviewState.Memory.SCOPE_ENVIRONMENT);
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
        input.setHint("输入要保存的记忆");
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
        actions.addView(dialogButton("取消", LineTheme.TEXT_SECONDARY, dialog::dismiss));
        actions.addView(dialogButton("保存", LineTheme.ACCENT, () -> {
            String value = input.getText().toString().trim();
            if (value.length() == 0) {
                Toast.makeText(getContext(), "记忆内容不能为空", Toast.LENGTH_SHORT).show();
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
        ScrollView scrollView = new ScrollView(getContext());
        scrollView.setFillViewport(false);
        scrollView.addView(panel, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        dialog.setContentView(scrollView);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            WindowManager.LayoutParams params = new WindowManager.LayoutParams();
            params.copyFrom(window.getAttributes());
            params.width = Math.min(getResources().getDisplayMetrics().widthPixels - LineTheme.dp(getContext(), 32), LineTheme.dp(getContext(), 560));
            params.height = WindowManager.LayoutParams.WRAP_CONTENT;
            window.setAttributes(params);
        }
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
        actions.addView(dialogButton("关闭", LineTheme.ACCENT, dialog::dismiss));
        panel.addView(actions);
        showPanel(dialog, panel);
    }

    private String memoryDesc(MemoryOverviewState.Memory memory) {
        return "来源: " + safe(memory.getSource())
                + " · 使用 " + memory.getUseCount() + " 次"
                + " · " + time(memory.getUpdatedAt());
    }

    private String memoryDetail(MemoryOverviewState.Memory memory) {
        return memory.getContent()
                + "\n\n范围: " + memory.getScope()
                + "\n来源: " + safe(memory.getSource())
                + "\n项目: " + (memory.getProjectId().length() == 0 ? "全局" : memory.getProjectId())
                + "\n置信度: " + String.format(Locale.ROOT, "%.2f", memory.getConfidence())
                + "\n使用次数: " + memory.getUseCount()
                + "\n创建: " + time(memory.getCreatedAt())
                + "\n更新: " + time(memory.getUpdatedAt())
                + "\n上次使用: " + (memory.getLastUsedAt() > 0 ? time(memory.getLastUsedAt()) : "尚未使用");
    }

    private String workingMemoryDetail(MemoryOverviewState.WorkingMemory memory) {
        return memory.getContent()
                + "\n\n来源: " + safe(memory.getSource())
                + "\n项目: " + (memory.getProjectId().length() == 0 ? "全局" : memory.getProjectId())
                + "\n创建: " + time(memory.getCreatedAt())
                + "\n更新: " + time(memory.getUpdatedAt())
                + "\n过期: " + (memory.getExpiresAt() > 0 ? time(memory.getExpiresAt()) : "不过期");
    }

    private String historyDesc(MemoryOverviewState.HistoryEntry entry) {
        String title = entry.getTitle().length() == 0 ? entry.getConversationId() : entry.getTitle();
        return title + " · " + entry.getRole() + " · " + time(entry.getUpdatedAt());
    }

    private String historyDetail(MemoryOverviewState.HistoryEntry entry) {
        return (entry.getTitle().length() == 0 ? "" : "标题: " + entry.getTitle() + "\n")
                + entry.getRole() + ": " + entry.getText()
                + "\n\n对话: " + entry.getConversationId()
                + "\n消息: " + (entry.getMessageId().length() == 0 ? "-" : entry.getMessageId())
                + "\n创建: " + time(entry.getCreatedAt())
                + "\n更新: " + time(entry.getUpdatedAt());
    }

    private String preview(String text, int maxChars) {
        String value = safe(text).replace('\n', ' ').replace('\r', ' ').trim();
        while (value.contains("  ")) {
            value = value.replace("  ", " ");
        }
        if (value.length() <= maxChars) {
            return value.length() == 0 ? "空内容" : value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String time(long value) {
        if (value <= 0) {
            return "未知时间";
        }
        return DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(new Date(value));
    }

    private String safe(String value) {
        return value == null ? "" : value;
    }
}
