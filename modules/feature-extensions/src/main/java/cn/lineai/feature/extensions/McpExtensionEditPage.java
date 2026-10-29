package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.McpExtension;
import cn.lineai.core.model.McpHeader;
import cn.lineai.core.model.McpToolInfo;
import cn.lineai.engine.ai.McpClient;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit one MCP extension, including headers, tools/list discovery and tools. */
public final class McpExtensionEditPage extends ExtensionPage {

    public interface Listener {
        void onSave(McpExtension extension);

        void onDelete(String id);
    }

    private final Listener listener;
    private final Scheduler scheduler;
    private final McpExtension editing;
    private final FormField name;
    private final FormField url;
    private final LinearLayout headersHost;
    private final LinearLayout queryHost;
    private final LinearLayout toolsHost;
    private final List<String> headerNames = new ArrayList<>();
    private final List<String> headerValues = new ArrayList<>();
    private List<McpToolInfo> tools = new ArrayList<>();
    private boolean queried;
    private boolean querying;

    public McpExtensionEditPage(Context context, Tokens tokens, Runnable back,
                                McpExtension editing, Scheduler scheduler, Listener listener) {
        super(context, tokens, back, R.string.screen_mcp_add_title);
        this.listener = listener;
        this.scheduler = scheduler;
        this.editing = editing == null ? McpExtension.builder().build() : editing;
        for (McpHeader header : this.editing.headers) {
            headerNames.add(header.name);
            headerValues.add(header.value);
        }
        this.tools = new ArrayList<>(this.editing.tools);
        this.queried = !this.editing.id.isEmpty();
        header(R.string.screen_mcp_add_title, ExtensionKit.saveAction(context, theme, this::save));

        name = ExtensionKit.field(context, theme, R.string.screen_mcp_field_name,
                this.editing.name, R.string.screen_mcp_hint_name, false);
        url = ExtensionKit.field(context, theme, R.string.screen_mcp_field_http_url,
                this.editing.url, R.string.screen_mcp_hint_url, false);
        headersHost = new LinearLayout(context);
        headersHost.setOrientation(LinearLayout.VERTICAL);
        queryHost = new LinearLayout(context);
        queryHost.setOrientation(LinearLayout.VERTICAL);
        toolsHost = new LinearLayout(context);
        toolsHost.setOrientation(LinearLayout.VERTICAL);

        SettingsSection connection = section(R.string.screen_mcp_form_title);
        connection.row(form(name, 16, 12, false), false, 0);
        connection.row(form(url, 0, 16, true), false, 0);
        content.addView(headersHost, new LinearLayout.LayoutParams(-1, -2));
        content.addView(queryHost, new LinearLayout.LayoutParams(-1, -2));
        content.addView(toolsHost, new LinearLayout.LayoutParams(-1, -2));
        renderHeaders();
        renderQuery();
        renderTools();
        if (!this.editing.id.isEmpty()) renderDelete();
    }

    private LinearLayout form(FormField field, int topDp, int bottomDp, boolean helper) {
        LinearLayout wrapper = new LinearLayout(getContext());
        wrapper.setOrientation(LinearLayout.VERTICAL);
        int side = Ui.dp(getContext(), 16);
        wrapper.setPadding(side, Ui.dp(getContext(), topDp), side, Ui.dp(getContext(), bottomDp));
        wrapper.addView(field, new LinearLayout.LayoutParams(-1, -2));
        if (helper) ExtensionKit.hint(getContext(), theme, wrapper, R.string.screen_mcp_helper_url);
        return wrapper;
    }

    private void renderHeaders() {
        headersHost.removeAllViews();
        SettingsSection section = ExtensionKit.section(getContext(), theme,
                R.string.screen_mcp_section_headers);
        headersHost.addView(section, new LinearLayout.LayoutParams(-1, -2));
        cn.lineai.ui.kit.PreferenceRow add = ExtensionKit.action(getContext(), theme, Icons.PLUS,
                R.string.screen_mcp_add_header, R.string.screen_mcp_add_header_desc, 68,
                () -> {
                    headerNames.add("");
                    headerValues.add("");
                    renderHeaders();
                });
        add.chevron(false);
        section.row(add, false, 0);
        for (int index = 0; index < headerNames.size(); index++) {
            section.row(headerRow(index), false, 0);
        }
    }

    private LinearLayout headerRow(int index) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 16, 12, 16, 12);
        EditText key = compactField(headerNames.get(index), R.string.screen_mcp_header_name, index, true);
        EditText value = compactField(headerValues.get(index), R.string.screen_mcp_header_value_hint,
                index, false);
        LinearLayout.LayoutParams keyParams = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 42), 1f);
        row.addView(key, keyParams);
        LinearLayout.LayoutParams valueParams = new LinearLayout.LayoutParams(0, Ui.dp(getContext(), 42), 1f);
        valueParams.leftMargin = Ui.dp(getContext(), 8);
        row.addView(value, valueParams);
        IconButton trash = new IconButton(getContext(), Icons.TRASH_2);
        trash.setIconSizeDp(34, 16);
        trash.setContentDescription(getContext().getString(R.string.common_delete));
        trash.setOnClickListener(v -> {
            headerNames.remove(index);
            headerValues.remove(index);
            renderHeaders();
        });
        LinearLayout.LayoutParams trashParams = new LinearLayout.LayoutParams(
                Ui.dp(getContext(), 34), Ui.dp(getContext(), 42));
        trashParams.leftMargin = Ui.dp(getContext(), 8);
        row.addView(trash, trashParams);
        theme.bind(t -> trash.setIconColor(t.textTertiary));
        return row;
    }

    private EditText compactField(String value, int hint, int index, boolean key) {
        EditText input = new EditText(getContext());
        input.setText(value == null ? "" : value);
        input.setHint(hint);
        input.setTextSize(Tokens.FONT_SM);
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        input.setMinHeight(Ui.dp(getContext(), 42));
        input.setPadding(Ui.dp(getContext(), 10), Ui.dp(getContext(), 8),
                Ui.dp(getContext(), 10), Ui.dp(getContext(), 8));
        input.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {
            }

            @Override public void onTextChanged(CharSequence s, int start, int before, int count) {
            }

            @Override public void afterTextChanged(Editable s) {
                String text = s == null ? "" : s.toString();
                if (index >= headerNames.size()) return;
                if (key) {
                    headerNames.set(index, text);
                } else {
                    headerValues.set(index, text);
                }
            }
        });
        theme.bind(t -> {
            input.setTextColor(t.text);
            input.setHintTextColor(t.textTertiary);
            input.setBackground(Ui.roundedStroke(getContext(), Color.TRANSPARENT, 8, t.borderLight));
        });
        return input;
    }

    private void renderQuery() {
        queryHost.removeAllViews();
        SettingsSection section = ExtensionKit.section(getContext(), theme,
                R.string.screen_mcp_query_section_title);
        queryHost.addView(section, new LinearLayout.LayoutParams(-1, -2));
        if (querying) {
            section.row(busyRow(), false, 0);
            return;
        }
        String description = tools.isEmpty()
                ? getContext().getString(R.string.screen_mcp_query_empty_desc)
                : getContext().getString(R.string.screen_mcp_query_done_desc, tools.size());
        cn.lineai.ui.kit.PreferenceRow row = ExtensionKit.plainAction(getContext(), theme,
                Icons.SEARCH, R.string.screen_mcp_query_tools, description, 68);
        row.setOnClickListener(v -> query());
        section.row(row, false, 0);
    }

    private LinearLayout busyRow() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(Ui.dp(getContext(), 68));
        Ui.padding(row, 16, 12, 16, 12);
        ProgressBar spinner = new ProgressBar(getContext());
        spinner.setIndeterminate(true);
        row.addView(spinner, new LinearLayout.LayoutParams(Ui.dp(getContext(), 34), Ui.dp(getContext(), 34)));
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(getContext(), 12);
        row.addView(labels, labelParams);
        TextView title = Ui.medium(getContext(), getContext().getString(R.string.screen_mcp_query_tools),
                Tokens.FONT_MD, theme.current().text);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(getContext(), getContext().getString(R.string.screen_mcp_query_busy_desc),
                Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(getContext(), 2);
        labels.addView(desc, descParams);
        theme.bind(t -> {
            title.setTextColor(t.text);
            desc.setTextColor(t.textTertiary);
        });
        row.setAlpha(0.85f);
        return row;
    }

    private void renderTools() {
        toolsHost.removeAllViews();
        int enabled = 0;
        for (McpToolInfo tool : tools) {
            if (tool.enabled) enabled++;
        }
        String title = getContext().getString(R.string.screen_mcp_tools_count, enabled);
        SettingsSection section = ExtensionKit.section(getContext(), theme, title);
        toolsHost.addView(section, new LinearLayout.LayoutParams(-1, -2));
        if (querying) {
            section.row(ExtensionKit.empty(getContext(), theme,
                    R.string.screen_mcp_query_busy), false, 0);
            return;
        }
        if (tools.isEmpty()) {
            section.row(ExtensionKit.empty(getContext(), theme, queried
                    ? R.string.screen_mcp_query_no_tools
                    : R.string.screen_mcp_query_pending), false, 0);
            return;
        }
        for (McpToolInfo tool : tools) {
            String description = tool.description == null || tool.description.isEmpty()
                    ? getContext().getString(R.string.screen_mcp_tool_default_desc)
                    : tool.description;
            ExtensionRows.SwitchRow row = new ExtensionRows.SwitchRow(getContext(), theme,
                    Icons.MCP, tool.name, description);
            row.checked(tool.enabled);
            row.onToggle(checked -> {
                replaceTool(tool, checked);
                renderTools();
            });
            section.row(row, false, 0);
        }
    }

    private void replaceTool(McpToolInfo target, boolean enabled) {
        List<McpToolInfo> updated = new ArrayList<>();
        for (McpToolInfo tool : tools) {
            updated.add(tool.name.equals(target.name) ? tool.withEnabled(enabled) : tool);
        }
        tools = updated;
    }

    private void query() {
        final String endpoint;
        try {
            endpoint = McpClient.normalizeUrl(url.value());
        } catch (IllegalArgumentException error) {
            Toast.makeText(getContext(), R.string.screen_mcp_url_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        querying = true;
        renderQuery();
        renderTools();
        final List<McpHeader> snapshot = headers();
        scheduler.io(() -> {
            try {
                List<McpToolInfo> found = new McpClient().listTools(endpoint, snapshot);
                scheduler.main(() -> applyQuery(found));
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> failQuery(message));
            }
        });
    }

    private void applyQuery(List<McpToolInfo> found) {
        querying = false;
        queried = true;
        List<McpToolInfo> merged = new ArrayList<>();
        for (McpToolInfo tool : found) {
            boolean enabled = true;
            for (McpToolInfo old : tools) {
                if (old.name.equals(tool.name)) enabled = old.enabled;
            }
            merged.add(tool.withEnabled(enabled));
        }
        tools = merged;
        renderQuery();
        renderTools();
        Toast.makeText(getContext(),
                getContext().getString(R.string.screen_mcp_query_done_toast, tools.size()),
                Toast.LENGTH_SHORT).show();
    }

    private void failQuery(String message) {
        querying = false;
        renderQuery();
        renderTools();
        Toast.makeText(getContext(), getContext().getString(
                R.string.screen_mcp_query_failed, message), Toast.LENGTH_SHORT).show();
    }

    private List<McpHeader> headers() {
        List<McpHeader> result = new ArrayList<>();
        for (int index = 0; index < headerNames.size(); index++) {
            String key = headerNames.get(index).trim();
            if (key.isEmpty()) continue;
            result.add(new McpHeader(key, headerValues.get(index)));
        }
        return result;
    }

    private void renderDelete() {
        SettingsSection section = section(R.string.screen_extension_detail_delete_title);
        section.row(ExtensionKit.dangerAction(getContext(), theme, R.string.common_delete,
                () -> ExtensionKit.confirm(getContext(), theme,
                        R.string.screen_mcp_delete_title,
                        getContext().getString(R.string.screen_mcp_delete_message, editing.name),
                        () -> listener.onDelete(editing.id))), false, 0);
    }

    private void save() {
        if (name.value().isEmpty() || url.value().isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_mcp_save_require_name_url,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!queried) {
            Toast.makeText(getContext(), R.string.screen_mcp_save_require_query,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        final String endpoint;
        try {
            endpoint = McpClient.normalizeUrl(url.value());
        } catch (IllegalArgumentException error) {
            Toast.makeText(getContext(), R.string.screen_mcp_url_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        McpExtension saved = editing.toBuilder()
                .name(name.value())
                .url(endpoint)
                .headers(headers())
                .tools(new ArrayList<>(tools))
                .build();
        listener.onSave(saved);
    }
}
