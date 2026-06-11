package cn.lineai.ui.component;

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
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.ExtensionOverviewState;
import cn.lineai.model.SkillRecord;
import cn.lineai.ui.MainChatView;
import cn.lineai.ui.theme.LineTheme;
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

        void onEnabledChanged(String kind, String id, boolean enabled);

        void onDelete(String kind, String id);
    }

    private final String kind;
    private final ExtensionOverviewState state;
    private final Listener listener;

    public ExtensionDetailScreenView(Context context, String kind, ExtensionOverviewState state, Listener listener) {
        super(context, titleFor(kind), listener::onBack, addButton(context, kind, listener));
        this.kind = kind == null ? "" : kind;
        this.state = state == null ? new ExtensionOverviewState(null, null, null) : state;
        this.listener = listener;
        getRightAction().setOnClickListener(v -> handleAdd());
        LinearLayout content = getContent();
        LineTheme.padding(content, 0, 0, 0, 100);

        SettingsSectionView add = new SettingsSectionView(context, isSkills() ? "安装与创建" : "添加");
        add.addRow(new ActionRowView(context, iconFor(this.kind), inlineTitle(this.kind), inlineDesc(this.kind), false, true, this::handleAdd), false);
        content.addView(add, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        SettingsSectionView installed = new SettingsSectionView(context, "已安装");
        renderInstalled(installed);
        content.addView(installed, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void handleAdd() {
        if ("agent".equals(kind)) {
            listener.onAddAgent();
        } else if ("mcp".equals(kind)) {
            listener.onAddMcp();
        } else if ("skills".equals(kind)) {
            showSkillActions();
        } else {
            Toast.makeText(getContext(), "LineCode 扩展后续接入。", Toast.LENGTH_SHORT).show();
        }
    }

    private void renderInstalled(SettingsSectionView installed) {
        if ("agent".equals(kind)) {
            List<ExtensionAgentConfig> agents = state.getAgents();
            if (agents.isEmpty()) {
                installed.addRow(empty("还没有添加 Agent。"), false);
                return;
            }
            for (int i = 0; i < agents.size(); i++) {
                ExtensionAgentConfig agent = agents.get(i);
                installed.addRow(extensionRow("agent", agent.getId(), IconButtonView.BRAIN, agent.getName(),
                        agent.getSlug() + " · " + count(agent.getToolNames().size(), "tools"),
                        agent.isEnabled()), i < agents.size() - 1);
            }
            return;
        }
        if ("mcp".equals(kind)) {
            List<ExtensionMcpConfig> mcps = state.getMcps();
            if (mcps.isEmpty()) {
                installed.addRow(empty("还没有添加 MCP。"), false);
                return;
            }
            for (int i = 0; i < mcps.size(); i++) {
                ExtensionMcpConfig mcp = mcps.get(i);
                installed.addRow(extensionRow("mcp", mcp.getId(), IconButtonView.MCP, mcp.getName(),
                        count(mcp.getTools().size(), "tools") + " · " + mcp.getUrl(),
                        mcp.isEnabled()), i < mcps.size() - 1);
            }
            return;
        }
        if ("skills".equals(kind)) {
            List<SkillRecord> skills = state.getSkills();
            if (skills.isEmpty()) {
                installed.addRow(empty("还没有发现 Skills。可以创建或安装 SKILL.md。"), false);
                return;
            }
            for (int i = 0; i < skills.size(); i++) {
                SkillRecord skill = skills.get(i);
                installed.addRow(extensionRow("skills", skill.getId(), IconButtonView.ARCHIVE, skill.getName(),
                        skill.getLocationLabel() + " · " + skill.getSkillMdPath(),
                        skill.isEnabled()), i < skills.size() - 1);
            }
            return;
        }
        installed.addRow(empty("LineCode 扩展后续接入。"), false);
    }

    private LinearLayout extensionRow(String rowKind, String id, int iconType, String title, String desc, boolean enabled) {
        SwitchRowView row = new SwitchRowView(getContext(), iconType, title, desc, enabled,
                (button, checked) -> listener.onEnabledChanged(rowKind, id, checked));
        row.setOnLongClickListener(v -> {
            confirmDelete(rowKind, id, title);
            return true;
        });
        return row;
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
        addSheetTitle(panel, "Skills");
        addDivider(panel);
        addActionRow(panel, "选择 ZIP/SKILL.md 安装", "选择后再选择项目或全局安装位置", () -> {
            dialog.dismiss();
            chooseSkillDocument();
        });
        addActionRow(panel, "创建 SKILL", "新建 SKILL.md 到项目或全局 Skills 目录", () -> {
            dialog.dismiss();
            showCreateSkillDialog();
        });
        addActionRow(panel, "从本地路径安装", "手动输入目录、SKILL.md 或 .zip 路径", () -> {
            dialog.dismiss();
            showInstallSkillDialog();
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
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
                    Toast.makeText(getContext(), "请选择 .zip 或 SKILL.md", Toast.LENGTH_SHORT).show();
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
        addSheetTitle(panel, "安装到");
        addDivider(panel);
        addActionRow(panel, "当前工作区 .linecode/skills", "随当前项目保存和导出", () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_PROJECT, uri, displayName);
        });
        addActionRow(panel, "应用全局 .linecode/skills", "保存到 LineCode 应用私有全局目录", () -> {
            dialog.dismiss();
            listener.onInstallSkillFromUri(SkillRecord.LOCATION_APP, uri, displayName);
        });
        addBottomInset(panel);
        showBottomDialog(dialog, panel);
    }

    private void showCreateSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel("创建 SKILL");
        FormTextFieldView name = new FormTextFieldView(getContext(), "名称", "", "android-native-view", null, false, false);
        FormTextFieldView desc = new FormTextFieldView(getContext(), "描述", "", "当前项目原生 Android View 规范", null, false, false);
        FormTextFieldView body = new FormTextFieldView(getContext(), "内容", "", "# Skill\n\n## 触发条件\n- ...", null, true, false);
        RadioGroup scope = locationGroup();
        panel.addView(name);
        panel.addView(desc, top());
        panel.addView(body, top());
        panel.addView(scope, top());
        panel.addView(actionButton("创建", () -> {
            listener.onCreateSkill(checkedLocation(scope), value(name), value(desc), value(body));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private void showInstallSkillDialog() {
        Dialog dialog = createDialog();
        LinearLayout panel = panel("安装 SKILL");
        String downloadExample = Environment.getExternalStorageDirectory().getPath() + "/Download/skill.zip";
        FormTextFieldView path = new FormTextFieldView(getContext(), "来源路径", "", downloadExample, "支持目录、SKILL.md 或 .zip。", false, false);
        FormTextFieldView name = new FormTextFieldView(getContext(), "名称（可选）", "", "skill-name", null, false, false);
        RadioGroup scope = locationGroup();
        panel.addView(path);
        panel.addView(name, top());
        panel.addView(scope, top());
        panel.addView(actionButton("安装", () -> {
            listener.onInstallSkill(checkedLocation(scope), value(path), value(name));
            dialog.dismiss();
        }), top());
        showPanel(dialog, panel);
    }

    private RadioGroup locationGroup() {
        RadioGroup group = new RadioGroup(getContext());
        group.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton project = locationButton("项目", SkillRecord.LOCATION_PROJECT);
        RadioButton app = locationButton("全局", SkillRecord.LOCATION_APP);
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
        if ("agent".equals(rowKind) || "mcp".equals(rowKind)) {
            Dialog dialog = createBottomDialog();
            LinearLayout panel = createBottomPanel();
            addHandle(panel);
            addSheetTitle(panel, title);
            addDivider(panel);
            addActionRow(panel, "修改", "编辑配置、提示词、请求头或 tools 列表", () -> {
                dialog.dismiss();
                if ("agent".equals(rowKind)) {
                    listener.onEditAgent(id);
                } else {
                    listener.onEditMcp(id);
                }
            });
            addActionRow(panel, "删除", "从已安装扩展列表移除", () -> {
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
        addSheetTitle(panel, "删除扩展");
        TextView desc = LineTheme.text(getContext(), "确定删除「" + title + "」？", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LineTheme.padding(desc, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        panel.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addDivider(panel);
        addActionRow(panel, "取消", "", dialog::dismiss);
        addActionRow(panel, "删除", "删除后需要重新添加或安装才能使用", () -> {
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

    private boolean isSkills() {
        return "skills".equals(kind);
    }

    private static IconButtonView addButton(Context context, String kind, Listener listener) {
        IconButtonView button = new IconButtonView(context, IconButtonView.PLUS);
        button.setIconColor(LineTheme.ACCENT);
        button.setIconSizeDp(36, 19);
        button.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 18));
        button.setOnClickListener(v -> {
            if ("agent".equals(kind)) {
                listener.onAddAgent();
            } else if ("mcp".equals(kind)) {
                listener.onAddMcp();
            } else if ("skills".equals(kind)) {
                Toast.makeText(context, "点击安装与创建入口", Toast.LENGTH_SHORT).show();
            }
        });
        return button;
    }

    private static String titleFor(String kind) {
        if ("agent".equals(kind)) return "Agent 扩展";
        if ("mcp".equals(kind)) return "MCP 扩展";
        if ("skills".equals(kind)) return "Skills 扩展";
        return "LineCode 扩展";
    }

    private static int iconFor(String kind) {
        if ("agent".equals(kind)) return IconButtonView.BRAIN;
        if ("mcp".equals(kind)) return IconButtonView.MCP;
        if ("skills".equals(kind)) return IconButtonView.ARCHIVE;
        return IconButtonView.PACKAGE;
    }

    private static String inlineTitle(String kind) {
        if ("skills".equals(kind)) return "创建或安装 SKILL";
        if ("linecode".equals(kind)) return "导入 LIP 扩展";
        if ("agent".equals(kind)) return "添加 Agent";
        return "添加 HTTP/S MCP";
    }

    private static String inlineDesc(String kind) {
        if ("skills".equals(kind)) return "创建 SKILL.md，或从本地目录、SKILL.md、ZIP 安装到项目/全局 Skills 目录。";
        if ("linecode".equals(kind)) return "LineCode 原生扩展后续接入。";
        if ("agent".equals(kind)) return "自定义 Agent 可按需启停，关闭后不会参与提示词和工具调用。";
        return "自定义 MCP 可查询 tools/list，保存后作为扩展工具暴露给 AI。";
    }

    private static String count(int value, String suffix) {
        return value + " " + suffix;
    }
}
