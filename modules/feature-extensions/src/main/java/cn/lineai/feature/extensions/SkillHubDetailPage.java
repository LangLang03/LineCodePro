package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SkillHubComment;
import cn.lineai.core.model.SkillHubDetail;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.skillhub.SkillHubApi;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/** SkillHub detail: hero, metadata, safety, SKILL.md body, install and comments. */
public final class SkillHubDetailPage extends SettingsPage {

    private final Scheduler scheduler;
    private final SkillStore store;
    private final SkillHubApi api = new SkillHubApi();
    private final String slug;
    private final LinearLayout body;
    private final LinearLayout commentsHost;
    private final EditText commentInput;
    private SkillHubDetail detail;
    private LinearLayout installButton;
    private TextView installLabel;

    public SkillHubDetailPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                              SkillStore store, String slug) {
        super(context, R.string.skillhub_title_detail, tokens, back);
        this.scheduler = scheduler;
        this.store = store;
        this.slug = slug == null ? "" : slug;
        Ui.padding(content, 16, 12, 16, 100);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));
        commentsHost = new LinearLayout(context);
        commentsHost.setOrientation(LinearLayout.VERTICAL);
        commentInput = new EditText(context);
        commentInput.setHint(R.string.skillhub_comment_hint);
        commentInput.setTextSize(Tokens.FONT_SM);
        commentInput.setSingleLine(true);
        commentInput.setIncludeFontPadding(false);
        commentInput.setMinHeight(Ui.dp(context, 44));
        Ui.padding(commentInput, 12, 10, 12, 10);
        theme.bind(t -> {
            commentInput.setTextColor(t.text);
            commentInput.setHintTextColor(t.textTertiary);
            commentInput.setBackground(Ui.roundedStroke(context, t.inputBg, 12, t.borderLight));
        });
        load();
    }

    private void load() {
        body.removeAllViews();
        addBody(spinner(), 16);
        scheduler.io(() -> {
            try {
                SkillHubDetail loaded = api.detail(slug);
                scheduler.main(() -> render(loaded));
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> renderError(message));
            }
        });
    }

    private void renderError(String message) {
        body.removeAllViews();
        LinearLayout section = ExtensionKit.card(getContext(), theme, false);
        TextView text = Ui.text(getContext(), getContext().getString(
                R.string.skillhub_load_failed, message), Tokens.FONT_SM,
                theme.current().danger, Typeface.NORMAL);
        section.addView(text, new LinearLayout.LayoutParams(-1, -2));
        TextView retry = Ui.medium(getContext(),
                getContext().getString(R.string.skillhub_retry_here), Tokens.FONT_SM,
                theme.current().accent);
        retry.setOnClickListener(v -> load());
        section.addView(retry, new LinearLayout.LayoutParams(-1, -2));
        addBody(section, 16);
    }

    private void render(SkillHubDetail loaded) {
        detail = loaded;
        body.removeAllViews();
        addBody(hero(loaded), 0);
        installLabel = Ui.medium(getContext(),
                getContext().getString(R.string.skillhub_select_location_install),
                Tokens.FONT_MD, theme.current().textOnColor);
        installLabel.setGravity(Gravity.CENTER);
        installButton = new LinearLayout(getContext());
        installButton.setGravity(Gravity.CENTER);
        installButton.setClickable(true);
        installButton.setMinimumHeight(Ui.dp(getContext(), 48));
        Ui.padding(installButton, 12, 12, 12, 12);
        installButton.addView(installLabel, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> {
            installLabel.setTextColor(t.textOnColor);
            installButton.setBackground(Ui.rounded(getContext(), t.accent, 12));
        });
        installButton.setOnClickListener(v -> install(loaded));
        addBody(installButton, 16);
        addBody(hubInfo(loaded), 12);
        addBody(hubSafety(loaded), 12);
        addBody(hubMarkdown(loaded), 12);
        addBody(hubComments(loaded), 12);
        loadComments();
    }

    private View hero(SkillHubDetail value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(card, 16, 16, 16, 16);
        LinearLayout head = new LinearLayout(getContext());
        head.setGravity(Gravity.CENTER_VERTICAL);
        FrameLayout artwork = new FrameLayout(getContext());
        IconButton glyph = new IconButton(getContext(), Icons.PACKAGE);
        glyph.setIconSizeDp(64, 32);
        glyph.setClickable(false);
        artwork.addView(glyph, new FrameLayout.LayoutParams(
                Ui.dp(getContext(), 64), Ui.dp(getContext(), 64), Gravity.CENTER));
        head.addView(artwork, new LinearLayout.LayoutParams(
                Ui.dp(getContext(), 64), Ui.dp(getContext(), 64)));
        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(0, -2, 1f);
        copyParams.leftMargin = Ui.dp(getContext(), 16);
        head.addView(copy, copyParams);
        TextView title = Ui.medium(getContext(), value.name, Tokens.FONT_XL,
                theme.current().text);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView author = Ui.text(getContext(), value.author, Tokens.FONT_SM,
                theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(-1, -2);
        authorParams.topMargin = Ui.dp(getContext(), 4);
        copy.addView(author, authorParams);
        card.addView(head, new LinearLayout.LayoutParams(-1, -2));
        TextView description = Ui.text(getContext(), value.description, Tokens.FONT_MD,
                theme.current().textSecondary, Typeface.NORMAL);
        description.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(getContext(), 12);
        card.addView(description, descParams);
        LinearLayout tags = new LinearLayout(getContext());
        tags.setOrientation(LinearLayout.HORIZONTAL);
        tags.addView(ExtensionKit.tag(getContext(), theme,
                getContext().getString(R.string.skillhub_card_meta,
                        String.valueOf(value.downloads), value.version), false),
                new LinearLayout.LayoutParams(-2, -2));
        if (!value.securityStatus.isEmpty()) {
                TextView safe = ExtensionKit.tag(getContext(), theme,
                    "benign".equals(value.securityStatus)
                            ? getContext().getString(R.string.skillhub_safe)
                            : value.securityStatus, "benign".equals(value.securityStatus));
            LinearLayout.LayoutParams safeParams = new LinearLayout.LayoutParams(-2, -2);
            safeParams.leftMargin = Ui.dp(getContext(), 4);
            tags.addView(safe, safeParams);
        }
        LinearLayout.LayoutParams tagParams = new LinearLayout.LayoutParams(-1, -2);
        tagParams.topMargin = Ui.dp(getContext(), 12);
        card.addView(tags, tagParams);
        theme.bind(t -> {
            card.setBackground(Ui.roundedStroke(getContext(), t.surfaceElevated, 14, t.border));
            artwork.setBackground(Ui.rounded(getContext(), t.accentMuted, 14));
            glyph.setIconColor(t.accent);
            title.setTextColor(t.text);
            author.setTextColor(t.textTertiary);
            description.setTextColor(t.textSecondary);
        });
        return card;
    }

    private View hubInfo(SkillHubDetail value) {
        LinearLayout cards = new LinearLayout(getContext());
        cards.setOrientation(LinearLayout.HORIZONTAL);
        cards.addView(metadata(R.string.skillhub_version_label, "v" + value.version),
                new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams countParams = new LinearLayout.LayoutParams(0, -2, 1f);
        countParams.leftMargin = Ui.dp(getContext(), 8);
        cards.addView(metadata(R.string.skillhub_downloads, String.valueOf(value.downloads)),
                countParams);
        List<View> content = new ArrayList<>();
        content.add(cards);
        if (!value.tags.isEmpty()) content.add(tagRow(value.tags));
        return ExtensionKit.hubSection(getContext(), theme, R.string.skillhub_skill_info,
                Icons.BOXES, content);
    }

    private View tagRow(List<String> values) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        int count = 0;
        for (String value : values) {
            if (count++ >= 4) break;
            TextView tag = ExtensionKit.tag(getContext(), theme, value, false);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-2, -2);
            if (count > 1) params.leftMargin = Ui.dp(getContext(), 4);
            row.addView(tag, params);
        }
        return row;
    }

    private View metadata(int label, String value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(LinearLayout.VERTICAL);
        Ui.padding(card, 12, 8, 12, 8);
        TextView name = Ui.text(getContext(), getContext().getString(label), Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView content = Ui.medium(getContext(), value, Tokens.FONT_SM, theme.current().text);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
        contentParams.topMargin = Ui.dp(getContext(), 3);
        card.addView(content, contentParams);
        theme.bind(t -> {
            name.setTextColor(t.textTertiary);
            content.setTextColor(t.text);
            card.setBackground(Ui.rounded(getContext(), t.surfaceLight, 8));
        });
        return card;
    }

    private View hubSafety(SkillHubDetail value) {
        boolean unsafe = !value.securityStatus.isEmpty()
                && !"benign".equals(value.securityStatus);
        String message;
        if ("benign".equals(value.securityStatus)) {
            message = getContext().getString(R.string.skillhub_no_obvious_risk);
        } else if (value.securityStatus.isEmpty()) {
            message = getContext().getString(R.string.skillhub_no_obvious_risk);
        } else {
            message = value.securityStatus;
        }
        TextView text = Ui.text(getContext(), message, Tokens.FONT_SM,
                theme.current().textSecondary, Typeface.NORMAL);
        text.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        theme.bind(t -> text.setTextColor(unsafe ? t.warning : t.textSecondary));
        List<View> content = new ArrayList<>();
        content.add(text);
        return ExtensionKit.hubSection(getContext(), theme, R.string.skillhub_safety_check,
                unsafe ? Icons.CIRCLE_ALERT : Icons.SHIELD_CHECK,
                content);
    }

    private View hubMarkdown(SkillHubDetail value) {
        List<View> content = new ArrayList<>();
        if (value.markdown.isEmpty()) {
            content.add(ExtensionKit.empty(getContext(), theme,
                    R.string.skillhub_no_public_doc));
        } else {
            MarkdownLite markdown = new MarkdownLite(getContext(), theme.current());
            markdown.render(value.markdown, theme.current());
            LinearLayout holder = new LinearLayout(getContext());
            holder.setOrientation(LinearLayout.VERTICAL);
            Ui.padding(holder, 12, 12, 12, 12);
            holder.addView(markdown, new LinearLayout.LayoutParams(-1, -2));
            theme.bind(t -> {
                holder.setBackground(Ui.rounded(getContext(), t.surfaceLight, 10));
                markdown.render(value.markdown, t);
            });
            content.add(holder);
        }
        return ExtensionKit.hubSection(getContext(), theme, R.string.skillhub_skill_md,
                Icons.FILE_TEXT, content);
    }

    private View hubComments(SkillHubDetail value) {
        if (commentsHost.getParent() instanceof android.view.ViewGroup) {
            ((android.view.ViewGroup) commentsHost.getParent()).removeView(commentsHost);
        }
        List<View> content = new ArrayList<>();
        content.add(ExtensionKit.primaryButton(getContext(), theme,
                R.string.skillhub_comment_post, 44, this::postComment));
        content.add(commentsHost);
        content.add(commentInput);
        return ExtensionKit.hubSection(getContext(), theme, R.string.skillhub_comments_title,
                Icons.MESSAGE_CIRCLE, content);
    }

    private void loadComments() {
        scheduler.io(() -> {
            List<SkillHubComment> comments;
            try {
                comments = api.comments(slug);
            } catch (Exception error) {
                comments = new ArrayList<>();
            }
            final List<SkillHubComment> result = comments;
            scheduler.main(() -> renderComments(result));
        });
    }

    private void renderComments(List<SkillHubComment> comments) {
        commentsHost.removeAllViews();
        if (comments == null || comments.isEmpty()) {
            commentsHost.addView(ExtensionKit.empty(getContext(), theme,
                    R.string.skillhub_no_comments_login_first),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (SkillHubComment comment : comments) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            Ui.padding(row, 12, 8, 12, 8);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = Ui.dp(getContext(), 6);
            commentsHost.addView(row, rowParams);
            TextView author = Ui.medium(getContext(), comment.author, Tokens.FONT_XS,
                    theme.current().textSecondary);
            row.addView(author, new LinearLayout.LayoutParams(-1, -2));
            TextView content = Ui.text(getContext(), comment.content, Tokens.FONT_SM,
                    theme.current().text, Typeface.NORMAL);
            LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(-1, -2);
            contentParams.topMargin = Ui.dp(getContext(), 2);
            row.addView(content, contentParams);
            theme.bind(t -> {
                author.setTextColor(t.textSecondary);
                content.setTextColor(t.text);
                row.setBackground(Ui.rounded(getContext(), t.surfaceLight, 10));
            });
        }
    }

    private void postComment() {
        String content = commentInput.getText() == null ? "" : commentInput.getText().toString();
        if (content.isEmpty() || content.length() > 500) return;
        scheduler.io(() -> {
            try {
                SkillHubSession session = new SkillHubSession();
                if (!session.signedIn()) {
                    throw new IllegalStateException(
                            getContext().getString(R.string.skillhub_error_not_logged_in));
                }
                session.postComment(slug, content);
                scheduler.main(() -> {
                    commentInput.setText("");
                    Toast.makeText(getContext(), R.string.skillhub_comment_submitted,
                            Toast.LENGTH_SHORT).show();
                    loadComments();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> Toast.makeText(getContext(), message,
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void install(SkillHubDetail value) {
        RemoteSkillInstall.run(scheduler, store,
                () -> api.download(value.slug, value.version), value.slug + ".zip",
                new RemoteSkillInstall.Callback() {
                    public void onSuccess() {
                        installButton.setEnabled(false);
                        installLabel.setText(getContext().getString(R.string.skillhub_installed));
                        Toast.makeText(getContext(), R.string.skillhub_install_success,
                                Toast.LENGTH_SHORT).show();
                    }

                    public void onError(String message) {
                        Toast.makeText(getContext(), getContext().getString(
                                R.string.skillhub_load_failed, message),
                                Toast.LENGTH_LONG).show();
                    }
                });
    }

    private View spinner() {
        FrameLayout frame = new FrameLayout(getContext());
        Ui.padding(frame, 16, 16, 16, 16);
        ProgressBar bar = new ProgressBar(getContext());
        bar.setIndeterminate(true);
        frame.addView(bar, new FrameLayout.LayoutParams(Ui.dp(getContext(), 24),
                Ui.dp(getContext(), 24), Gravity.CENTER));
        return frame;
    }

    private void addBody(View view, int topDp) {
        ExtensionKit.topMargin(getContext(), view, topDp);
        body.addView(view);
    }
}
