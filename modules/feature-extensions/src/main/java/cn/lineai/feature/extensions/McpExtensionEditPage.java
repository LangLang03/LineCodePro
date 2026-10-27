package cn.lineai.feature.extensions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.CheckBox;
import android.widget.LinearLayout;
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
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Create or edit one MCP extension, including tools/list discovery. */
public final class McpExtensionEditPage extends SettingsPage {

    public interface Listener {
        void onSave(McpExtension extension);

        void onDelete(String id);
    }

    private final Listener listener;
    private final Scheduler scheduler;
    private final McpExtension editing;
    private final FormField name;
    private final FormField url;
    private final FormField headerName;
    private final FormField headerValue;
    private final LinearLayout headerRows;
    private final LinearLayout toolRows;
    private final TextView queryStatus;
    private final LinearLayout queryButton;
    private final List<McpHeader> headers = new ArrayList<>();
    private List<McpToolInfo> tools = new ArrayList<>();
    private boolean queried;

    public McpExtensionEditPage(Context context, Tokens tokens, Runnable back,
                                McpExtension editing, Scheduler scheduler, Listener listener) {
        super(context, editing == null || editing.id.isEmpty()
                        ? R.string.screen_mcp_add_title : R.string.screen_mcp_edit_title,
                tokens, back);
        this.listener = listener;
        this.scheduler = scheduler;
        this.editing = editing == null ? McpExtension.builder().build() : editing;
        this.headers.addAll(this.editing.headers);
        this.tools = new ArrayList<>(this.editing.tools);
        this.queried = !this.editing.id.isEmpty();
        Ui.padding(content, 16, 16, 16, 100);

        name = field(R.string.screen_mcp_field_name, editing == null ? "" : this.editing.name,
                R.string.screen_mcp_hint_name, false);
        url = field(R.string.screen_mcp_field_http_url, this.editing.url,
                R.string.screen_mcp_hint_url, false);

        LinearLayout connection = card(R.string.screen_mcp_form_title);
        connection.addView(name, formParams(0));
        connection.addView(url, formParams(12));
        TextView helper = Ui.text(context, context.getString(R.string.screen_mcp_helper_url),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        helper.setLineSpacing(Ui.dp(context, 3), 1f);
        connection.addView(helper, formParams(6));
        addCard(connection);

        headerName = field(R.string.screen_mcp_header_name, "", R.string.screen_mcp_header_name,
                false);
        headerValue = field(R.string.screen_mcp_header_value_hint, "", R.string.screen_mcp_header_value_hint,
                false);
        LinearLayout headerCard = card(R.string.screen_mcp_section_headers);
        TextView headerDesc = Ui.text(context,
                context.getString(R.string.screen_mcp_add_header_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        headerCard.addView(headerDesc, new LinearLayout.LayoutParams(-1, -2));
        headerCard.addView(headerName, formParams(10));
        headerCard.addView(headerValue, formParams(8));
        LinearLayout addHeader = actionButton(R.string.screen_mcp_add_header, Icons.PLUS, false,
                this::addHeader);
        headerCard.addView(addHeader, new LinearLayout.LayoutParams(-1, Ui.dp(context, 40)));
        headerRows = new LinearLayout(context);
        headerRows.setOrientation(LinearLayout.VERTICAL);
        headerCard.addView(headerRows, formParams(8));
        renderHeaders();
        addCard(headerCard);

        LinearLayout queryCard = card(R.string.screen_mcp_query_section_title);
        queryStatus = Ui.text(context, context.getString(R.string.screen_mcp_query_empty_desc),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        queryStatus.setLineSpacing(Ui.dp(context, 3), 1f);
        queryCard.addView(queryStatus, new LinearLayout.LayoutParams(-1, -2));
        queryButton = actionButton(R.string.screen_mcp_query_tools, Icons.REFRESH_CW, true,
                this::query);
        queryCard.addView(queryButton, new LinearLayout.LayoutParams(-1, Ui.dp(context, 42)));
        toolRows = new LinearLayout(context);
        toolRows.setOrientation(LinearLayout.VERTICAL);
        queryCard.addView(toolRows, formParams(8));
        renderTools();
        addCard(queryCard);

        content.addView(saveButton(), saveParams());
        if (!this.editing.id.isEmpty()) {
            content.addView(deleteButton(), deleteParams());
        }
    }

    private void addHeader() {
        String headerKey = headerName.value();
        if (headerKey.isEmpty()) return;
        headers.add(new McpHeader(headerKey, headerValue.value()));
        headerName.input().setText("");
        headerValue.input().setText("");
        renderHeaders();
    }

    private void renderHeaders() {
        headerRows.removeAllViews();
        Context context = getContext();
        Tokens tokens = theme.current();
        if (headers.isEmpty()) return;
        for (McpHeader header : new ArrayList<>(headers)) {
            LinearLayout row = new LinearLayout(context);
            row.setGravity(Gravity.CENTER_VERTICAL);
            TextView label = Ui.text(context, header.name,
                    Tokens.FONT_SM, tokens.textSecondary, Typeface.NORMAL);
            row.addView(label, new LinearLayout.LayoutParams(0, -2, 1f));
            TextView remove = Ui.text(context, context.getString(R.string.common_delete),
                    Tokens.FONT_XS, tokens.danger, Typeface.NORMAL);
            remove.setClickable(true);
            Ui.padding(remove, 8, 6, 0, 6);
            remove.setOnClickListener(v -> {
                headers.remove(header);
                renderHeaders();
            });
            row.addView(remove, new LinearLayout.LayoutParams(-2, -2));
            headerRows.addView(row, new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void query() {
        final String endpoint;
        try {
            endpoint = McpClient.normalizeUrl(url.value());
        } catch (IllegalArgumentException error) {
            Toast.makeText(getContext(), R.string.screen_mcp_url_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        setQuerying(true);
        queryStatus.setText(getContext().getString(R.string.screen_mcp_query_busy_desc));
        final List<McpHeader> snapshot = new ArrayList<>(headers);
        scheduler.io(() -> {
            try {
                List<McpToolInfo> found = new McpClient().listTools(endpoint, snapshot);
                scheduler.main(() -> applyQuery(found));
            } catch (Exception error) {
                scheduler.main(() -> failQuery(error));
            }
        });
    }

    private void applyQuery(List<McpToolInfo> found) {
        setQuerying(false);
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
        queryStatus.setText(tools.isEmpty()
                ? getContext().getString(R.string.screen_mcp_query_no_tools)
                : getContext().getString(R.string.screen_mcp_query_done_desc, tools.size()));
        renderTools();
    }

    private void failQuery(Exception error) {
        setQuerying(false);
        queryStatus.setText(getContext().getString(R.string.screen_mcp_query_failed,
                error.getMessage() == null ? error.getClass().getSimpleName() : error.getMessage()));
    }

    private void setQuerying(boolean querying) {
        queryButton.setEnabled(!querying);
        queryButton.setAlpha(querying ? 0.6f : 1f);
    }

    private void renderTools() {
        toolRows.removeAllViews();
        if (tools.isEmpty()) return;
        Context context = getContext();
        Tokens tokens = theme.current();
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        for (McpToolInfo tool : tools) {
            CheckBox box = new CheckBox(context);
            box.setText(tool.name);
            box.setTextSize(Tokens.FONT_SM);
            box.setTextColor(tokens.text);
            box.setButtonTintList(new ColorStateList(states,
                    new int[] {tokens.accent, tokens.border}));
            box.setChecked(tool.enabled);
            box.setOnCheckedChangeListener((button, checked) -> replaceTool(tool, checked));
            toolRows.addView(box, new LinearLayout.LayoutParams(-1, -2));
            theme.bind(t -> {
                box.setTextColor(t.text);
                box.setButtonTintList(new ColorStateList(states,
                        new int[] {t.accent, t.border}));
            });
        }
    }

    private void replaceTool(McpToolInfo target, boolean enabled) {
        List<McpToolInfo> updated = new ArrayList<>();
        for (McpToolInfo tool : tools) {
            updated.add(tool.name.equals(target.name) ? tool.withEnabled(enabled) : tool);
        }
        tools = updated;
    }

    private void save() {
        String nameValue = name.value();
        if (nameValue.isEmpty() || url.value().isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_mcp_save_require_name_url,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (!queried) {
            Toast.makeText(getContext(), R.string.screen_mcp_save_require_query,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        String endpoint;
        try {
            endpoint = McpClient.normalizeUrl(url.value());
        } catch (IllegalArgumentException error) {
            Toast.makeText(getContext(), R.string.screen_mcp_url_invalid, Toast.LENGTH_SHORT).show();
            return;
        }
        McpExtension saved = editing.toBuilder()
                .name(nameValue)
                .url(endpoint)
                .headers(new ArrayList<>(headers))
                .tools(new ArrayList<>(tools))
                .build();
        listener.onSave(saved);
        Toast.makeText(getContext(), R.string.screen_mcp_saved, Toast.LENGTH_SHORT).show();
    }

    private LinearLayout saveButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 12));
        button.addView(Ui.bold(context, context.getString(R.string.common_save),
                Tokens.FONT_MD, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> save());
        return button;
    }

    private LinearLayout deleteButton() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context, tokens.dangerMuted, 12, tokens.danger));
        button.addView(Ui.bold(context, context.getString(R.string.common_delete),
                Tokens.FONT_SM, tokens.danger), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> Dialogs.confirm(context, theme.current(),
                context.getString(R.string.screen_mcp_delete_title),
                context.getString(R.string.screen_mcp_delete_message, editing.name),
                context.getString(R.string.common_cancel),
                context.getString(R.string.common_delete),
                confirmed -> {
                    if (confirmed) listener.onDelete(editing.id);
                }));
        return button;
    }

    private LinearLayout actionButton(int label, int icon, boolean primary, Runnable action) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.roundedStroke(context,
                primary ? tokens.accent : tokens.surfaceLight, 8,
                primary ? tokens.accent : tokens.borderLight));
        button.setOnClickListener(v -> action.run());
        IconButton glyph = new IconButton(context, icon);
        glyph.setIconColor(primary ? tokens.textOnColor : tokens.textSecondary);
        glyph.setIconSizeDp(16, 16);
        glyph.setClickable(false);
        button.addView(glyph, new LinearLayout.LayoutParams(Ui.dp(context, 16), Ui.dp(context, 16)));
        TextView text = Ui.bold(context, context.getString(label), Tokens.FONT_SM,
                primary ? tokens.textOnColor : tokens.textSecondary);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(-2, -2);
        textParams.leftMargin = Ui.dp(context, 4);
        button.addView(text, textParams);
        return button;
    }

    private FormField field(int label, String value, int hint, boolean multiline) {
        return new FormField(getContext(), theme, label, value,
                getContext().getString(hint), false, multiline);
    }

    private LinearLayout card(int title) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.rounded(context, tokens.surfaceElevated, 12));
        Ui.padding(card, 16, 16, 16, 16);
        card.addView(Ui.bold(context, context.getString(title), Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        theme.bind(t -> card.setBackground(Ui.rounded(context, t.surfaceElevated, 12)));
        return card;
    }

    private void addCard(LinearLayout card) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 12);
        content.addView(card, params);
    }

    private LinearLayout.LayoutParams formParams(int topMargin) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), topMargin);
        return params;
    }

    private LinearLayout.LayoutParams saveParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 46));
        params.bottomMargin = Ui.dp(getContext(), 12);
        return params;
    }

    private LinearLayout.LayoutParams deleteParams() {
        return new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 44));
    }
}
