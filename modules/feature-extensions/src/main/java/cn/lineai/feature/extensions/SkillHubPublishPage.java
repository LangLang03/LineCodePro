package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.Skill;
import cn.lineai.data.api.SkillStore;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Publish one local SKILL.md to SkillHub. */
public final class SkillHubPublishPage extends SettingsPage {

    /** Completion callback invoked after a successful publish. */
    public interface Listener {
        /** Invoked on the main thread after the skill was published successfully. */
        void onPublished();
    }

    private final Scheduler scheduler;
    private final Listener listener;
    private final List<Skill> skills;
    private final TextView selected;
    private final FormField slug;
    private final FormField displayName;
    private final FormField version;
    private final LinearLayout publish;
    private final TextView publishLabel;
    private int index;

    /**
     * Builds the publish page and preselects the first local skill.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param scheduler scheduler enforcing the I/O-to-main threading contract
     * @param store skill store listing the publishable local skills
     * @param listener completion callback for successful publish
     */
    public SkillHubPublishPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                               SkillStore store, Listener listener) {
        super(context, R.string.skillhub_publish_title, tokens, back);
        this.scheduler = scheduler;
        this.listener = listener;
        this.skills = new ArrayList<>(store.list());
        Ui.padding(content, 16, 16, 16, 100);

        content.addView(ExtensionKit.notice(context, theme, cn.lineai.ui.kit.Icons.SHIELD_CHECK,
                R.string.skillhub_publish_notice), new LinearLayout.LayoutParams(-1, -2));

        selected = Ui.medium(context, "", Tokens.FONT_MD, tokens.text);
        selected.setGravity(Gravity.CENTER_VERTICAL);
        selected.setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(selected, 12, 12, 12, 12);
        selected.setClickable(true);
        selected.setOnClickListener(v -> selectNext());
        LinearLayout.LayoutParams selectedParams = new LinearLayout.LayoutParams(-1, -2);
        selectedParams.topMargin = Ui.dp(context, 16);
        content.addView(selected, selectedParams);
        theme.bind(t -> {
            selected.setTextColor(t.text);
            selected.setBackground(Ui.roundedStroke(context, t.inputBg, 12, t.borderLight));
        });

        slug = field(R.string.skillhub_slug_label, R.string.skillhub_slug_hint);
        displayName = field(R.string.skillhub_display_name_label,
                R.string.skillhub_display_name_hint);
        version = field(R.string.skillhub_version_label, R.string.skillhub_version_hint);
        content.addView(slug, formParams());
        content.addView(displayName, formParams());
        content.addView(version, formParams());

        publishLabel = Ui.medium(context,
                context.getString(R.string.skillhub_publish_to_hub), Tokens.FONT_MD,
                tokens.textOnColor);
        publishLabel.setGravity(Gravity.CENTER);
        publish = new LinearLayout(context);
        publish.setGravity(Gravity.CENTER);
        publish.setClickable(true);
        publish.setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(publish, 12, 12, 12, 12);
        publish.addView(publishLabel, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            publishLabel.setTextColor(t.textOnColor);
            publish.setBackground(Ui.rounded(context, t.accent, 12));
        });
        publish.setOnClickListener(v -> publish());
        LinearLayout.LayoutParams publishParams = new LinearLayout.LayoutParams(-1, -2);
        publishParams.topMargin = Ui.dp(context, 16);
        content.addView(publish, publishParams);
        select(0);
    }

    /** Creates an empty form field with a label and hint resource. */
    private FormField field(int label, int hint) {
        return ExtensionKit.field(getContext(), theme, label, "", hint, false);
    }

    /** Builds the layout parameters spacing one form field below the previous. */
    private LinearLayout.LayoutParams formParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), 12);
        return params;
    }

    /** Advances the skill selection cyclically and refreshes the form defaults. */
    private void selectNext() {
        if (skills.isEmpty()) return;
        select((index + 1) % skills.size());
    }

    /** Applies the selection, seeding slug, display name, and version when still empty. */
    private void select(int value) {
        index = value;
        if (skills.isEmpty()) {
            selected.setText(getContext().getString(R.string.skillhub_no_publishable_skills));
            publish.setEnabled(false);
            publish.setAlpha(0.45f);
            return;
        }
        Skill skill = skills.get(index);
        selected.setText(skill.name);
        if (slug.value().isEmpty()) slug.input().setText(defaultSlug(skill));
        if (displayName.value().isEmpty()) displayName.input().setText(skill.name);
        if (version.value().isEmpty()) version.input().setText("1.0.0");
    }

    /** Derives a lowercase dash-separated slug from the skill name. */
    private static String defaultSlug(Skill skill) {
        String source = skill.name.toLowerCase(java.util.Locale.ROOT);
        StringBuilder out = new StringBuilder();
        for (int index = 0; index < source.length(); index++) {
            char ch = source.charAt(index);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if ((ch == ' ' || ch == '_' || ch == '-') && out.length() > 0
                    && out.charAt(out.length() - 1) != '-') {
                out.append('-');
            }
        }
        return out.length() == 0 ? "skill" : out.toString();
    }

    /** Validates the form, publishes on I/O while signed in, and reports the outcome on main. */
    private void publish() {
        if (skills.isEmpty()) return;
        String slugValue = slug.value();
        String nameValue = displayName.value();
        String versionValue = version.value();
        if (slugValue.isEmpty() || nameValue.isEmpty() || versionValue.isEmpty()) {
            Toast.makeText(getContext(), R.string.screen_agent_save_require,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        publish.setEnabled(false);
        publishLabel.setText(getContext().getString(R.string.skillhub_publishing));
        Skill skill = skills.get(index);
        scheduler.io(() -> {
            try {
                SkillHubSession session = new SkillHubSession();
                if (!session.signedIn()) {
                    throw new IllegalStateException(
                            getContext().getString(R.string.skillhub_error_not_logged_in));
                }
                session.publish(skill, slugValue, nameValue, versionValue);
                scheduler.main(() -> {
                    Toast.makeText(getContext(), R.string.skillhub_publish_success,
                            Toast.LENGTH_SHORT).show();
                    listener.onPublished();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> {
                    publish.setEnabled(true);
                    publishLabel.setText(getContext().getString(R.string.skillhub_publish_to_hub));
                    Toast.makeText(getContext(), getContext().getString(
                            R.string.skill_github_failed, message), Toast.LENGTH_LONG).show();
                });
            }
        });
    }
}
