package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.data.service.SkillHubClient;
import cn.lineai.data.service.SkillHubSessionClient;
import cn.lineai.model.SkillHubModels;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.ArrayList;
import java.util.List;

public final class SkillStoreScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onOpen(String slug);
        void onLogin();
        void onCenter();
        void onPublish();
    }

    private final Listener listener;
    private final SkillHubClient client;
    private final SkillHubSessionClient sessionClient;
    private final SkillIconLoader iconLoader;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final LinearLayout results;
    private final ProgressBar progress;
    private final TextView status;
    private final EditText search;
    private LinearLayout accountRow;
    private IconButtonView accountIcon;
    private TextView accountTitle;
    private TextView accountSubtitle;
    private IconButtonView accountAction;
    private TextView publishButton;
    private SkillHubSessionClient.Session accountSession;
    private final List<FilterChip> filterChips = new ArrayList<>();
    private int requestGeneration;
    private int page = 1;
    private String sortBy = "downloads";

    public SkillStoreScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.skillhub_title_store), listener::onBack, null, true);
        this.listener = listener;
        this.client = new SkillHubClient(new ContextResourceProvider(context));
        this.sessionClient = new SkillHubSessionClient(new ContextResourceProvider(context));
        this.iconLoader = new SkillIconLoader(context);
        LinearLayout content = getContent();
        LineTheme.padding(content, 16, 16, 16, 32);

        addIntro(content);
        search = addSearch(content);
        addFilters(content);
        addLogin(content);
        addPublish(content);
        loadAccount();

        progress = new ProgressBar(context);
        LinearLayout.LayoutParams progressParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        progressParams.gravity = Gravity.CENTER_HORIZONTAL;
        progressParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        content.addView(progress, progressParams);

        status = LineTheme.text(context, "", LineTheme.FONT_SM,
                LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(status, statusParams);

        results = new LinearLayout(context);
        results.setOrientation(VERTICAL);
        LinearLayout.LayoutParams resultsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        resultsParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(results, resultsParams);

        addPager(content);
        updateFilterStyles();
        load();
    }

    private void addIntro(LinearLayout content) {
        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        header.addView(copy, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        copy.addView(LineTheme.textMedium(getContext(),
                getContext().getString(R.string.skillhub_discover_community_skills),
                LineTheme.FONT_XL, LineTheme.TEXT));
        TextView description = LineTheme.text(getContext(),
                getContext().getString(R.string.skillhub_browse_install_desc),
                LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
        copy.addView(description, descriptionParams);

        IconButtonView storeIcon = new IconButtonView(getContext(), IconButtonView.SPARKLES);
        storeIcon.setIconColor(LineTheme.ACCENT);
        storeIcon.setIconSizeDp(44, 24);
        storeIcon.setClickable(false);
        storeIcon.setFocusable(false);
        storeIcon.setBackground(null);
        header.addView(storeIcon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 44), LineTheme.dp(getContext(), 44)));
        content.addView(header);
    }

    private EditText addSearch(LinearLayout content) {
        LinearLayout searchBox = new LinearLayout(getContext());
        searchBox.setOrientation(HORIZONTAL);
        searchBox.setGravity(Gravity.CENTER_VERTICAL);
        searchBox.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.INPUT_BG, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(searchBox, LineTheme.SM, 0, LineTheme.MD, 0);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.SEARCH);
        icon.setIconColor(LineTheme.TEXT_TERTIARY);
        icon.setIconSizeDp(36, 18);
        icon.setClickable(false);
        icon.setFocusable(false);
        searchBox.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 36), LineTheme.dp(getContext(), 44)));

        EditText field = new EditText(getContext());
        field.setSingleLine(true);
        field.setHint(getContext().getString(R.string.skillhub_search_hint));
        field.setHintTextColor(LineTheme.TEXT_TERTIARY);
        field.setTextColor(LineTheme.TEXT);
        field.setTextSize(LineTheme.FONT_MD);
        field.setInputType(InputType.TYPE_CLASS_TEXT);
        field.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        field.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        field.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_SEARCH) {
                page = 1;
                load();
                return true;
            }
            return false;
        });
        searchBox.addView(field, new LinearLayout.LayoutParams(
                0, LineTheme.dp(getContext(), 48), 1f));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        content.addView(searchBox, params);
        return field;
    }

    private void addFilters(LinearLayout content) {
        LinearLayout filters = new LinearLayout(getContext());
        filters.setOrientation(HORIZONTAL);
        filters.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(filters, params);
        addFilter(filters, getContext().getString(R.string.skillhub_filter_hot_downloads), "downloads");
        addFilter(filters, getContext().getString(R.string.skillhub_filter_most_stars), "stars");
        addFilter(filters, getContext().getString(R.string.skillhub_filter_recent_update), "updated_at");
    }

    private void addFilter(LinearLayout host, String label, String value) {
        TextView chip = LineTheme.textMedium(getContext(), label,
                LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY);
        chip.setGravity(Gravity.CENTER);
        chip.setClickable(true);
        chip.setFocusable(true);
        chip.setContentDescription(getContext().getString(R.string.skillhub_sort_by, label));
        LineTheme.padding(chip, LineTheme.SM, LineTheme.SM, LineTheme.SM, LineTheme.SM);
        chip.setOnClickListener(v -> {
            if (!value.equals(sortBy)) {
                sortBy = value;
                page = 1;
                updateFilterStyles();
                load();
            }
        });
        FilterChip filter = new FilterChip(value, chip);
        filterChips.add(filter);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        if (!filterChips.isEmpty()) {
            params.rightMargin = LineTheme.dp(getContext(), LineTheme.XS);
        }
        host.addView(chip, params);
    }

    private void updateFilterStyles() {
        for (FilterChip filter : filterChips) {
            boolean selected = filter.value.equals(sortBy);
            filter.view.setTextColor(selected ? LineTheme.ACCENT : LineTheme.TEXT_SECONDARY);
            filter.view.setBackground(LineTheme.roundedStroke(
                    getContext(),
                    selected ? LineTheme.ACCENT_MUTED : LineTheme.SURFACE_ELEVATED,
                    10,
                    selected ? LineTheme.ACCENT : LineTheme.BORDER));
            filter.view.setSelected(selected);
        }
    }

    private void addLogin(LinearLayout content) {
        accountRow = new LinearLayout(getContext());
        accountRow.setOrientation(HORIZONTAL);
        accountRow.setGravity(Gravity.CENTER_VERTICAL);
        accountRow.setClickable(true);
        accountRow.setFocusable(true);
        accountRow.setContentDescription(getContext().getString(R.string.skillhub_account_label));
        accountRow.setOnClickListener(v -> {
            if (accountSession != null && accountSession.isAuthenticated()) {
                showAccountDialog(accountSession.getAccount());
            } else {
                listener.onLogin();
            }
        });
        accountRow.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(accountRow, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);

        accountIcon = new IconButtonView(getContext(), IconButtonView.USER);
        accountIcon.setIconColor(LineTheme.ACCENT);
        accountIcon.setIconSizeDp(36, 19);
        accountIcon.setClickable(false);
        accountIcon.setFocusable(false);
        accountIcon.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 9));
        accountRow.addView(accountIcon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 36), LineTheme.dp(getContext(), 36)));

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        accountRow.addView(copy, copyParams);
        accountTitle = LineTheme.textMedium(getContext(),
                getContext().getString(R.string.skillhub_checking_account),
                LineTheme.FONT_SM, LineTheme.TEXT);
        copy.addView(accountTitle);
        accountSubtitle = LineTheme.text(getContext(),
                getContext().getString(R.string.skillhub_login_via_official),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(getContext(), 2);
        copy.addView(accountSubtitle, subtitleParams);

        accountAction = new IconButtonView(getContext(), IconButtonView.EXTERNAL_LINK);
        accountAction.setIconColor(LineTheme.TEXT_TERTIARY);
        accountAction.setIconSizeDp(28, 16);
        accountAction.setClickable(false);
        accountAction.setFocusable(false);
        accountRow.addView(accountAction, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 28), LineTheme.dp(getContext(), 28)));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(accountRow, params);
    }

    private void addPublish(LinearLayout content) {
        publishButton = LineTheme.textMedium(getContext(),
                getContext().getString(R.string.skillhub_feature_center),
                LineTheme.FONT_SM, LineTheme.ACCENT);
        publishButton.setGravity(Gravity.CENTER);
        publishButton.setClickable(true);
        publishButton.setFocusable(true);
        publishButton.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 12, LineTheme.ACCENT));
        LineTheme.padding(publishButton, LineTheme.MD, LineTheme.SM,
                LineTheme.MD, LineTheme.SM);
        publishButton.setOnClickListener(v -> listener.onCenter());
        publishButton.setVisibility(GONE);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(publishButton, params);
    }

    private void loadAccount() {
        accountRow.setEnabled(false);
        accountTitle.setText(getContext().getString(R.string.skillhub_checking_account));
        accountSubtitle.setText(getContext().getString(R.string.skillhub_login_via_official));
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                main.post(() -> renderAccount(session));
            } catch (Exception e) {
                main.post(() -> {
                    accountSession = null;
                    publishButton.setVisibility(GONE);
                    accountRow.setEnabled(true);
                    accountTitle.setText(getContext().getString(R.string.skillhub_account_check_failed));
                    accountSubtitle.setText(getContext().getString(R.string.skillhub_retry_here)
                            + " · " + safeMessage(e));
                    accountAction.setIconType(IconButtonView.REFRESH_CW);
                    accountRow.setOnClickListener(v -> loadAccount());
                });
            }
        }, "skillhub-session").start();
    }

    private void renderAccount(SkillHubSessionClient.Session session) {
        accountSession = session;
        accountRow.setEnabled(true);
        accountRow.setOnClickListener(v -> {
            if (accountSession != null && accountSession.isAuthenticated()) {
                showAccountDialog(accountSession.getAccount());
            } else {
                listener.onLogin();
            }
        });
        if (!session.isAuthenticated()) {
            publishButton.setVisibility(GONE);
            accountTitle.setText(getContext().getString(R.string.skillhub_login_account));
            accountSubtitle.setText(getContext().getString(R.string.skillhub_login_desc));
            accountAction.setIconType(IconButtonView.EXTERNAL_LINK);
            return;
        }
        publishButton.setVisibility(VISIBLE);
        SkillHubSessionClient.Account account = session.getAccount();
        accountTitle.setText(account.getDisplayName());
        accountSubtitle.setText(account.getHandle().length() == 0
                ? getContext().getString(R.string.skillhub_logged_in)
                : "@" + account.getHandle() + " · "
                        + getContext().getString(R.string.skillhub_logged_in_suffix));
        accountAction.setIconType(IconButtonView.CHEVRON_RIGHT);
    }

    private void showAccountDialog(SkillHubSessionClient.Account account) {
        LinearLayout panel = new LinearLayout(getContext());
        panel.setOrientation(VERTICAL);
        panel.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        LineTheme.padding(panel, LineTheme.LG, LineTheme.LG, LineTheme.LG, LineTheme.LG);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.USER);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(54, 27);
        icon.setClickable(false);
        icon.setFocusable(false);
        icon.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 14));
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 54), LineTheme.dp(getContext(), 54));
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        panel.addView(icon, iconParams);

        TextView name = LineTheme.textMedium(getContext(), account.getDisplayName(),
                LineTheme.FONT_LG, LineTheme.TEXT);
        name.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(name, nameParams);
        if (account.getHandle().length() > 0) {
            TextView handle = LineTheme.text(getContext(), "@" + account.getHandle(),
                    LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            handle.setGravity(Gravity.CENTER);
            panel.addView(handle);
        }

        TextView status = LineTheme.textMedium(getContext(),
                getContext().getString(R.string.skillhub_account_connected),
                LineTheme.FONT_SM, LineTheme.ACCENT);
        status.setGravity(Gravity.CENTER);
        status.setBackground(LineTheme.rounded(getContext(), LineTheme.ACCENT_MUTED, 9));
        LineTheme.padding(status, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        panel.addView(status, statusParams);

        LinearLayout actions = new AdaptiveActionsView(getContext());
        actions.setOrientation(HORIZONTAL);
        TextView close = accountDialogButton(
                getContext().getString(R.string.skillhub_continue), false);
        TextView logout = accountDialogButton(
                getContext().getString(R.string.skillhub_logout), true);
        actions.addView(close, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        LinearLayout.LayoutParams logoutParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        logoutParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        actions.addView(logout, logoutParams);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        actionsParams.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        panel.addView(actions, actionsParams);

        Dialog dialog = DialogBuilder.create(getContext());
        close.setOnClickListener(v -> dialog.dismiss());
        logout.setOnClickListener(v -> {
            close.setEnabled(false);
            logout.setEnabled(false);
            logout.setText(getContext().getString(R.string.skillhub_logging_out));
            new Thread(() -> {
                try {
                    sessionClient.logout();
                    main.post(() -> {
                        dialog.dismiss();
                        renderAccount(SkillHubSessionClient.Session.signedOut());
                        Toast.makeText(getContext(), getContext().getString(R.string.skillhub_logged_out_success),
                                Toast.LENGTH_SHORT).show();
                    });
                } catch (Exception e) {
                    main.post(() -> {
                        close.setEnabled(true);
                        logout.setEnabled(true);
                        logout.setText(getContext().getString(R.string.skillhub_logout));
                        Toast.makeText(getContext(), safeMessage(e), Toast.LENGTH_LONG).show();
                    });
                }
            }, "skillhub-logout").start();
        });
        DialogBuilder.showInset(dialog, panel);
    }

    private TextView accountDialogButton(String value, boolean danger) {
        TextView button = LineTheme.textMedium(getContext(), value, LineTheme.FONT_SM,
                danger ? LineTheme.DANGER : LineTheme.TEXT);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(LineTheme.roundedStroke(
                getContext(), danger ? LineTheme.DANGER_MUTED : LineTheme.SURFACE_LIGHT,
                10, danger ? LineTheme.DANGER : LineTheme.BORDER_LIGHT));
        LineTheme.padding(button, LineTheme.SM, LineTheme.MD, LineTheme.SM, LineTheme.MD);
        return button;
    }

    private void addPager(LinearLayout content) {
        LinearLayout pager = new LinearLayout(getContext());
        pager.setGravity(Gravity.CENTER);
        TextView previous = pagerButton(getContext().getString(R.string.skillhub_previous_page));
        previous.setOnClickListener(v -> {
            if (page > 1) {
                page--;
                load();
            }
        });
        pager.addView(previous);
        TextView next = pagerButton(getContext().getString(R.string.skillhub_next_page));
        next.setOnClickListener(v -> {
            page++;
            load();
        });
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nextParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        pager.addView(next, nextParams);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.MD);
        content.addView(pager, params);
    }

    private TextView pagerButton(String label) {
        TextView button = LineTheme.textMedium(getContext(), label,
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY);
        button.setGravity(Gravity.CENTER);
        button.setClickable(true);
        button.setFocusable(true);
        button.setBackground(LineTheme.roundedStroke(
                getContext(), LineTheme.SURFACE_ELEVATED, 10, LineTheme.BORDER_LIGHT));
        LineTheme.padding(button, LineTheme.LG, LineTheme.SM, LineTheme.LG, LineTheme.SM);
        return button;
    }

    private void load() {
        final int generation = ++requestGeneration;
        final int requestedPage = page;
        final String keyword = search.getText().toString();
        final String requestedSort = sortBy;
        progress.setVisibility(VISIBLE);
        status.setText(getContext().getString(R.string.skillhub_loading_page, requestedPage));
        results.removeAllViews();
        new Thread(() -> {
            try {
                SkillHubModels.Page value = client.list(
                        requestedPage, 20, keyword, "", "all", requestedSort, "desc");
                main.post(() -> {
                    if (generation != requestGeneration) return;
                    progress.setVisibility(GONE);
                    render(value, requestedPage);
                });
            } catch (Exception e) {
                main.post(() -> {
                    if (generation != requestGeneration) return;
                    progress.setVisibility(GONE);
                    status.setText(getContext().getString(R.string.skillhub_load_failed_retry) + "\n" + safeMessage(e));
                    status.setOnClickListener(v -> load());
                });
            }
        }, "skillhub-list").start();
    }

    private void render(SkillHubModels.Page value, int requestedPage) {
        status.setOnClickListener(null);
        status.setText(value.getSkills().isEmpty()
                ? getContext().getString(R.string.skillhub_no_matching_skills)
                : getContext().getString(R.string.skillhub_page_count,
                        requestedPage, formatCount(value.getTotal())));
        for (SkillHubModels.Summary skill : value.getSkills()) {
            results.addView(card(skill));
        }
    }

    private View card(SkillHubModels.Summary skill) {
        LinearLayout card = new LinearLayout(getContext());
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setFocusable(true);
        card.setContentDescription(skill.getName() + getContext().getString(R.string.skillhub_view_details));
        card.setOnClickListener(v -> listener.onOpen(skill.getSlug()));
        card.setBackground(LineTheme.pressable(getContext()));
        LineTheme.padding(card, 0, 20, 0, 20);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = LineTheme.dp(getContext(), LineTheme.SM);
        card.setLayoutParams(cardParams);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.PACKAGE);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(48, 25);
        icon.setClickable(false);
        icon.setFocusable(false);

        card.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 48), LineTheme.dp(getContext(), 48)));
        iconLoader.load(skill.getIconUrl(), icon);

        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(VERTICAL);
        LinearLayout.LayoutParams copyParams = new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f);
        copyParams.leftMargin = LineTheme.dp(getContext(), LineTheme.MD);
        copyParams.rightMargin = LineTheme.dp(getContext(), LineTheme.XS);
        card.addView(copy, copyParams);

        LinearLayout titleRow = new LinearLayout(getContext());
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = LineTheme.textMedium(getContext(), skill.getName(),
                LineTheme.FONT_MD, LineTheme.TEXT);
        title.setMaxLines(2);
        titleRow.addView(title, new LinearLayout.LayoutParams(
                0, LayoutParams.WRAP_CONTENT, 1f));
        if (skill.isVerified()) {
            titleRow.addView(tag(getContext().getString(R.string.skillhub_verified), LineTheme.ACCENT, LineTheme.ACCENT_MUTED));
        }
        copy.addView(titleRow);

        String owner = skill.getOwner().length() == 0 ? "SkillHub" : skill.getOwner();
        TextView ownerView = LineTheme.text(getContext(), owner,
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams ownerParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        ownerParams.topMargin = LineTheme.dp(getContext(), 2);
        copy.addView(ownerView, ownerParams);

        if (skill.getCategory().length() > 0 || skill.requiresApiKey()) {
            LinearLayout tags = new LinearLayout(getContext());
            tags.setOrientation(HORIZONTAL);
            LinearLayout.LayoutParams tagsParams = new LinearLayout.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            tagsParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
            if (skill.getCategory().length() > 0) {
                tags.addView(tag(skill.getCategory(), LineTheme.TEXT_SECONDARY, LineTheme.SURFACE_LIGHT));
            }
            if (skill.requiresApiKey()) {
                TextView apiKey = tag(getContext().getString(R.string.skillhub_requires_api_key), LineTheme.WARNING, LineTheme.SURFACE_LIGHT);
                LinearLayout.LayoutParams apiParams = new LinearLayout.LayoutParams(
                        LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
                apiParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
                tags.addView(apiKey, apiParams);
            }

        }

        TextView description = LineTheme.text(getContext(), skill.getDescription(),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        description.setMaxLines(2);
        LinearLayout.LayoutParams descriptionParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descriptionParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);
        copy.addView(description, descriptionParams);

        String version = skill.getVersion().length() == 0 ? "" : "  ·  v" + skill.getVersion();
        TextView stats = LineTheme.text(getContext(),
                "↓ " + formatCount(skill.getDownloads()) + "   ☆ " + formatCount(skill.getStars()) + version,
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams statsParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statsParams.topMargin = LineTheme.dp(getContext(), LineTheme.XS);


        IconButtonView chevron = new IconButtonView(getContext(), IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(24, 16);
        chevron.setClickable(false);
        chevron.setFocusable(false);
        card.addView(chevron, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 24), LineTheme.dp(getContext(), 24)));
        return card;
    }

    private TextView tag(String value, int color, int background) {
        TextView tag = LineTheme.textMedium(getContext(), value, LineTheme.FONT_XS, color);
        tag.setSingleLine(true);
        tag.setBackground(LineTheme.rounded(getContext(), background, 8));
        LineTheme.padding(tag, LineTheme.SM, 3, LineTheme.SM, 3);
        return tag;
    }

    private String formatCount(long value) {
        if (value >= 10000) {
            return getContext().getString(R.string.skillhub_count_wan, value / 10000d);
        }
        return String.valueOf(value);
    }

    private String safeMessage(Exception e) {
        return e.getMessage() == null ? getContext().getString(R.string.skillhub_unknown_error) : e.getMessage();
    }

    private static final class FilterChip {
        private final String value;
        private final TextView view;

        private FilterChip(String value, TextView view) {
            this.value = value;
            this.view = view;
        }
    }
}
