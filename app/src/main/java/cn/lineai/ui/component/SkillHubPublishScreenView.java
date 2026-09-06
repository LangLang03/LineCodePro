package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.data.service.SkillHubSessionClient;
import cn.lineai.model.SkillRecord;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.List;

public final class SkillHubPublishScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onPublished();
    }

    private final SkillHubSessionClient client;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Listener listener;
    private final List<SkillRecord> skills = new ArrayList<>();
    private final TextView selected;
    private final EditText slug;
    private final EditText displayName;
    private final EditText version;
    private final TextView publish;
    private final ProgressBar progress;
    private int selectedIndex;

    public SkillHubPublishScreenView(
            Context context, List<SkillRecord> availableSkills, Listener listener) {
        super(context, context.getString(cn.lineai.R.string.skillhub_publish_title), listener::onBack, null);
        this.listener = listener;
        this.client = new SkillHubSessionClient(new ContextResourceProvider(context));
        if (availableSkills != null) {
            for (SkillRecord skill : availableSkills) {
                if (skill != null && !SkillRecord.LOCATION_SSH.equals(skill.getLocation())) {
                    skills.add(skill);
                }
            }
        }

        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);
        addNotice(content);
        selected = fieldButton(content);
        slug = field(content, context.getString(cn.lineai.R.string.skillhub_slug_label),
                context.getString(cn.lineai.R.string.skillhub_slug_hint), false);
        displayName = field(content, context.getString(cn.lineai.R.string.skillhub_display_name_label),
                context.getString(cn.lineai.R.string.skillhub_display_name_hint), false);
        version = field(content, context.getString(cn.lineai.R.string.skillhub_version_label),
                context.getString(cn.lineai.R.string.skillhub_version_hint), false);

        publish = LineTheme.textMedium(context, context.getString(cn.lineai.R.string.skillhub_publish_to_hub),
                LineTheme.FONT_MD, LineTheme.TEXT_ON_COLOR);
        publish.setGravity(Gravity.CENTER);
        publish.setClickable(true);
        publish.setFocusable(true);
        publish.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 12));
        LineTheme.padding(publish, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        publish.setOnClickListener(v -> publish());
        LinearLayout.LayoutParams publishParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        publishParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        content.addView(publish, publishParams);

        progress = new ProgressBar(context);
        progress.setVisibility(GONE);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(progress, progressParams);

        if (skills.isEmpty()) {
            selected.setText(getContext().getString(cn.lineai.R.string.skillhub_no_publishable_skills));
            selected.setEnabled(false);
            publish.setEnabled(false);
            publish.setAlpha(0.45f);
        } else {
            select(0);
        }
    }

    private void addNotice(LinearLayout content) {
        TextView notice = LineTheme.text(getContext(),
                getContext().getString(cn.lineai.R.string.skillhub_publish_notice),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        notice.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(notice, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        content.addView(notice, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private TextView fieldButton(LinearLayout content) {
        TextView value = LineTheme.textMedium(getContext(),
                getContext().getString(cn.lineai.R.string.skillhub_select_local_skill),
                LineTheme.FONT_MD, LineTheme.TEXT);
        value.setGravity(Gravity.CENTER_VERTICAL);
        value.setClickable(true);
        value.setFocusable(true);
        value.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.INPUT_BG, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(value, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        value.setOnClickListener(v -> select((selectedIndex + 1) % skills.size()));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        content.addView(value, params);
        return value;
    }

    private EditText field(
            LinearLayout content, String label, String hint, boolean multiline) {
        TextView title = LineTheme.textMedium(getContext(), label,
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        titleParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        content.addView(title, titleParams);

        EditText input = new EditText(getContext());
        input.setHint(hint);
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setSingleLine(!multiline);
        input.setInputType(multiline
                ? InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                : InputType.TYPE_CLASS_TEXT);
        input.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.INPUT_BG, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(input, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        content.addView(input, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return input;
    }

    private void select(int index) {
        selectedIndex = index;
        SkillRecord skill = skills.get(index);
        selected.setText(skill.getName() + " · " + skill.getLocationLabel());
        displayName.setText(skill.getName());
        String candidate = skill.getName().trim().toLowerCase()
                .replaceAll("[^a-z0-9._-]+", "-")
                .replaceAll("^-+|-+$", "");
        slug.setText(candidate);
        if (version.getText().length() == 0) {
            version.setText("1.0.0");
        }
    }

    private void publish() {
        if (skills.isEmpty()) {
            return;
        }
        setBusy(true);
        SkillRecord skill = skills.get(selectedIndex);
        String slugValue = slug.getText().toString();
        String nameValue = displayName.getText().toString();
        String versionValue = version.getText().toString();
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = client.currentSession();
                if (!session.isAuthenticated()) {
                    throw new IllegalStateException(getContext().getString(cn.lineai.R.string.skillhub_error_not_logged_in));
                }
                client.publish(skill, slugValue, nameValue, versionValue);
                main.post(() -> {
                    setBusy(false);
                    Toast.makeText(getContext(), getContext().getString(cn.lineai.R.string.skillhub_publish_success), Toast.LENGTH_SHORT).show();
                    listener.onPublished();
                });
            } catch (Exception e) {
                main.post(() -> {
                    setBusy(false);
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-publish").start();
    }

    private void setBusy(boolean busy) {
        selected.setEnabled(!busy);
        slug.setEnabled(!busy);
        displayName.setEnabled(!busy);
        version.setEnabled(!busy);
        publish.setEnabled(!busy);
        publish.setText(busy ? getContext().getString(cn.lineai.R.string.skillhub_publishing)
                : getContext().getString(cn.lineai.R.string.skillhub_publish_to_hub));
        progress.setVisibility(busy ? VISIBLE : GONE);
    }

    private String safeMessage(Exception error) {
        String message = error.getMessage();
        return message == null || message.trim().length() == 0
                ? getContext().getString(cn.lineai.R.string.skillhub_error_publish_failed)
                : message;
    }
}
