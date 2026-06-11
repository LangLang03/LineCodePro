package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.McpRequestHeader;
import cn.lineai.model.McpToolSummary;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class McpExtensionEditScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        List<McpToolSummary> onQueryTools(String url, List<McpRequestHeader> headers) throws Exception;

        void onSave(ExtensionMcpConfig config);
    }

    private final Handler mainHandler = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final ExtensionMcpConfig editingMcp;
    private final ArrayList<HeaderRow> headerRows = new ArrayList<>();
    private ArrayList<McpToolSummary> queriedTools = new ArrayList<>();

    private FormTextFieldView nameField;
    private FormTextFieldView urlField;
    private SettingsSectionView headersSection;
    private SettingsSectionView querySection;
    private SettingsSectionView toolsSection;
    private boolean querying;
    private boolean queried;

    public McpExtensionEditScreenView(Context context, ExtensionMcpConfig editingMcp, Listener listener) {
        super(context, editingMcp == null ? "添加 MCP" : "修改 MCP", listener::onBack, saveAction(context));
        this.listener = listener;
        this.editingMcp = editingMcp;
        LinearLayout content = getContent();
        nameField = new FormTextFieldView(context, "名称", value(editingMcp == null ? "" : editingMcp.getName()),
                "例如：公司 MCP 服务", null, false, false);
        urlField = new FormTextFieldView(context, "HTTP/S 地址", value(editingMcp == null ? "" : editingMcp.getUrl()),
                "https://example.com/mcp", "查询会请求 tools/list 并展示 MCP 工具列表。", false, false);
        addForm(content, "连接信息", nameField, urlField);

        headersSection = new SettingsSectionView(context, "自定义请求头");
        content.addView(headersSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        if (editingMcp != null) {
            for (McpRequestHeader header : editingMcp.getRequestHeaders()) {
                headerRows.add(new HeaderRow(context, header, headerRows, this::renderHeaders));
            }
            queriedTools = new ArrayList<>(editingMcp.getTools());
            queried = !queriedTools.isEmpty();
        }
        renderHeaders();

        querySection = new SettingsSectionView(context, "查询");
        content.addView(querySection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        renderQuery();

        toolsSection = new SettingsSectionView(context, "TOOLS 列表");
        content.addView(toolsSection, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        renderTools();
        getRightAction().setOnClickListener(v -> save());
    }

    private void renderHeaders() {
        headersSection.removeAllRows();
        headersSection.addRow(new ActionRowView(getContext(), IconButtonView.PLUS, "添加请求头",
                "查询和调用 MCP tools 时会附带这些请求头。", false, false, () -> {
                    headerRows.add(new HeaderRow(getContext(), null, headerRows, this::renderHeaders));
                    renderHeaders();
                }), !headerRows.isEmpty());
        for (int i = 0; i < headerRows.size(); i++) {
            headersSection.addRow(headerRows.get(i), i < headerRows.size() - 1);
        }
    }

    private void queryTools() {
        if (querying) {
            return;
        }
        String url = fieldValue(urlField);
        if (!validUrl(url)) {
            Toast.makeText(getContext(), "MCP 地址必须以 http:// 或 https:// 开头", Toast.LENGTH_SHORT).show();
            return;
        }
        querying = true;
        renderQuery();
        renderTools();
        new Thread(() -> {
            try {
                List<McpToolSummary> tools = listener.onQueryTools(url, headers());
                mainHandler.post(() -> {
                    querying = false;
                    queried = true;
                    queriedTools = new ArrayList<>(tools);
                    renderQuery();
                    renderTools();
                    Toast.makeText(getContext(), "已查询到 " + queriedTools.size() + " 个 tools", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                mainHandler.post(() -> {
                    querying = false;
                    queried = true;
                    renderQuery();
                    renderTools();
                    Toast.makeText(getContext(), e.getMessage(), Toast.LENGTH_LONG).show();
                });
            }
        }, "linecode-mcp-query").start();
    }

    private void renderQuery() {
        querySection.removeAllRows();
        if (querying) {
            querySection.addRow(queryBusyRow(), false);
            return;
        }
        String desc = queriedTools.isEmpty()
                ? "填写地址后查询服务暴露的 tools。"
                : "已查询到 " + queriedTools.size() + " 个 tools。";
        querySection.addRow(new ActionRowView(getContext(), IconButtonView.SEARCH, "查询 MCP 列表",
                desc, false, true, this::queryTools), false);
    }

    private void renderTools() {
        toolsSection.removeAllRows();
        int enabledCount = 0;
        for (McpToolSummary tool : queriedTools) {
            if (tool.isEnabled()) {
                enabledCount++;
            }
        }
        toolsSection.setTitle("TOOLS 列表 · 已启用 " + enabledCount + "/" + queriedTools.size());
        if (querying) {
            toolsSection.addRow(stateRow("正在查询 MCP tools...", true), false);
            return;
        }
        if (queriedTools.isEmpty()) {
            toolsSection.addRow(stateRow(queried ? "没有查询到 tools。" : "查询后会在这里显示 tools 列表，可单独开启或关闭。", false), false);
            return;
        }
        for (int i = 0; i < queriedTools.size(); i++) {
            McpToolSummary tool = queriedTools.get(i);
            toolsSection.addRow(new SwitchRowView(getContext(), IconButtonView.MCP, tool.getName(),
                    tool.getDescription().length() == 0 ? "MCP tool" : tool.getDescription(),
                    tool.isEnabled(), (button, checked) -> {
                        setToolEnabled(tool.getName(), checked);
                        renderTools();
                    }), i < queriedTools.size() - 1);
        }
    }

    private View queryBusyRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(getContext(), 68));
        row.setAlpha(0.85f);
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);

        ProgressBar progressBar = new ProgressBar(getContext());
        progressBar.setIndeterminate(true);
        row.addView(progressBar, new LinearLayout.LayoutParams(LineTheme.dp(getContext(), 34), LineTheme.dp(getContext(), 34)));

        LinearLayout textWrap = new LinearLayout(getContext());
        textWrap.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.MD);
        row.addView(textWrap, textParams);

        textWrap.addView(LineTheme.textMedium(getContext(), "查询 MCP 列表", LineTheme.FONT_MD, LineTheme.TEXT));
        TextView desc = LineTheme.text(getContext(), "正在查询服务暴露的 tools。", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(getContext(), 2);
        textWrap.addView(desc, descParams);
        return row;
    }

    private View stateRow(String text, boolean busy) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        row.setMinimumHeight(LineTheme.dp(getContext(), 56));
        LineTheme.padding(row, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        if (busy) {
            ProgressBar progressBar = new ProgressBar(getContext());
            progressBar.setIndeterminate(true);
            row.addView(progressBar, new LinearLayout.LayoutParams(LineTheme.dp(getContext(), 22), LineTheme.dp(getContext(), 22)));
        }
        TextView label = LineTheme.text(getContext(), text, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        label.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        if (busy) {
            labelParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        }
        row.addView(label, labelParams);
        return row;
    }

    private void setToolEnabled(String name, boolean enabled) {
        ArrayList<McpToolSummary> next = new ArrayList<>();
        for (McpToolSummary tool : queriedTools) {
            if (tool.getName().equals(name)) {
                next.add(new McpToolSummary(tool.getName(), enabled, tool.getDescription(), tool.getInputSchemaJson()));
            } else {
                next.add(tool);
            }
        }
        queriedTools = next;
    }

    private void save() {
        String name = fieldValue(nameField);
        String url = fieldValue(urlField);
        if (name.length() == 0 || !validUrl(url)) {
            Toast.makeText(getContext(), "请填写名称和有效 MCP 地址", Toast.LENGTH_SHORT).show();
            return;
        }
        listener.onSave(new ExtensionMcpConfig(
                editingMcp == null ? "" : editingMcp.getId(),
                editingMcp == null || editingMcp.isEnabled(),
                name,
                trimTrailingSlash(url),
                headers(),
                queriedTools,
                editingMcp == null ? 0 : editingMcp.getCreatedAt(),
                editingMcp == null ? 0 : editingMcp.getUpdatedAt()
        ));
    }

    private List<McpRequestHeader> headers() {
        ArrayList<McpRequestHeader> headers = new ArrayList<>();
        for (HeaderRow row : headerRows) {
            McpRequestHeader header = row.header();
            if (header.getName().length() > 0) {
                headers.add(header);
            }
        }
        return headers;
    }

    private static TextView saveAction(Context context) {
        TextView save = LineTheme.textMedium(context, "保存", LineTheme.FONT_MD, LineTheme.ACCENT);
        save.setGravity(Gravity.CENTER);
        return save;
    }

    private void addForm(LinearLayout content, String title, android.view.View first, android.view.View second) {
        Context context = content.getContext();
        LinearLayout group = new LinearLayout(context);
        group.setOrientation(LinearLayout.VERTICAL);
        group.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(group, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        group.addView(first, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams secondParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        secondParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        group.addView(second, secondParams);

        SectionHeaderView header = new SectionHeaderView(context, title);
        LinearLayout.LayoutParams headerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        headerParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        headerParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(header, headerParams);
        LinearLayout.LayoutParams groupParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        groupParams.leftMargin = LineTheme.dp(context, LineTheme.LG);
        groupParams.rightMargin = LineTheme.dp(context, LineTheme.LG);
        content.addView(group, groupParams);
    }

    private String fieldValue(FormTextFieldView field) {
        return field == null ? "" : field.getInput().getText().toString().trim();
    }

    private String value(String input) {
        return input == null ? "" : input;
    }

    private boolean validUrl(String url) {
        String value = url == null ? "" : url.trim().toLowerCase(java.util.Locale.ROOT);
        return value.startsWith("http://") || value.startsWith("https://");
    }

    private String trimTrailingSlash(String url) {
        String value = url == null ? "" : url.trim();
        while (value.endsWith("/") && value.length() > "https://".length()) {
            value = value.substring(0, value.length() - 1);
        }
        return value;
    }

    private static final class HeaderRow extends LinearLayout {
        private final EditText nameInput;
        private final EditText valueInput;

        HeaderRow(Context context, McpRequestHeader header, List<HeaderRow> owner, Runnable onRemove) {
            super(context);
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            LineTheme.padding(this, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
            nameInput = input(context, "名字", header == null ? "" : header.getName());
            valueInput = input(context, "值", header == null ? "" : header.getValue());
            addView(nameInput, new LayoutParams(0, LineTheme.dp(context, 42), 1f));
            LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, LineTheme.dp(context, 42), 1f);
            valueParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            addView(valueInput, valueParams);
            IconButtonView remove = new IconButtonView(context, IconButtonView.TRASH_2);
            remove.setIconColor(LineTheme.TEXT_TERTIARY);
            remove.setIconSizeDp(34, 16);
            remove.setOnClickListener(v -> {
                if (owner != null) {
                    owner.remove(this);
                }
                if (onRemove != null) {
                    onRemove.run();
                }
            });
            LinearLayout.LayoutParams removeParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 34), LineTheme.dp(context, 42));
            removeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            addView(remove, removeParams);
        }

        McpRequestHeader header() {
            return new McpRequestHeader(nameInput.getText().toString(), valueInput.getText().toString());
        }

        private static EditText input(Context context, String hint, String value) {
            EditText input = new EditText(context);
            input.setSingleLine(true);
            input.setText(value == null ? "" : value);
            input.setHint(hint);
            input.setHintTextColor(LineTheme.TEXT_TERTIARY);
            input.setTextColor(LineTheme.TEXT);
            input.setTextSize(LineTheme.FONT_SM);
            input.setIncludeFontPadding(false);
            input.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_LIGHT, 8, LineTheme.BORDER_LIGHT));
            input.setPadding(LineTheme.dp(context, LineTheme.MD), 0, LineTheme.dp(context, LineTheme.MD), 0);
            return input;
        }
    }
}
