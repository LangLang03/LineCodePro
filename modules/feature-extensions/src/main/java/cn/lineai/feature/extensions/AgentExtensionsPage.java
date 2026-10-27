package cn.lineai.feature.extensions;

import android.content.Context;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;

import java.util.List;
import java.util.function.Consumer;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Agent extension list: enable, edit and delete custom agents. */
public final class AgentExtensionsPage extends SettingsPage implements Refreshable {

    public interface Listener {
        List<AgentExtension> load();

        void setEnabled(String id, boolean enabled);

        void delete(String id);

        void add();

        void edit(AgentExtension agent);
    }

    private final Listener listener;

    public AgentExtensionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_extensions_section_agent, tokens, back,
                Icons.PLUS, R.string.screen_extension_detail_inline_title_agent, listener::add);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        reload();
    }

    public void reload() {
        content.removeAllViews();
        List<AgentExtension> agents = listener.load();
        if (agents.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.screen_agent_list_empty),
                    Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            Ui.padding(empty, 16, 48, 16, 48);
            content.addView(empty, new LayoutParams(-1, -2));
            return;
        }
        for (AgentExtension agent : agents) {
            content.addView(row(agent), rowParams());
        }
    }

    @Override public void onShown() {
        reload();
    }

    private LinearLayout row(AgentExtension agent) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(row, 14, 12, 12, 12);
        row.setClickable(true);
        row.setOnClickListener(v -> listener.edit(agent));
        row.setOnLongClickListener(v -> {
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.screen_agent_delete_title),
                    context.getString(R.string.screen_agent_delete_message, agent.name),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_delete),
                    confirmed -> {
                        if (confirmed) {
                            listener.delete(agent.id);
                            reload();
                        }
                    });
            return true;
        });

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, Icons.BRAIN);
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
        labels.addView(Ui.bold(context, agent.name.isEmpty() ? agent.slug : agent.name,
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        String description = agent.trigger.isEmpty() ? agent.slug : agent.trigger;
        TextView desc = Ui.text(context, description, Tokens.FONT_XS, tokens.textTertiary,
                Typeface.NORMAL);
        desc.setSingleLine(true);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);

        Switch toggle = new Switch(context);
        toggle.setChecked(agent.enabled);
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
        toggle.setOnCheckedChangeListener((button, checked) -> listener.setEnabled(agent.id, checked));
        row.addView(toggle, new LinearLayout.LayoutParams(-2, -2));

        theme.bind(t -> {
            row.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.border));
            iconBox.setBackground(Ui.rounded(context, t.accentMuted, 18));
            icon.setIconColor(t.accent);
            toggle.setThumbTintList(new ColorStateList(states,
                    new int[] {t.accent, t.textTertiary}));
            toggle.setTrackTintList(new ColorStateList(states,
                    new int[] {t.accentDim, t.surfaceLight}));
        });
        return row;
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }
}
