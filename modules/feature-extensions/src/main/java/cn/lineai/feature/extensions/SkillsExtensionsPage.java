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

import cn.lineai.core.model.Skill;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Dialogs;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Refreshable;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Local SKILL.md extension list. */
public final class SkillsExtensionsPage extends SettingsPage implements Refreshable {

    public interface Listener {
        List<Skill> load();

        void setEnabled(String id, boolean enabled);

        void delete(String id);

        void add();

        void edit(Skill skill);

        void importSkill();

        void openHub();

        void openGithub();

        void openLogin();

        void openPublish();
    }

    private final Listener listener;

    public SkillsExtensionsPage(Context context, Tokens tokens, Runnable back, Listener listener) {
        super(context, R.string.screen_extensions_section_skills, tokens, back,
                Icons.PLUS, R.string.screen_skill_add_title, listener::add);
        this.listener = listener;
        Ui.padding(content, 16, 16, 16, 100);
        reload();
    }

    @Override public void onShown() {
        reload();
    }

    public void reload() {
        content.removeAllViews();
        List<Skill> skills = listener.load();
        if (skills.isEmpty()) {
            TextView empty = Ui.text(getContext(),
                    getContext().getString(R.string.screen_skill_list_empty),
                    Tokens.FONT_MD, theme.current().textTertiary, Typeface.NORMAL);
            empty.setGravity(Gravity.CENTER);
            Ui.padding(empty, 16, 48, 16, 48);
            content.addView(empty, new LayoutParams(-1, -2));
            return;
        }
        for (Skill skill : skills) {
            content.addView(row(skill), rowParams());
        }
        content.addView(importCard(), rowParams());
        content.addView(hubCard(), rowParams());
    }

    private LinearLayout hubCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(card, 16, 14, 16, 14);
        card.addView(Ui.bold(context, context.getString(R.string.skillhub_title_store),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 10));
        button.addView(Ui.bold(context, context.getString(R.string.skillhub_title_store),
                Tokens.FONT_SM, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> listener.openHub());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 8);
        card.addView(button, buttonParams);
        LinearLayout github = new LinearLayout(context);
        github.setGravity(Gravity.CENTER);
        github.setClickable(true);
        github.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 10, tokens.border));
        github.addView(Ui.bold(context, context.getString(R.string.skill_github_title),
                Tokens.FONT_SM, tokens.textSecondary), new LinearLayout.LayoutParams(-2, -2));
        github.setOnClickListener(v -> listener.openGithub());
        LinearLayout.LayoutParams githubParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        githubParams.topMargin = Ui.dp(context, 8);
        card.addView(github, githubParams);
        LinearLayout hubActions = new LinearLayout(context);
        hubActions.setOrientation(LinearLayout.HORIZONTAL);
        TextView login = Ui.bold(context,
                context.getString(R.string.skillhub_login_account), Tokens.FONT_XS,
                tokens.textSecondary);
        login.setGravity(Gravity.CENTER);
        login.setClickable(true);
        login.setBackground(Ui.roundedStroke(context, tokens.surfaceLight, 10, tokens.border));
        login.setOnClickListener(v -> listener.openLogin());
        hubActions.addView(login, new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1f));
        TextView publish = Ui.bold(context,
                context.getString(R.string.skillhub_publish), Tokens.FONT_XS,
                tokens.textOnColor);
        publish.setGravity(Gravity.CENTER);
        publish.setClickable(true);
        publish.setBackground(Ui.rounded(context, tokens.accent, 10));
        publish.setOnClickListener(v -> listener.openPublish());
        LinearLayout.LayoutParams publishParams = new LinearLayout.LayoutParams(0, Ui.dp(context, 38), 1f);
        publishParams.leftMargin = Ui.dp(context, 6);
        hubActions.addView(publish, publishParams);
        LinearLayout.LayoutParams hubParams = new LinearLayout.LayoutParams(-1, -2);
        hubParams.topMargin = Ui.dp(context, 8);
        card.addView(hubActions, hubParams);
        theme.bind(t -> card.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.border)));
        return card;
    }

    private LinearLayout importCard() {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(card, 16, 14, 16, 14);
        card.addView(Ui.bold(context,
                context.getString(R.string.screen_extension_detail_dialog_install_skill),
                Tokens.FONT_MD, tokens.text), new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context,
                context.getString(R.string.screen_extension_detail_inline_desc_skills),
                Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        LinearLayout button = new LinearLayout(context);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setBackground(Ui.rounded(context, tokens.accent, 10));
        button.addView(Ui.bold(context, context.getString(R.string.screen_extension_detail_dialog_install_skill),
                Tokens.FONT_SM, tokens.textOnColor), new LinearLayout.LayoutParams(-2, -2));
        button.setOnClickListener(v -> listener.importSkill());
        LinearLayout.LayoutParams buttonParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 42));
        buttonParams.topMargin = Ui.dp(context, 10);
        card.addView(button, buttonParams);
        theme.bind(t -> card.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.border)));
        return card;
    }

    private LinearLayout row(Skill skill) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(row, 14, 12, 12, 12);
        row.setClickable(true);
        row.setOnClickListener(v -> listener.edit(skill));
        row.setOnLongClickListener(v -> {
            Dialogs.confirm(context, theme.current(),
                    context.getString(R.string.screen_skill_delete_title),
                    context.getString(R.string.screen_skill_delete_message, skill.name),
                    context.getString(R.string.common_cancel),
                    context.getString(R.string.common_delete),
                    confirmed -> {
                        if (confirmed) {
                            listener.delete(skill.id);
                            reload();
                        }
                    });
            return true;
        });

        FrameLayout iconBox = new FrameLayout(context);
        iconBox.setBackground(Ui.rounded(context, tokens.accentMuted, 18));
        IconButton icon = new IconButton(context, Icons.BOOK_OPEN);
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
        labels.addView(Ui.bold(context, skill.name, Tokens.FONT_MD, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context, skill.description, Tokens.FONT_XS, tokens.textTertiary,
                Typeface.NORMAL);
        desc.setSingleLine(true);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        desc.setVisibility(skill.description.isEmpty() ? GONE : VISIBLE);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(desc, descParams);

        Switch toggle = new Switch(context);
        toggle.setChecked(skill.enabled);
        int[][] states = new int[][] {
                new int[] {android.R.attr.state_checked},
                new int[] {-android.R.attr.state_checked}
        };
        toggle.setThumbTintList(new ColorStateList(states,
                new int[] {tokens.accent, tokens.textTertiary}));
        toggle.setTrackTintList(new ColorStateList(states,
                new int[] {tokens.accentDim, tokens.surfaceLight}));
        toggle.setOnCheckedChangeListener((button, checked) -> listener.setEnabled(skill.id, checked));
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
