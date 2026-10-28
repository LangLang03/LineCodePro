package cn.lineai.feature.settings;

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

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.MemoryItem;
import cn.lineai.core.model.TodoItem;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.PreferenceRow;
import cn.lineai.ui.kit.ScreenBar;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/** Long-term memory management with scopes, editor and batch deletion. */
public final class MemoryPage extends LinearLayout {

    public interface Listener {
        List<MemoryItem> load();

        void save(String id, String scope, String content);

        void delete(String id);

        void deleteAll(List<String> ids);

        List<TodoItem> loadShortTerm();

        List<Conversation> loadChatIndex();
    }

    private final ThemeBinder theme;
    private final Listener listener;
    private final FrameLayout headerHost;
    private final LinearLayout content;
    private final Set<String> selected = new HashSet<>();
    private List<MemoryItem> memories = new ArrayList<>();

    public MemoryPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context);
        this.theme = new ThemeBinder(tokens);
        this.listener = listener;
        setOrientation(VERTICAL);
        theme.bind(t -> setBackgroundColor(t.bg));
        headerHost = new FrameLayout(context);
        addView(headerHost, new LayoutParams(-1, -2));
        ScrollView scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        Ui.padding(content, 0, 0, 0, 100);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1f));
        backAction = back;
        renderHeader();
        load();
    }

    private final Runnable backAction;

    public void load() {
        memories = new ArrayList<>(listener.load());
        refresh();
    }

    private void renderHeader() {
        Context context = getContext();
        headerHost.removeAllViews();
        ScreenBar bar;
        if (!selected.isEmpty()) {
            bar = new ScreenBar(context, theme, R.string.screen_memory_title,
                    Icons.CLOSE, R.string.common_close, () -> {
                        selected.clear();
                        renderHeader();
                        refresh();
                    },
                    Icons.TRASH_2, R.string.common_delete, this::confirmBatchDelete);
            bar.title(context.getString(R.string.screen_memory_selected_count, selected.size()));
            bar.iconSizes(20, 20);
            bar.rightTint(theme.current().danger);
        } else {
            bar = new ScreenBar(context, theme, R.string.screen_memory_title,
                    Icons.CHEVRON_LEFT, R.string.common_back, backAction,
                    Icons.PLUS, R.string.screen_memory_editor_add, () -> showEditor(null));
            bar.iconSizes(22, 20);
            bar.rightTint(theme.current().accent);
        }
        headerHost.addView(bar, new FrameLayout.LayoutParams(-1, -2));
    }

    private void refresh() {
        content.removeAllViews();
        Tokens tokens = theme.current();
        TextView hint = Ui.text(getContext(),
                getContext().getString(R.string.memory_project_hint,
                        getContext().getString(R.string.screen_memory_project_unselected)),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LayoutParams(-1, -2);
        hintParams.leftMargin = hintParams.rightMargin = Ui.dp(getContext(), 16);
        hintParams.topMargin = Ui.dp(getContext(), 12);
        content.addView(hint, hintParams);

        addSection(R.string.screen_memory_section_long_term, Icons.DATABASE,
                filter(MemoryItem.SCOPE_USER));
        addSection(R.string.screen_memory_section_project, Icons.FOLDER_OPEN,
                filter(MemoryItem.SCOPE_PROJECT));
        addSection(R.string.screen_memory_section_environment, Icons.GLOBE,
                filter(MemoryItem.SCOPE_ENVIRONMENT));
        addSimpleSection(R.string.screen_memory_section_short_term, Icons.SCROLL_TEXT,
                listener.loadShortTerm());
        addSimpleSection(R.string.screen_memory_section_chat_index, Icons.MESSAGE_SQUARE,
                listener.loadChatIndex());
    }

    private List<MemoryItem> filter(String scope) {
        List<MemoryItem> result = new ArrayList<>();
        for (MemoryItem memory : memories) {
            if (memory.scope.equals(scope)) result.add(memory);
        }
        return result;
    }

    private void addSection(int title, int icon, List<MemoryItem> rows) {
        SettingsSection section = new SettingsSection(getContext(),
                MemoryText.sectionTitle(getContext(), title, rows.size()), theme);
        content.addView(section, new LayoutParams(-1, -2));
        if (rows.isEmpty()) {
            section.row(emptyView(), false, 0);
            return;
        }
        for (int i = 0; i < rows.size(); i++) {
            MemoryItem memory = rows.get(i);
            boolean checked = selected.contains(memory.id);
            PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.ACTION,
                    icon, R.string.screen_memory_title, R.string.screen_memory_empty_text);
            row.titleText(MemoryText.label(getContext(), memory));
            row.descriptionText(MemoryText.desc(getContext(), memory));
            row.chevron(selected.isEmpty());
            row.setOnClickListener(v -> {
                if (!selected.isEmpty()) {
                    toggle(memory.id);
                    return;
                }
                showDetail(MemoryText.label(getContext(), memory), MemoryText.detail(getContext(), memory));
            });
            row.setOnLongClickListener(v -> {
                if (!selected.isEmpty()) toggle(memory.id);
                else showActions(memory);
                return true;
            });
            if (checked) row.setBackgroundColor(theme.current().accentMuted);
            section.row(row, i < rows.size() - 1, 68);
        }
    }

    private void addSimpleSection(int title, int icon, List<?> entries) {
        SettingsSection section = new SettingsSection(getContext(),
                MemoryText.sectionTitle(getContext(), title, entries.size()), theme);
        content.addView(section, new LayoutParams(-1, -2));
        if (entries.isEmpty()) {
            section.row(emptyView(), false, 0);
            return;
        }
        for (int i = 0; i < entries.size(); i++) {
            Object entry = entries.get(i);
            String label;
            String desc;
            if (entry instanceof TodoItem) {
                TodoItem todo = (TodoItem) entry;
                label = todo.content;
                desc = getContext().getString(todo.status == TodoItem.Status.COMPLETED
                        ? R.string.memory_todo_completed
                        : todo.status == TodoItem.Status.IN_PROGRESS
                                ? R.string.memory_todo_in_progress : R.string.memory_todo_pending);
            } else {
                Conversation conversation = (Conversation) entry;
                label = conversation.title;
                desc = getContext().getString(R.string.memory_chat_message_count,
                        conversation.messageCount);
            }
            PreferenceRow row = new PreferenceRow(getContext(), theme, PreferenceRow.Style.ACTION,
                    icon, R.string.screen_memory_title, R.string.screen_memory_empty_text);
            row.titleText(label);
            row.descriptionText(desc);
            row.chevron(false);
            section.row(row, i < entries.size() - 1, 68);
        }
    }

    private void emptySection(int title) {
        SettingsSection section = new SettingsSection(getContext(),
                MemoryText.sectionTitle(getContext(), title, 0), theme);
        content.addView(section, new LayoutParams(-1, -2));
        section.row(emptyView(), false, 0);
    }

    private View emptyView() {
        TextView empty = Ui.text(getContext(), getContext().getString(R.string.screen_memory_empty_text),
                Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
        Ui.padding(empty, 16, 16, 16, 16);
        theme.bind(t -> empty.setTextColor(t.textTertiary));
        return empty;
    }

    private void toggle(String id) {
        if (!selected.remove(id)) selected.add(id);
        renderHeader();
        refresh();
    }

    private void showActions(MemoryItem memory) {
        Tokens tokens = theme.current();
        LinearLayout panel = sheetPanel(tokens);
        sheetTitle(panel, tokens, getContext().getString(R.string.screen_memory_action_sheet_title));
        Dialog[] holder = new Dialog[1];
        sheetRow(panel, tokens, getContext().getString(R.string.screen_memory_action_edit),
                tokens.text, () -> {
                    holder[0].dismiss();
                    showEditor(memory);
                });
        sheetRow(panel, tokens, getContext().getString(R.string.screen_memory_action_multi_select),
                tokens.text, () -> {
                    holder[0].dismiss();
                    selected.clear();
                    selected.add(memory.id);
                    renderHeader();
                    refresh();
                });
        sheetRow(panel, tokens, getContext().getString(R.string.screen_memory_action_delete),
                tokens.danger, () -> {
                    holder[0].dismiss();
                    confirmDelete(memory);
                });
        sheetRow(panel, tokens, getContext().getString(R.string.common_cancel),
                tokens.textSecondary, () -> holder[0].dismiss());
        holder[0] = Sheets.showBottom(getContext(), tokens, panel);
    }

    private void confirmDelete(MemoryItem memory) {
        Dialogs.confirm(getContext(), theme.current(),
                getContext().getString(R.string.screen_memory_delete_title),
                MemoryText.deleteBody(getContext(), memory.content),
                getContext().getString(R.string.common_cancel),
                getContext().getString(R.string.common_delete), confirmed -> {
                    if (!confirmed) return;
                    listener.delete(memory.id);
                    selected.remove(memory.id);
                    load();
                    renderHeader();
                });
    }

    private void confirmBatchDelete() {
        if (selected.isEmpty()) return;
        Dialogs.confirm(getContext(), theme.current(),
                getContext().getString(R.string.screen_memory_delete_title),
                getContext().getString(R.string.screen_memory_delete_selected_message, selected.size()),
                getContext().getString(R.string.common_cancel),
                getContext().getString(R.string.common_delete), confirmed -> {
                    if (!confirmed) return;
                    listener.deleteAll(new ArrayList<>(selected));
                    selected.clear();
                    load();
                    renderHeader();
                });
    }

    private void showDetail(String title, String body) {
        Dialogs.confirm(getContext(), theme.current(), title, body,
                getContext().getString(R.string.common_close),
                getContext().getString(R.string.common_close), confirmed -> { });
    }

    private void showEditor(MemoryItem memory) {
        Context context = getContext();
        Tokens tokens = theme.current();
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(VERTICAL);
        panel.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(panel, 16, 16, 16, 16);
        panel.addView(Ui.bold(context, context.getString(memory == null
                        ? R.string.screen_memory_editor_add : R.string.screen_memory_editor_edit),
                Tokens.FONT_LG, tokens.text), new LayoutParams(-1, -2));

        RadioGroup scopes = new RadioGroup(context);
        scopes.setOrientation(RadioGroup.HORIZONTAL);
        String scope = memory == null ? MemoryItem.SCOPE_USER : memory.scope;
        scopes.addView(scopeButton(context, tokens, R.string.screen_memory_scope_user, MemoryItem.SCOPE_USER));
        scopes.addView(scopeButton(context, tokens, R.string.screen_memory_scope_project, MemoryItem.SCOPE_PROJECT));
        scopes.addView(scopeButton(context, tokens, R.string.screen_memory_scope_environment, MemoryItem.SCOPE_ENVIRONMENT));
        if (MemoryItem.SCOPE_PROJECT.equals(scope)) ((RadioButton) scopes.getChildAt(1)).setChecked(true);
        else if (MemoryItem.SCOPE_ENVIRONMENT.equals(scope)) ((RadioButton) scopes.getChildAt(2)).setChecked(true);
        else ((RadioButton) scopes.getChildAt(0)).setChecked(true);
        panel.addView(scopes, new LayoutParams(-1, -2));

        EditText input = new EditText(context);
        input.setText(memory == null ? "" : memory.content);
        input.setHint(R.string.screen_memory_hint);
        input.setHintTextColor(tokens.textTertiary);
        input.setTextColor(tokens.text);
        input.setTextSize(Tokens.FONT_MD);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setMinLines(5);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setBackground(Ui.roundedStroke(context, tokens.inputBg, 8, tokens.borderLight));
        Ui.padding(input, 12, 12, 12, 12);
        LinearLayout.LayoutParams inputParams = new LayoutParams(-1, Ui.dp(context, 132));
        inputParams.topMargin = Ui.dp(context, 12);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams actionsParams = new LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 16);
        panel.addView(actions, actionsParams);
        actions.addView(dialogButton(context, tokens, R.string.common_cancel, tokens.textSecondary,
                dialog::dismiss));
        actions.addView(dialogButton(context, tokens, R.string.common_save, tokens.accent, () -> {
            String value = input.getText().toString().trim();
            if (value.isEmpty()) {
                Toast.makeText(context, R.string.screen_memory_empty_toast, Toast.LENGTH_SHORT).show();
                return;
            }
            listener.save(memory == null ? "" : memory.id, checkedScope(scopes), value);
            dialog.dismiss();
            load();
        }));
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (context.getResources().getDisplayMetrics().widthPixels * 0.9f), -2);
        }
    }

    private RadioButton scopeButton(Context context, Tokens tokens, int label, String scope) {
        RadioButton button = new RadioButton(context);
        button.setText(context.getString(label));
        button.setTextColor(tokens.textSecondary);
        button.setTextSize(Tokens.FONT_SM);
        button.setTag(scope);
        button.setId(View.generateViewId());
        return button;
    }

    private String checkedScope(RadioGroup group) {
        View checked = group.findViewById(group.getCheckedRadioButtonId());
        return checked == null || checked.getTag() == null
                ? MemoryItem.SCOPE_USER : String.valueOf(checked.getTag());
    }

    private TextView dialogButton(Context context, Tokens tokens, int label, int color,
                                  Runnable action) {
        TextView button = Ui.bold(context, context.getString(label), Tokens.FONT_MD, color);
        Ui.padding(button, 12, 8, 12, 8);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private LinearLayout sheetPanel(Tokens tokens) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setBackground(Ui.roundedTop(getContext(), tokens.surfaceElevated, 16));
        return panel;
    }

    private void sheetTitle(LinearLayout panel, Tokens tokens, String title) {
        TextView view = Ui.bold(getContext(), title, Tokens.FONT_LG, tokens.text);
        Ui.padding(view, 16, 0, 16, 12);
        panel.addView(view, new LayoutParams(-1, -2));
    }

    private void sheetRow(LinearLayout panel, Tokens tokens, String label, int color,
                          Runnable action) {
        TextView view = Ui.text(getContext(), label, Tokens.FONT_MD, color, Typeface.NORMAL);
        Ui.padding(view, 16, 14, 16, 14);
        view.setClickable(true);
        view.setOnClickListener(v -> action.run());
        panel.addView(view, new LayoutParams(-1, -2));
    }
}
