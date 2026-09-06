package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.app.Dialog;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.widget.EditText;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.data.service.SkillHubClient;
import cn.lineai.data.service.SkillHubSessionClient;
import cn.lineai.model.SkillHubModels;
import cn.lineai.model.SkillRecord;
import cn.lineai.share.ShareHelper;
import cn.lineai.ui.markdown.MarkdownView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.text.DateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public final class SkillStoreDetailScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onLogin();
        void onOfficial(String namespace, String slug);
        void onInstall(String location, String slug, String version) throws Exception;
    }

    private static final int TAB_OVERVIEW = 0;
    private static final int TAB_FILES = 1;
    private static final int TAB_COMMENTS = 2;
    private static final int TAB_VERSIONS = 3;
    private static final int TAB_EVALUATION = 4;
    private static final int TAB_PREVIEW = 5;
    private static final String READING_PREFERENCES = "skill_store_reading";
    private static final String MARKDOWN_TEXT_SCALE = "markdown_text_scale";

    private final String slug;
    private final Listener listener;
    private final SkillHubClient client;
    private final SkillHubSessionClient sessionClient;
    private final SkillIconLoader iconLoader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout body;
    private final ProgressBar progress;
    private SkillHubModels.Detail detail;
    private Boolean starred;
    private boolean starBusy;
    private View starButton;
    private int activeTab = TAB_OVERVIEW;

    public SkillStoreDetailScreenView(Context context, String slug, Listener listener) {
        super(context, context.getString(R.string.skillhub_title_detail), listener::onBack, null);
        this.slug = slug;
        this.listener = listener;
        this.client = new SkillHubClient(new ContextResourceProvider(context));
        this.sessionClient = new SkillHubSessionClient(new ContextResourceProvider(context));
        this.iconLoader = new SkillIconLoader(context);
        body = getContent();
        LineTheme.padding(body, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);
        progress = new ProgressBar(context);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.gravity = Gravity.CENTER_HORIZONTAL;
        params.topMargin = LineTheme.dp(context, LineTheme.LG);
        body.addView(progress, params);
        load();
    }

    private void load() {
        progress.setVisibility(VISIBLE);
        new Thread(() -> {
            try {
                SkillHubModels.Detail value = client.detail(slug);
                main.post(() -> render(value));
            } catch (Exception e) {
                main.post(() -> renderError(e));
            }
        }, "skillhub-detail").start();
    }

    private void render(SkillHubModels.Detail value) {
        detail = value;
        body.removeAllViews();
        addHero(value);
        addActions(value);
        addInstallButton(value);
        addTabs();
        addActiveTab(value);
    }

    private void addHero(SkillHubModels.Detail value) {
        LinearLayout hero = new LinearLayout(getContext());
        hero.setOrientation(VERTICAL);
        hero.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 14, LineTheme.BORDER));
        LineTheme.padding(hero, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        LinearLayout top = new LinearLayout(getContext());
        top.setOrientation(HORIZONTAL);
        top.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.PACKAGE);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(64, 32);
        icon.setClickable(false);
        icon.setFocusable(false);
        icon.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 14));
        top.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 64), LineTheme.dp(getContext(), 64)));
        iconLoader.load(value.getIconUrl(), icon);

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.LG);
        top.addView(copy, copyParams);

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LineTheme.textMedium(getContext(), value.getName(),
                LineTheme.FONT_XL, LineTheme.TEXT);
        title.setMaxLines(2);
        titleRow.addView(title, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        if (value.isVerified()) {
            TextView verified = tag(getString(R.string.skillhub_verified), LineTheme.ACCENT, LineTheme.ACCENT_MUTED);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
            params.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
            titleRow.addView(verified, params);
        }
        copy.addView(titleRow);

        String identity = value.getCanonicalName().length() > 0
                ? value.getCanonicalName() : value.getOwner();
        if (value.getPublisher().length() > 0) {
            identity += (identity.length() == 0 ? "" : " · ") + value.getPublisher();
        }
        if (identity.length() > 0) {
            TextView identityView = LineTheme.text(getContext(), identity,
                    LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            copy.addView(identityView, params);
        }
        hero.addView(top);

        TextView description = LineTheme.text(getContext(), value.getDescription(),
                LineTheme.FONT_MD, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        description.setLineSpacing(LineTheme.dp(getContext(), 4), 1f);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        hero.addView(description, descriptionParams);

        LinearLayout badges = new LinearLayout(getContext());
        badges.setOrientation(HORIZONTAL);
        badges.setGravity(Gravity.CENTER_VERTICAL);
        badges.addView(tag("↓ " + formatCount(value.getDownloads()),
                LineTheme.TEXT_SECONDARY, LineTheme.SURFACE_LIGHT));
        addBadge(badges, "★ " + formatCount(value.getStars()), LineTheme.TEXT_SECONDARY);
        addBadge(badges, "v" + value.getVersion(), LineTheme.TEXT_SECONDARY);
        if ("benign".equalsIgnoreCase(value.getSecurityStatus())) {
            addBadge(badges, getString(R.string.skillhub_safe), LineTheme.ACCENT);
        }
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        badgeParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        hero.addView(badges, badgeParams);
        body.addView(hero);
    }

    private void addActions(SkillHubModels.Detail value) {
        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        actions.setGravity(Gravity.CENTER_VERTICAL);
        actions.addView(actionButton(IconButtonView.COPY, getString(R.string.skillhub_copy_prompt),
                () -> copyPrompt(value)),
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams starParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        starParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        String starLabel = Boolean.TRUE.equals(starred)
                ? getString(R.string.skillhub_unstar) : getString(R.string.skillhub_star);
        starButton = actionButton(IconButtonView.SAVE, starLabel, () -> toggleStar(value));
        starButton.setEnabled(!starBusy);
        starButton.setAlpha(starBusy ? 0.55f : 1f);
        actions.addView(starButton, starParams);
        if (starred == null && !starBusy) {
            loadStarred(value);
        }
        LinearLayout.LayoutParams shareParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        shareParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actions.addView(actionButton(IconButtonView.SHARE,
                getString(R.string.skillhub_share), () -> share(value)), shareParams);
        LinearLayout.LayoutParams officialParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        officialParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actions.addView(actionButton(IconButtonView.EXTERNAL_LINK,
                getString(R.string.skillhub_full_features),
                () -> listener.onOfficial(namespaceHandle(value), value.getSlug())), officialParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        body.addView(actions, params);
    }

    private View actionButton(int iconType, String label, Runnable action) {
        LinearLayout button = new LinearLayout(getContext());
        button.setOrientation(HORIZONTAL);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setContentDescription(label);
        button.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 10, LineTheme.BORDER_LIGHT));
        LineTheme.padding(button, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(20, 16);
        icon.setClickable(false);
        icon.setFocusable(false);
        button.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 20), LineTheme.dp(getContext(), 20)));
        TextView text = LineTheme.textMedium(getContext(), label, LineTheme.FONT_SM, LineTheme.TEXT);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        button.addView(text, textParams);
        button.setOnClickListener(v -> action.run());
        return button;
    }

    private void addTabs() {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        LinearLayout tabs = new LinearLayout(getContext());
        tabs.setOrientation(HORIZONTAL);
        tabs.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 10));
        LineTheme.padding(tabs, LineTheme.XS, LineTheme.XS, LineTheme.XS, LineTheme.XS);
        addTab(tabs, TAB_OVERVIEW, getString(R.string.skillhub_tab_overview));
        addTab(tabs, TAB_FILES, getString(R.string.skillhub_tab_files, detail.getFiles().size()));
        addTab(tabs, TAB_COMMENTS, getString(R.string.skillhub_tab_comments, detail.getComments().size()));
        addTab(tabs, TAB_VERSIONS, getString(R.string.skillhub_tab_versions));
        addTab(tabs, TAB_EVALUATION, getString(R.string.skillhub_tab_evaluation));
        addTab(tabs, TAB_PREVIEW, getString(R.string.skillhub_tab_preview));
        scroll.addView(tabs, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        body.addView(scroll, params);
    }

    private void addTab(LinearLayout tabs, int tabId, String label) {
        boolean active = activeTab == tabId;
        TextView tab = active
                ? LineTheme.textMedium(getContext(), label, LineTheme.FONT_SM, LineTheme.ACCENT)
                : LineTheme.text(getContext(), label, LineTheme.FONT_SM,
                LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        tab.setGravity(Gravity.CENTER);
        tab.setClickable(true);
        tab.setFocusable(true);
        tab.setBackground(active
                ? LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 7) : null);
        LineTheme.padding(tab, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        tab.setOnClickListener(v -> {
            if (activeTab != tabId) {
                activeTab = tabId;
                render(detail);
            }
        });
        tabs.addView(tab);
    }

    private void addActiveTab(SkillHubModels.Detail value) {
        switch (activeTab) {
            case TAB_FILES:
                addFiles(value);
                break;
            case TAB_COMMENTS:
                addComments(value);
                break;
            case TAB_VERSIONS:
                addVersions(value);
                break;
            case TAB_EVALUATION:
                addEvaluation(value);
                break;
            case TAB_PREVIEW:
                addPreview(value);
                break;
            default:
                addOverview(value);
                break;
        }
    }

    private void addOverview(SkillHubModels.Detail value) {
        LinearLayout meta = section(getString(R.string.skillhub_skill_info), IconButtonView.BOXES);
        LinearLayout primary = new LinearLayout(getContext());
        primary.setOrientation(HORIZONTAL);
        primary.addView(metadataCard(getString(R.string.skillhub_category), value.getCategory()),
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams sourceParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        sourceParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        primary.addView(metadataCard(getString(R.string.skillhub_source), sourceName(value.getSource())), sourceParams);
        addSectionContent(meta, primary);

        LinearLayout secondary = new LinearLayout(getContext());
        secondary.setOrientation(HORIZONTAL);
        secondary.addView(metadataCard(getString(R.string.skillhub_tab_versions), "v" + value.getVersion()),
                new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams dateParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        dateParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        secondary.addView(metadataCard(getString(R.string.skillhub_updated), formatDate(value.getUpdatedAt())), dateParams);
        addSectionContent(meta, secondary);
        if (!value.getSubCategories().isEmpty()) {
            addSectionContent(meta, metadataRow(getString(R.string.skillhub_subcategories), join(value.getSubCategories())));
        }
        if (!value.getTags().isEmpty()) {
            addSectionContent(meta, metadataRow(getString(R.string.skillhub_tags), join(value.getTags())));
        }
        addSection(meta);

        addSecurity(value);
        LinearLayout content = section(getString(R.string.skillhub_skill_md), IconButtonView.FILE_TEXT);
        TextView zoomHint = LineTheme.text(getContext(),
                getString(R.string.skillhub_pinch_to_zoom),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        addSectionContent(content, zoomHint);
        if (value.getMarkdown().trim().length() == 0) {
            addSectionContent(content, emptyText(getString(R.string.skillhub_no_public_doc)));
        } else {
            LinearLayout reader = new LinearLayout(getContext());
            reader.setOrientation(VERTICAL);
            reader.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 10));
            LineTheme.padding(reader, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
            MarkdownView markdown = new MarkdownView(getContext());
            markdown.setCodeWrapEnabled(true);
            configureReadingScale(markdown);
            markdown.setLinkHandler(url -> openHttps(url));
            markdown.setMarkdown(value.getMarkdown());
            reader.addView(markdown, new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            addSectionContent(content, reader);
        }
        addSection(content);
    }

    private void addSecurity(SkillHubModels.Detail value) {
        boolean unsafe = value.hasScripts()
                || (!value.getSecurityStatus().isEmpty()
                && !"benign".equalsIgnoreCase(value.getSecurityStatus()));
        LinearLayout section = section(getString(R.string.skillhub_safety_check),
                unsafe ? IconButtonView.CIRCLE_ALERT : IconButtonView.SHIELD_CHECK);
        String text = value.getSecurityStatusText().length() > 0
                ? value.getSecurityStatusText()
                : unsafe ? getString(R.string.skillhub_check_before_use)
                : getString(R.string.skillhub_no_obvious_risk);
        if (value.hasScripts()) {
            text += "\n" + getString(R.string.skillhub_contains_scripts);
        }
        if (value.requiresApiKey()) {
            text += "\n" + getString(R.string.skillhub_requires_api_key_warning);
        }
        addSectionContent(section, LineTheme.text(getContext(), text,
                LineTheme.FONT_SM, unsafe ? LineTheme.WARNING : LineTheme.TEXT_SECONDARY,
                Typeface.NORMAL));
        addSection(section);
    }

    private void addFiles(SkillHubModels.Detail value) {
        LinearLayout section = section(getString(R.string.skillhub_file_list, value.getFiles().size()),
                IconButtonView.FILE_TEXT);
        addSectionContent(section, LineTheme.text(getContext(),
                getString(R.string.skillhub_click_to_preview),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL));
        if (value.getFiles().isEmpty()) {
            addSectionContent(section, emptyText(getString(R.string.skillhub_no_public_files)));
        } else {
            for (SkillHubModels.FileEntry file : value.getFiles()) {
                addSectionContent(section, fileRow(value, file));
            }
        }
        addSection(section);
    }

    private View fileRow(SkillHubModels.Detail value, SkillHubModels.FileEntry file) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setContentDescription(getString(R.string.skillhub_preview_file, file.getPath()));
        row.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 9));
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        IconButtonView icon = new IconButtonView(getContext(), fileIcon(file.getPath()));
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(32, 18);
        icon.setClickable(false);
        icon.setFocusable(false);
        row.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 32), LineTheme.dp(getContext(), 32)));

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        row.addView(copy, copyParams);
        String path = file.getPath();
        int slash = path.lastIndexOf('/');
        String name = slash < 0 ? path : path.substring(slash + 1);
        String directory = slash < 0 ? getString(R.string.skillhub_root_dir) : path.substring(0, slash);
        copy.addView(LineTheme.textMedium(getContext(), name, LineTheme.FONT_SM, LineTheme.TEXT));
        TextView meta = LineTheme.text(getContext(), directory + " · " + formatBytes(file.getSize()),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        metaParams.topMargin = LineTheme.dp(getContext(), 2);
        copy.addView(meta, metaParams);

        IconButtonView chevron = new IconButtonView(getContext(), IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(24, 16);
        chevron.setClickable(false);
        chevron.setFocusable(false);
        row.addView(chevron, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));
        row.setOnClickListener(v -> previewFile(value, file, row));
        return row;
    }

    private int fileIcon(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        if (lower.endsWith(".md") || lower.endsWith(".txt")) {
            return IconButtonView.FILE_TEXT;
        }
        if (lower.endsWith(".json") || lower.endsWith(".js") || lower.endsWith(".java")
                || lower.endsWith(".py") || lower.endsWith(".sh") || lower.endsWith(".xml")
                || lower.endsWith(".yml") || lower.endsWith(".yaml")) {
            return IconButtonView.FILE_CODE;
        }
        return IconButtonView.FILE;
    }

    private void previewFile(SkillHubModels.Detail value, SkillHubModels.FileEntry file, View row) {
        if (!isPreviewable(file.getPath())) {
            Toast.makeText(getContext(), getString(R.string.skillhub_file_not_supported), Toast.LENGTH_SHORT).show();
            return;
        }
        row.setEnabled(false);
        Toast.makeText(getContext(), getString(R.string.skillhub_loading_file), Toast.LENGTH_SHORT).show();
        new Thread(() -> {
            try {
                String content = client.fileContent(value.getSlug(), value.getVersion(), file.getPath());
                main.post(() -> {
                    row.setEnabled(true);
                    showFilePreview(file.getPath(), content);
                });
            } catch (Exception e) {
                main.post(() -> {
                    row.setEnabled(true);
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-file-preview").start();
    }

    private boolean isPreviewable(String path) {
        String lower = path.toLowerCase(Locale.ROOT);
        return lower.endsWith(".md") || lower.endsWith(".txt") || lower.endsWith(".json")
                || lower.endsWith(".js") || lower.endsWith(".ts") || lower.endsWith(".java")
                || lower.endsWith(".py") || lower.endsWith(".sh") || lower.endsWith(".xml")
                || lower.endsWith(".yml") || lower.endsWith(".yaml") || lower.endsWith(".toml")
                || lower.endsWith(".ini") || lower.endsWith(".properties") || lower.endsWith(".csv");
    }

    private void showFilePreview(String path, String content) {
        LinearLayout host = new LinearLayout(getContext());
        host.setOrientation(VERTICAL);
        int padding = LineTheme.dp(getContext(), LineTheme.LG);
        host.setPadding(padding, LineTheme.dp(getContext(), LineTheme.SM),
                padding, LineTheme.dp(getContext(), LineTheme.SM));
        if (path.toLowerCase(Locale.ROOT).endsWith(".md")) {
            host.addView(LineTheme.text(getContext(), getString(R.string.skillhub_pinch_to_zoom),
                    LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL));
            MarkdownView markdown = new MarkdownView(getContext());
            markdown.setCodeWrapEnabled(true);
            configureReadingScale(markdown);
            markdown.setLinkHandler(url -> openHttps(url));
            markdown.setMarkdown(content);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
            host.addView(markdown, params);
        } else {
            TextView text = LineTheme.text(getContext(), content, LineTheme.FONT_SM,
                    LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
            text.setTypeface(Typeface.MONOSPACE);
            text.setTextIsSelectable(true);
            host.addView(text);
        }
        new LineAlertDialog.Builder(getContext())
                .setTitle(path)
                .setView(host)
                .setNegativeButton(getString(R.string.skillhub_close), null)
                .setPositiveButton(getString(R.string.skillhub_copy), (dialog, which) -> {
                    ShareHelper.copy(getContext(), content);
                    Toast.makeText(getContext(),
                            getString(R.string.skillhub_file_copied), Toast.LENGTH_SHORT).show();
                })
                .show();
    }

    private void addComments(SkillHubModels.Detail value) {
        LinearLayout section = section(getString(R.string.skillhub_community_comments),
                IconButtonView.MESSAGE_CIRCLE);
        TextView compose = LineTheme.textMedium(getContext(),
                getString(R.string.skillhub_post_comment),
                LineTheme.FONT_SM, LineTheme.TEXT_ON_COLOR);
        compose.setGravity(Gravity.CENTER);
        compose.setClickable(true);
        compose.setFocusable(true);
        compose.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT, 9));
        LineTheme.padding(compose, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        compose.setOnClickListener(v -> checkSessionForComment(value, null));
        addSectionContent(section, compose);
        if (value.getComments().isEmpty()) {
            addSectionContent(section, emptyText(getString(R.string.skillhub_no_comments_login_first)));
        } else {
            for (SkillHubModels.Comment comment : value.getComments()) {
                addComment(section, comment, 0);
            }
        }
        addSection(section);
    }

    private void checkSessionForComment(
            SkillHubModels.Detail value, SkillHubModels.Comment parent) {
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                main.post(() -> {
                    if (session.isAuthenticated()) {
                        showCommentDialog(value, parent);
                    } else {
                        Toast.makeText(getContext(),
                                getString(R.string.skillhub_login_to_star),
                                Toast.LENGTH_SHORT).show();
                        listener.onLogin();
                    }
                });
            } catch (Exception e) {
                main.post(() -> Toast.makeText(
                        getContext(), safeMessage(e), Toast.LENGTH_LONG).show());
            }
        }, "skillhub-comment-session").start();
    }

    private void showCommentDialog(
            SkillHubModels.Detail value, SkillHubModels.Comment parent) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);
        String dialogTitle = parent == null ? getString(R.string.skillhub_post_comment)
                : getString(R.string.skillhub_reply_to,
                parent.getAuthor().length() == 0 ? getString(R.string.skillhub_user)
                        : parent.getAuthor());
        panel.addView(LineTheme.textMedium(getContext(), dialogTitle,
                LineTheme.FONT_LG, LineTheme.TEXT));
        TextView hint = LineTheme.text(getContext(),
                getString(R.string.skillhub_comment_review_notice),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams hintParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        hintParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
        panel.addView(hint, hintParams);

        EditText input = new EditText(getContext());
        input.setHint(parent == null ? getString(R.string.skillhub_share_experience_hint)
                : getString(R.string.skillhub_write_reply_hint));
        input.setHintTextColor(LineTheme.TEXT_TERTIARY);
        input.setTextColor(LineTheme.TEXT);
        input.setTextSize(LineTheme.FONT_MD);
        input.setGravity(Gravity.TOP | Gravity.START);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_MULTI_LINE
                | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        input.setMinLines(4);
        input.setMaxLines(8);
        input.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.INPUT_BG, 10, LineTheme.BORDER_LIGHT));
        LineTheme.padding(input, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        LinearLayout.LayoutParams inputParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        inputParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(input, inputParams);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        TextView cancel = dialogButton(getString(R.string.skillhub_cancel), false);
        TextView submit = dialogButton(getString(R.string.skillhub_submit_comment), true);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams submitParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        submitParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actions.addView(submit, submitParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(actions, actionsParams);

        Dialog dialog = DialogBuilder.create(getContext());
        cancel.setOnClickListener(v -> dialog.dismiss());
        submit.setOnClickListener(v -> {
            String content = input.getText().toString().trim();
            if (content.length() == 0 || content.codePointCount(0, content.length()) > 500) {
                Toast.makeText(getContext(),
                        getString(R.string.skillhub_error_comment_length), Toast.LENGTH_SHORT).show();
                return;
            }
            submit.setEnabled(false);
            cancel.setEnabled(false);
            input.setEnabled(false);
            submit.setText(getString(R.string.skillhub_submitting));
            new Thread(() -> {
                try {
                    if (parent == null) {
                        sessionClient.postComment(
                                value.getSlug(), namespaceHandle(value), content);
                    } else {
                        sessionClient.postCommentReply(
                                value.getSlug(), parent.getId(), namespaceHandle(value), content);
                    }
                    main.post(() -> {
                        dialog.dismiss();
                        activeTab = TAB_COMMENTS;
                        Toast.makeText(getContext(),
                                getString(R.string.skillhub_comment_submitted),
                                Toast.LENGTH_LONG).show();
                        load();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        submit.setEnabled(true);
                        cancel.setEnabled(true);
                        input.setEnabled(true);
                        submit.setText(getString(R.string.skillhub_submit_comment));
                        Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                    });
                }
            }, "skillhub-comment-submit").start();
        });
        DialogBuilder.showInset(dialog, panel);
    }

    private void addComment(LinearLayout section, SkillHubModels.Comment comment, int depth) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(card, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        String header = (comment.getAuthor().length() == 0
                ? getString(R.string.skillhub_user) : comment.getAuthor())
                + " · " + formatDate(comment.getCreatedAt());
        card.addView(LineTheme.textMedium(getContext(), header,
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY));
        TextView content = LineTheme.text(getContext(), comment.getContent(), LineTheme.FONT_SM,
                depth == 0 ? LineTheme.TEXT_SECONDARY : LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
        card.addView(content, contentParams);

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        TextView like = commentAction(
                (comment.isLiked() ? getString(R.string.skillhub_unlike)
                        : getString(R.string.skillhub_like))
                        + (comment.getLikeCount() > 0 ? " " + comment.getLikeCount() : ""));
        like.setOnClickListener(v -> updateCommentLike(comment, !comment.isLiked(), like));
        actions.addView(like);
        TextView reply = commentAction(getString(R.string.skillhub_reply));
        reply.setOnClickListener(v -> checkSessionForComment(detail, comment));
        actions.addView(reply);
        if (comment.getReplyCount() > comment.getReplies().size()) {
            TextView allReplies = commentAction(
                getString(R.string.skillhub_all_replies, comment.getReplyCount()));
            allReplies.setOnClickListener(v -> loadCommentReplies(section, comment, allReplies));
            actions.addView(allReplies);
        }
        TextView delete = commentAction(getString(R.string.skillhub_delete));
        delete.setTextColor(LineTheme.DANGER);
        delete.setOnClickListener(v -> deleteComment(comment, delete));
        actions.addView(delete);
        LinearLayout.LayoutParams actionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
        card.addView(actions, actionParams);

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        params.leftMargin = LineTheme.dp(getContext(), depth * LineTheme.LG);
        section.addView(card, params);
        for (SkillHubModels.Comment child : comment.getReplies()) {
            addComment(section, child, depth + 1);
        }
    }

    private TextView commentAction(String label) {
        TextView action = LineTheme.textMedium(getContext(), label,
                LineTheme.FONT_XS, LineTheme.ACCENT);
        action.setClickable(true);
        action.setFocusable(true);
        LineTheme.padding(action, 0, LineTheme.XS, LineTheme.MD, LineTheme.XS);
        return action;
    }

    private void updateCommentLike(
            SkillHubModels.Comment comment, boolean liked, TextView action) {
        action.setEnabled(false);
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                if (!session.isAuthenticated()) {
                    main.post(() -> {
                        action.setEnabled(true);
                        listener.onLogin();
                    });
                    return;
                }
                sessionClient.setCommentLiked(
                        detail.getSlug(), comment.getId(), namespaceHandle(detail), liked);
                main.post(() -> {
                    activeTab = TAB_COMMENTS;
                    load();
                });
            } catch (Exception e) {
                main.post(() -> {
                    action.setEnabled(true);
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-comment-like").start();
    }

    private void deleteComment(SkillHubModels.Comment comment, TextView action) {
        action.setEnabled(false);
        new Thread(() -> {
            try {
                sessionClient.deleteComment(
                        detail.getSlug(), comment.getId(), namespaceHandle(detail));
                main.post(() -> {
                    activeTab = TAB_COMMENTS;
                    load();
                });
            } catch (Exception e) {
                main.post(() -> {
                    action.setEnabled(true);
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-comment-delete").start();
    }

    private void loadCommentReplies(
            LinearLayout section, SkillHubModels.Comment comment, TextView action) {
        action.setEnabled(false);
        new Thread(() -> {
            try {
                List<SkillHubModels.Comment> replies = client.commentReplies(
                        detail.getSlug(), comment.getId(), namespaceHandle(detail));
                main.post(() -> {
                    action.setVisibility(GONE);
                    for (SkillHubModels.Comment reply : replies) {
                        addComment(section, reply, 1);
                    }
                });
            } catch (Exception e) {
                main.post(() -> {
                    action.setEnabled(true);
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-comment-replies").start();
    }

    private void addVersions(SkillHubModels.Detail value) {
        LinearLayout section = section(getString(R.string.skillhub_version_history),
                IconButtonView.CLOCK_3);
        if (value.getVersions().isEmpty()) {
            addSectionContent(section, emptyText(getString(R.string.skillhub_no_version_history)));
        } else {
            for (SkillHubModels.Version version : value.getVersions()) {
                String text = "v" + version.getVersion() + " · " + formatDate(version.getCreatedAt());
                if (version.getSecurityStatusText().length() > 0) {
                    text += "\n" + version.getSecurityStatusText();
                }
                if (version.getChangelog().length() > 0) {
                    text += "\n" + version.getChangelog();
                }
                addSectionContent(section, LineTheme.text(getContext(), text,
                        LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL));
            }
        }
        addSection(section);
    }

    private void addEvaluation(SkillHubModels.Detail value) {
        LinearLayout section = section(getString(R.string.skillhub_evaluation_report),
                IconButtonView.FLASK_CONICAL);
        SkillHubModels.Evaluation evaluation = value.getEvaluation();
        if (evaluation == null || evaluation.getStatus().length() == 0) {
            addSectionContent(section, emptyText(getString(R.string.skillhub_no_evaluation)));
        } else {
            if (evaluation.getScore() > 0) {
                addSectionContent(section, LineTheme.textMedium(getContext(),
                        getString(R.string.skillhub_overall_score, evaluation.getScore()),
                        LineTheme.FONT_XL, LineTheme.ACCENT));
            }
            addSectionContent(section, LineTheme.text(getContext(), evaluation.getSummary(),
                    LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL));
            int visible = Math.min(5, evaluation.getHighlights().size());
            for (int i = 0; i < visible; i++) {
                addSectionContent(section, LineTheme.text(getContext(),
                        "• " + evaluation.getHighlights().get(i), LineTheme.FONT_SM,
                        LineTheme.TEXT_TERTIARY, Typeface.NORMAL));
            }
        }
        addSection(section);
    }

    private void addPreview(SkillHubModels.Detail value) {
        LinearLayout section = section(getString(R.string.skillhub_tab_preview), IconButtonView.PLAY);
        if (value.getTestCases().isEmpty()) {
            addSectionContent(section, emptyText(getString(R.string.skillhub_no_preview)));
        } else {
            for (SkillHubModels.TestCase testCase : value.getTestCases()) {
                TextView prompt = LineTheme.textMedium(getContext(),
                        testCase.getTitle() + "\n" + getString(R.string.skillhub_user_colon) + testCase.getPrompt(),
                        LineTheme.FONT_SM, LineTheme.TEXT);
                prompt.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 8));
                LineTheme.padding(prompt, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
                addSectionContent(section, prompt);

                MarkdownView answer = new MarkdownView(getContext());
                answer.setCodeWrapEnabled(true);
                answer.setMarkdown(testCase.getExpected());
                addSectionContent(section, answer);
            }
        }
        addSection(section);
    }

    private View metadataCard(String label, String value) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(VERTICAL);
        card.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(card, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        card.addView(LineTheme.text(getContext(), label, LineTheme.FONT_XS,
                LineTheme.TEXT_TERTIARY, Typeface.NORMAL));
        TextView content = LineTheme.textMedium(getContext(),
                value.length() == 0 ? getString(R.string.skillhub_dash) : value, LineTheme.FONT_SM, LineTheme.TEXT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), 3);
        card.addView(content, params);
        return card;
    }

    private TextView metadataRow(String label, String value) {
        TextView row = LineTheme.text(getContext(), label + "  ·  " + value,
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        row.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_LIGHT, 8));
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        return row;
    }

    private LinearLayout section(String title, int iconType) {
        LinearLayout section = new LinearLayout(getContext());
        section.setOrientation(VERTICAL);
        section.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER));
        LineTheme.padding(section, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(28, 17);
        icon.setClickable(false);
        icon.setFocusable(false);
        header.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 28), LineTheme.dp(getContext(), 28)));
        TextView titleView = LineTheme.textMedium(getContext(), title,
                LineTheme.FONT_MD, LineTheme.TEXT);
        LinearLayout.LayoutParams titleParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        titleParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(titleView, titleParams);
        section.addView(header);
        return section;
    }

    private void addSectionContent(LinearLayout section, View content) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        section.addView(content, params);
    }

    private void addSection(LinearLayout section) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        body.addView(section, params);
    }

    private void addInstallButton(SkillHubModels.Detail value) {
        TextView install = LineTheme.textMedium(getContext(),
                getString(R.string.skillhub_select_location_install),
                LineTheme.FONT_MD, LineTheme.TEXT_ON_COLOR);
        install.setGravity(Gravity.CENTER);
        install.setClickable(true);
        install.setFocusable(true);
        install.setContentDescription(getString(R.string.skillhub_install_skill, value.getName()));
        install.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT, 12));
        LineTheme.padding(install, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        install.setOnClickListener(v -> showInstallConfirm(value, install));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        body.addView(install, params);
    }

    private TextView tag(String value, int color, int background) {
        TextView tag = LineTheme.textMedium(getContext(), value, LineTheme.FONT_XS, color);
        tag.setSingleLine(true);
        tag.setBackground(LineTheme.rounded(getContext(), background, 8));
        LineTheme.padding(tag, LineTheme.SM, 3, LineTheme.SM, 3);
        return tag;
    }

    private void addBadge(LinearLayout badges, String value, int color) {
        TextView badge = tag(value, color, LineTheme.SURFACE_LIGHT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        params.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        badges.addView(badge, params);
    }

    private void loadStarred(SkillHubModels.Detail value) {
        starBusy = true;
        updateStarButton();
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                if (!session.isAuthenticated()) {
                    main.post(() -> {
                        starBusy = false;
                        starred = Boolean.FALSE;
                        updateStarButton();
                    });
                    return;
                }
                boolean current = sessionClient.starred(
                        value.getSlug(), namespaceHandle(value));
                main.post(() -> {
                    starBusy = false;
                    starred = current;
                    updateStarButton();
                });
            } catch (Exception e) {
                main.post(() -> {
                    starBusy = false;
                    updateStarButton();
                });
            }
        }, "skillhub-star-state").start();
    }

    private void toggleStar(SkillHubModels.Detail value) {
        if (starBusy) {
            return;
        }
        starBusy = true;
        updateStarButton();
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                if (!session.isAuthenticated()) {
                    main.post(() -> {
                        starBusy = false;
                        updateStarButton();
                        Toast.makeText(getContext(),
                                getString(R.string.skillhub_login_to_star),
                                Toast.LENGTH_SHORT).show();
                        listener.onLogin();
                    });
                    return;
                }
                String namespace = namespaceHandle(value);
                boolean current = starred != null
                        ? starred : sessionClient.starred(value.getSlug(), namespace);
                boolean next = !current;
                sessionClient.setStarred(value.getSlug(), namespace, next);
                main.post(() -> {
                    starred = next;
                    starBusy = false;
                    updateStarButton();
                    Toast.makeText(getContext(),
                            next ? getString(R.string.skillhub_star_success)
                                    : getString(R.string.skillhub_unstar_success),
                            Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                main.post(() -> {
                    starBusy = false;
                    updateStarButton();
                    Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                });
            }
        }, "skillhub-star").start();
    }

    private void updateStarButton() {
        if (!(starButton instanceof LinearLayout)) {
            return;
        }
        starButton.setEnabled(!starBusy);
        starButton.setAlpha(starBusy ? 0.55f : 1f);
        LinearLayout button = (LinearLayout) starButton;
        if (button.getChildCount() > 1 && button.getChildAt(1) instanceof TextView) {
            ((TextView) button.getChildAt(1)).setText(
                    Boolean.TRUE.equals(starred) ? getString(R.string.skillhub_unstar)
                            : getString(R.string.skillhub_star));
        }
        starButton.setContentDescription(
                Boolean.TRUE.equals(starred) ? getString(R.string.skillhub_unstar)
                        : getString(R.string.skillhub_star));
    }

    private void copyPrompt(SkillHubModels.Detail value) {
        ShareHelper.copy(getContext(), installPrompt(value));
        Toast.makeText(getContext(), getString(R.string.skillhub_prompt_copied), Toast.LENGTH_SHORT).show();
    }

    private void share(SkillHubModels.Detail value) {
        ShareHelper.shareText(getContext(), value.getName() + "\n" + value.getDescription()
                + "\nhttps://skillhub.cn/skills/" + value.getCanonicalName().replaceFirst("^@", ""));
    }

    private String installPrompt(SkillHubModels.Detail value) {
        return getString(R.string.skillhub_install_prompt,
                value.getCanonicalName(), value.getVersion());
    }

    private void openHttps(String rawUrl) {
        try {
            Uri uri = Uri.parse(rawUrl == null ? "" : rawUrl.trim());
            if (!"https".equalsIgnoreCase(uri.getScheme())) {
                Toast.makeText(getContext(), getString(R.string.skillhub_https_only),
                        Toast.LENGTH_SHORT).show();
                return;
            }
            getContext().startActivity(new Intent(Intent.ACTION_VIEW, uri));
        } catch (Exception e) {
            Toast.makeText(getContext(), getString(R.string.toast_open_link_failed,
                    rawUrl == null ? "" : rawUrl), Toast.LENGTH_SHORT).show();
        }
    }

    private void showInstallConfirm(SkillHubModels.Detail value, TextView installButton) {
        final String[] selectedLocation = {SkillRecord.LOCATION_APP};
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        LinearLayout heading = new LinearLayout(getContext());
        heading.setOrientation(HORIZONTAL);
        heading.setGravity(Gravity.CENTER_VERTICAL);
        IconButtonView packageIcon = new IconButtonView(getContext(), IconButtonView.PACKAGE);
        packageIcon.setIconColor(LineTheme.ACCENT);
        packageIcon.setIconSizeDp(42, 22);
        packageIcon.setClickable(false);
        packageIcon.setFocusable(false);
        packageIcon.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 10));
        heading.addView(packageIcon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 42), LineTheme.dp(getContext(), 42)));
        LinearLayout titleCopy = new LinearLayout(getContext());
        titleCopy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams titleCopyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        titleCopyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.MD);
        heading.addView(titleCopy, titleCopyParams);
        TextView title = LineTheme.textMedium(getContext(),
                getString(R.string.skillhub_install_skill, value.getName()),
                LineTheme.FONT_LG, LineTheme.TEXT);
        title.setMaxLines(2);
        titleCopy.addView(title);
        TextView subtitle = LineTheme.text(getContext(),
                getString(R.string.skillhub_install_scope_desc),
                LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(getContext(), 3);
        titleCopy.addView(subtitle, subtitleParams);
        panel.addView(heading);

        LinearLayout facts = new LinearLayout(getContext());
        facts.setOrientation(HORIZONTAL);
        facts.addView(tag("SkillHub", LineTheme.TEXT_SECONDARY, LineTheme.SURFACE_LIGHT));
        addBadge(facts, "v" + value.getVersion(), LineTheme.TEXT_SECONDARY);
        addBadge(facts, getString(R.string.skillhub_file_count, value.getFiles().size()),
                LineTheme.TEXT_SECONDARY);
        LinearLayout.LayoutParams factsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        factsParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(facts, factsParams);

        TextView locationLabel = LineTheme.textMedium(getContext(),
                getString(R.string.skillhub_install_location),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        LinearLayout.LayoutParams locationLabelParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        locationLabelParams.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        panel.addView(locationLabel, locationLabelParams);

        LinearLayout appOption = installLocationOption(
                IconButtonView.SMARTPHONE, getString(R.string.skillhub_location_app_title),
                getString(R.string.skillhub_location_app_desc), true);
        LinearLayout projectOption = installLocationOption(
                IconButtonView.FOLDER, getString(R.string.skillhub_location_project_title),
                getString(R.string.skillhub_location_project_desc), false);
        LinearLayout.LayoutParams optionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        optionParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        panel.addView(appOption, optionParams);
        LinearLayout.LayoutParams projectParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        projectParams.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        panel.addView(projectOption, projectParams);
        appOption.setOnClickListener(v -> {
            selectedLocation[0] = SkillRecord.LOCATION_APP;
            styleInstallLocation(appOption, true);
            styleInstallLocation(projectOption, false);
        });
        projectOption.setOnClickListener(v -> {
            selectedLocation[0] = SkillRecord.LOCATION_PROJECT;
            styleInstallLocation(appOption, false);
            styleInstallLocation(projectOption, true);
        });

        if (value.hasScripts() || value.requiresApiKey()) {
            String warning = value.hasScripts()
                    ? getString(R.string.skillhub_contains_scripts_confirm)
                    : getString(R.string.skillhub_may_require_api_key_confirm);
            if (value.hasScripts() && value.requiresApiKey()) {
                warning += "\n" + getString(R.string.skillhub_may_also_require_api_key);
            }
            LinearLayout warningCard = new LinearLayout(getContext());
            warningCard.setOrientation(HORIZONTAL);
            warningCard.setGravity(Gravity.TOP);
            warningCard.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 9));
            LineTheme.padding(warningCard, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
            IconButtonView warningIcon = new IconButtonView(getContext(), IconButtonView.CIRCLE_ALERT);
            warningIcon.setIconColor(LineTheme.WARNING);
            warningIcon.setIconSizeDp(24, 16);
            warningIcon.setClickable(false);
            warningIcon.setFocusable(false);
            warningCard.addView(warningIcon, new LinearLayout.LayoutParams(
                    LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));
            TextView warningText = LineTheme.text(getContext(), warning, LineTheme.FONT_XS,
                    LineTheme.WARNING, Typeface.NORMAL);
            LinearLayout.LayoutParams warningTextParams = new LinearLayout.LayoutParams(
                    0, LayoutParams.WRAP_CONTENT, 1f);
            warningTextParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
            warningCard.addView(warningText, warningTextParams);
            LinearLayout.LayoutParams warningParams = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            warningParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
            panel.addView(warningCard, warningParams);
        }

        LinearLayout actions = new LinearLayout(getContext());
        actions.setOrientation(HORIZONTAL);
        TextView cancel = dialogButton(getString(R.string.skillhub_cancel), false);
        TextView install = dialogButton(getString(R.string.common_install), true);
        actions.addView(cancel, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        installParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actions.addView(install, installParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        panel.addView(actions, actionsParams);

        Dialog dialog = DialogBuilder.create(getContext());
        dialog.setCanceledOnTouchOutside(true);
        cancel.setOnClickListener(v -> dialog.dismiss());
        install.setOnClickListener(v -> {
            String location = selectedLocation[0];
            install.setEnabled(false);
            cancel.setEnabled(false);
            appOption.setEnabled(false);
            projectOption.setEnabled(false);
            installButton.setEnabled(false);
            install.setText(getString(R.string.skillhub_installing));
            installButton.setText(getString(R.string.skillhub_installing));
            new Thread(() -> {
                try {
                    listener.onInstall(location, value.getSlug(), value.getVersion());
                    main.post(() -> {
                        dialog.dismiss();
                        installButton.setText(getString(R.string.skillhub_installed));
                        Toast.makeText(getContext(),
                                getString(R.string.skillhub_install_success),
                                Toast.LENGTH_LONG).show();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        install.setEnabled(true);
                        cancel.setEnabled(true);
                        appOption.setEnabled(true);
                        projectOption.setEnabled(true);
                        installButton.setEnabled(true);
                        install.setText(getString(R.string.common_install));
                        installButton.setText(getString(R.string.skillhub_select_location_install));
                        Toast.makeText(getContext(), safeMessage(e),
                                Toast.LENGTH_LONG).show();
                    });
                }
            }, "skillhub-install").start();
        });
        DialogBuilder.showInset(dialog, panel);
    }

    private LinearLayout installLocationOption(
            int iconType, String title, String description, boolean selected) {
        LinearLayout option = new LinearLayout(getContext());
        option.setOrientation(HORIZONTAL);
        option.setGravity(Gravity.CENTER_VERTICAL);
        option.setClickable(true);
        option.setFocusable(true);
        option.setContentDescription(title);
        LineTheme.padding(option, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(34, 19);
        icon.setClickable(false);
        icon.setFocusable(false);
        option.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 34), LineTheme.dp(getContext(), 34)));
        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        option.addView(copy, copyParams);
        copy.addView(LineTheme.textMedium(getContext(), title, LineTheme.FONT_SM, LineTheme.TEXT));
        TextView detail = LineTheme.text(getContext(), description, LineTheme.FONT_XS,
                LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = LineTheme.dp(getContext(), 2);
        copy.addView(detail, detailParams);
        TextView indicator = LineTheme.textMedium(getContext(), selected ? "✓" : "",
                LineTheme.FONT_LG, LineTheme.ACCENT);
        indicator.setTag("selection-indicator");
        indicator.setGravity(Gravity.CENTER);
        option.addView(indicator, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 30), LineTheme.dp(getContext(), 30)));
        styleInstallLocation(option, selected);
        return option;
    }

    private void styleInstallLocation(LinearLayout option, boolean selected) {
        option.setBackground(LineTheme.roundedStroke(
                getContext(), selected ? LineTheme.ACCENT_MUTED : LineTheme.SURFACE_LIGHT,
                10, selected ? LineTheme.ACCENT : LineTheme.BORDER_LIGHT));
        View indicator = option.findViewWithTag("selection-indicator");
        if (indicator instanceof TextView) {
            ((TextView) indicator).setText(selected ? "✓" : "");
        }
        option.setSelected(selected);
    }

    private TextView dialogButton(String value, boolean primary) {
        TextView button = LineTheme.textMedium(getContext(), value, LineTheme.FONT_SM,
                primary ? LineTheme.TEXT_ON_COLOR : LineTheme.TEXT);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(primary
                ? LineTheme.rounded(getContext(), LineTheme.ACCENT, 10)
                : LineTheme.roundedStroke(getContext(), LineTheme.SURFACE_LIGHT, 10, LineTheme.BORDER_LIGHT));
        LineTheme.padding(button, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        return button;
    }

    private void renderError(Exception error) {
        body.removeAllViews();
        LinearLayout errorCard = section(getString(R.string.skillhub_detail_load_failed),
                IconButtonView.CIRCLE_ALERT);
        TextView message = LineTheme.text(getContext(),
                safeMessage(error) + "\n" + getString(R.string.skillhub_retry_here),
                LineTheme.FONT_SM, LineTheme.DANGER, Typeface.NORMAL);
        message.setGravity(Gravity.CENTER);
        errorCard.setClickable(true);
        errorCard.setFocusable(true);
        errorCard.setOnClickListener(v -> {
            body.removeAllViews();
            body.addView(progress);
            load();
        });
        addSectionContent(errorCard, message);
        body.addView(errorCard, new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void configureReadingScale(MarkdownView markdown) {
        SharedPreferences preferences = getContext().getSharedPreferences(
                READING_PREFERENCES, Context.MODE_PRIVATE);
        markdown.setTextScale(preferences.getFloat(MARKDOWN_TEXT_SCALE, 1f));
        markdown.setTextScaleListener(scale -> preferences.edit()
                .putFloat(MARKDOWN_TEXT_SCALE, scale)
                .apply());
        markdown.setPinchZoomEnabled(true);
    }

    private TextView emptyText(String value) {
        return LineTheme.text(getContext(), value, LineTheme.FONT_SM,
                LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
    }

    private String namespaceHandle(SkillHubModels.Detail value) {
        String canonical = value.getCanonicalName();
        if (!canonical.startsWith("@")) {
            return "";
        }
        int slash = canonical.indexOf('/');
        return slash > 1 ? canonical.substring(1, slash) : "";
    }

    private String sourceName(String source) {
        return "community".equalsIgnoreCase(source)
                ? getString(R.string.skillhub_source_community) : source;
    }

    private String join(List<String> values) {
        StringBuilder result = new StringBuilder();
        for (String value : values) {
            if (result.length() > 0) {
                result.append(" · ");
            }
            result.append(value);
        }
        return result.toString();
    }

    private String formatCount(long value) {
        if (value >= 10000) {
            return getString(R.string.skillhub_count_wan, value / 10000d);
        }
        return String.valueOf(value);
    }

    private String formatBytes(long value) {
        if (value >= 1024 * 1024) {
            return String.format(Locale.getDefault(), "%.1f MB", value / (1024d * 1024d));
        }
        if (value >= 1024) {
            return String.format(Locale.getDefault(), "%.1f KB", value / 1024d);
        }
        return value + " B";
    }

    private String formatDate(long value) {
        if (value <= 0) {
            return getString(R.string.skillhub_dash);
        }
        return DateFormat.getDateInstance(DateFormat.MEDIUM, Locale.getDefault())
                .format(new Date(value));
    }

    private String safeMessage(Exception error) {
        return error.getMessage() == null
                ? getString(R.string.skillhub_unknown_error) : error.getMessage();
    }
}
