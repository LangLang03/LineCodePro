package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.io.ByteArrayInputStream;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SkillHubComment;
import cn.lineai.core.model.SkillHubDetail;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.skillhub.SkillHubApi;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.markdown.MarkdownLite;
import cn.lineai.ui.tokens.Tokens;

/** SkillHub detail: metadata, SKILL.md and install. */
public final class SkillHubDetailPage extends SettingsPage {

    private final Scheduler scheduler;
    private final SkillStore store;
    private final SkillHubApi api = new SkillHubApi();
    private final String slug;
    private final LinearLayout body;
    private final LinearLayout commentsBox;
    private final FormField commentInput;

    public SkillHubDetailPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                              SkillStore store, String slug) {
        super(context, R.string.skillhub_title_detail, tokens, back);
        this.scheduler = scheduler;
        this.store = store;
        this.slug = slug == null ? "" : slug;
        Ui.padding(content, 16, 16, 16, 100);
        body = new LinearLayout(context);
        body.setOrientation(LinearLayout.VERTICAL);
        content.addView(body, new LinearLayout.LayoutParams(-1, -2));
        commentInput = new FormField(context, theme, R.string.skillhub_comments_title, "",
                context.getString(R.string.skillhub_comment_hint), false);
        commentsBox = new LinearLayout(context);
        commentsBox.setOrientation(LinearLayout.VERTICAL);
        load();
    }

    private void load() {
        scheduler.io(() -> {
            try {
                SkillHubDetail detail = api.detail(slug);
                scheduler.main(() -> render(detail));
            } catch (Exception error) {
                scheduler.main(() -> Toast.makeText(getContext(),
                        getContext().getString(R.string.skillhub_load_failed,
                                error.getMessage() == null
                                        ? error.getClass().getSimpleName() : error.getMessage()),
                        Toast.LENGTH_LONG).show());
            }
        });
    }

    private void render(SkillHubDetail detail) {
        body.removeAllViews();
        Tokens tokens = theme.current();
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(LinearLayout.VERTICAL);
        header.setBackground(Ui.rounded(getContext(), tokens.surfaceElevated, 12));
        Ui.padding(header, 16, 16, 16, 16);
        header.addView(Ui.bold(getContext(), detail.name, Tokens.FONT_XL, tokens.text),
                new LinearLayout.LayoutParams(-1, -2));
        TextView meta = Ui.text(getContext(), detail.author + " · v" + detail.version
                + " · " + detail.downloads, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = Ui.dp(getContext(), 4);
        header.addView(meta, metaParams);
        TextView desc = Ui.text(getContext(), detail.description, Tokens.FONT_SM,
                tokens.textSecondary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(getContext(), 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(getContext(), 8);
        header.addView(desc, descParams);
        if (!detail.securityStatus.isEmpty()) {
            TextView security = Ui.text(getContext(), detail.securityStatus, Tokens.FONT_XS,
                    tokens.success, Typeface.NORMAL);
            LinearLayout.LayoutParams securityParams = new LinearLayout.LayoutParams(-1, -2);
            securityParams.topMargin = Ui.dp(getContext(), 6);
            header.addView(security, securityParams);
        }
        TextView install = Ui.bold(getContext(),
                getContext().getString(R.string.skillhub_install_skill, detail.name),
                Tokens.FONT_MD, tokens.textOnColor);
        install.setGravity(Gravity.CENTER);
        install.setClickable(true);
        install.setBackground(Ui.rounded(getContext(), tokens.accent, 12));
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 44));
        installParams.topMargin = Ui.dp(getContext(), 12);
        header.addView(install, installParams);
        install.setOnClickListener(v -> install(detail, install));
        body.addView(header, new LinearLayout.LayoutParams(-1, -2));

        MarkdownLite markdown = new MarkdownLite(getContext(), tokens);
        markdown.render(detail.markdown, tokens);
        LinearLayout.LayoutParams markdownParams = new LinearLayout.LayoutParams(-1, -2);
        markdownParams.topMargin = Ui.dp(getContext(), 16);
        body.addView(markdown, markdownParams);
        addComments();
        loadComments();
    }

    private void addComments() {
        Tokens tokens = theme.current();
        TextView title = Ui.bold(getContext(),
                getContext().getString(R.string.skillhub_comments_title), Tokens.FONT_MD,
                tokens.text);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(-1, -2);
        titleParams.topMargin = Ui.dp(getContext(), 22);
        body.addView(title, titleParams);
        body.addView(commentsBox, new LinearLayout.LayoutParams(-1, -2));
        body.addView(commentInput, new LinearLayout.LayoutParams(-1, -2));
        TextView post = Ui.bold(getContext(),
                getContext().getString(R.string.skillhub_comment_post), Tokens.FONT_MD,
                tokens.textOnColor);
        post.setGravity(Gravity.CENTER);
        post.setClickable(true);
        post.setBackground(Ui.rounded(getContext(), tokens.accent, 12));
        post.setOnClickListener(v -> postComment(post));
        LinearLayout.LayoutParams postParams = new LinearLayout.LayoutParams(-1, Ui.dp(getContext(), 42));
        postParams.topMargin = Ui.dp(getContext(), 8);
        body.addView(post, postParams);
    }

    private void loadComments() {
        scheduler.io(() -> {
            try {
                java.util.List<SkillHubComment> comments = api.comments(slug);
                scheduler.main(() -> renderComments(comments));
            } catch (Exception ignored) {
                scheduler.main(() -> renderComments(java.util.Collections.emptyList()));
            }
        });
    }

    private void renderComments(java.util.List<SkillHubComment> comments) {
        commentsBox.removeAllViews();
        Tokens tokens = theme.current();
        if (comments.isEmpty()) {
            commentsBox.addView(Ui.text(getContext(),
                    getContext().getString(R.string.skillhub_comments_empty), Tokens.FONT_XS,
                    tokens.textTertiary, Typeface.NORMAL),
                    new LinearLayout.LayoutParams(-1, -2));
            return;
        }
        for (SkillHubComment comment : comments) {
            LinearLayout row = new LinearLayout(getContext());
            row.setOrientation(LinearLayout.VERTICAL);
            row.setBackground(Ui.rounded(getContext(), tokens.surfaceElevated, 10));
            Ui.padding(row, 12, 8, 12, 8);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(-1, -2);
            rowParams.topMargin = Ui.dp(getContext(), 6);
            commentsBox.addView(row, rowParams);
            row.addView(Ui.medium(getContext(), comment.author, Tokens.FONT_XS,
                    tokens.textSecondary), new LinearLayout.LayoutParams(-1, -2));
            row.addView(Ui.text(getContext(), comment.content, Tokens.FONT_SM, tokens.text,
                    Typeface.NORMAL), new LinearLayout.LayoutParams(-1, -2));
        }
    }

    private void postComment(TextView button) {
        String content = commentInput.value();
        if (content.isEmpty() || content.length() > 500) return;
        button.setEnabled(false);
        scheduler.io(() -> {
            try {
                SkillHubSession session = new SkillHubSession();
                if (!session.signedIn()) {
                    throw new IllegalStateException(
                            getContext().getString(R.string.skillhub_error_not_logged_in));
                }
                session.postComment(slug, content);
                scheduler.main(() -> {
                    button.setEnabled(true);
                    commentInput.input().setText("");
                    Toast.makeText(getContext(), R.string.skillhub_comment_submitted,
                            Toast.LENGTH_SHORT).show();
                    loadComments();
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> {
                    button.setEnabled(true);
                    Toast.makeText(getContext(), message, Toast.LENGTH_LONG).show();
                });
            }
        });
    }

    private void install(SkillHubDetail detail, TextView button) {
        button.setEnabled(false);
        button.setText(getContext().getString(R.string.skillhub_installing));
        RemoteSkillInstall.run(scheduler, store,
                () -> api.download(detail.slug, detail.version), detail.slug + ".zip",
                new RemoteSkillInstall.Callback() {
                    public void onSuccess() {
                        button.setText(getContext().getString(R.string.skillhub_installed));
                        Toast.makeText(getContext(), R.string.skillhub_install_success,
                                Toast.LENGTH_SHORT).show();
                    }
                    public void onError(String message) {
                        button.setEnabled(true);
                        button.setText(getContext().getString(R.string.skillhub_install_skill,
                                detail.name));
                        Toast.makeText(getContext(), getContext().getString(
                                R.string.skillhub_load_failed, message), Toast.LENGTH_LONG).show();
                    }
                });
    }
}
