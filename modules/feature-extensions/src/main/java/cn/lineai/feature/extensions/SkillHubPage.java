package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Consumer;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SkillHubEntry;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.skillhub.SkillHubApi;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** SkillHub store: search, account state, paged result cards and install entry points. */
public final class SkillHubPage extends SettingsPage {

    private final Scheduler scheduler;
    private final SkillStore store;
    private final SkillHubApi api = new SkillHubApi();
    private final Consumer<String> openDetail;
    private final Runnable openLogin;
    private final Runnable openPublish;
    private final LinearLayout filtersHost;
    private final LinearLayout featureHost;
    private String sort = "downloads";
    private final EditText search;
    private final TextView accountTitle;
    private final TextView accountDescription;
    private final IconButton accountIcon;
    private final LinearLayout listHost;
    private int page = 1;
    private int accountState;
    private boolean loading;

    public SkillHubPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                        SkillStore store, Consumer<String> openDetail) {
        this(context, tokens, back, scheduler, store, openDetail, null, null);
    }

    public SkillHubPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                        SkillStore store, Consumer<String> openDetail, Runnable openLogin) {
        this(context, tokens, back, scheduler, store, openDetail, openLogin, null);
    }

    public SkillHubPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                        SkillStore store, Consumer<String> openDetail, Runnable openLogin,
                        Runnable openPublish) {
        super(context, R.string.skillhub_title_store, tokens, back);
        this.scheduler = scheduler;
        this.store = store;
        this.openDetail = openDetail;
        this.openLogin = openLogin;
        this.openPublish = openPublish;
        Ui.padding(content, 16, 16, 16, 100);
        accountTitle = accountTitleView(context);
        accountDescription = accountDescriptionView(context);
        accountIcon = accountIconView(context);
        addRow(intro(), 0);
        search = searchField(context);
        addRow(searchBox(context, search), 16);
        filtersHost = new LinearLayout(context);
        filtersHost.setOrientation(LinearLayout.HORIZONTAL);
        addRow(filtersHost, 8);
        renderFilters();
        addRow(accountCard(context), 8);
        featureHost = new LinearLayout(context);
        featureHost.setOrientation(LinearLayout.VERTICAL);
        featureHost.setVisibility(View.GONE);
        addRow(featureHost, 8);
        listHost = new LinearLayout(context);
        listHost.setOrientation(LinearLayout.VERTICAL);
        addRow(listHost, 0);
        reload();
        loadAccount();
    }

    private View intro() {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout copy = new LinearLayout(getContext());
        copy.setOrientation(LinearLayout.VERTICAL);
        row.addView(copy, new LinearLayout.LayoutParams(0, -2, 1f));
        TextView title = Ui.medium(getContext(),
                getContext().getString(R.string.skillhub_discover_community_skills),
                Tokens.FONT_XL, theme.current().text);
        copy.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(getContext(),
                getContext().getString(R.string.skillhub_browse_install_desc),
                Tokens.FONT_SM, theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(getContext(), 4);
        copy.addView(desc, descParams);
        FrameLayout artwork = new FrameLayout(getContext());
        artwork.addView(iconView(Icons.SPARKLES, 24), new FrameLayout.LayoutParams(
                Ui.dp(getContext(), 44), Ui.dp(getContext(), 44), Gravity.CENTER));
        row.addView(artwork, new LinearLayout.LayoutParams(Ui.dp(getContext(), 44), Ui.dp(getContext(), 44)));
        theme.bind(t -> {
            title.setTextColor(t.text);
            desc.setTextColor(t.textTertiary);
            artwork.setBackground(Ui.rounded(getContext(), t.accentMuted, 12));
        });
        return row;
    }

    private EditText searchField(Context context) {
        EditText input = new EditText(context);
        input.setHint(R.string.skillhub_search_hint);
        input.setTextSize(Tokens.FONT_SM);
        input.setSingleLine(true);
        input.setIncludeFontPadding(false);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setImeOptions(EditorInfo.IME_ACTION_SEARCH);
        input.setBackgroundColor(android.graphics.Color.TRANSPARENT);
        input.setOnEditorActionListener((view, action, event) -> {
            page = 1;
            reload();
            return true;
        });
        theme.bind(t -> {
            input.setTextColor(t.text);
            input.setHintTextColor(t.textTertiary);
        });
        return input;
    }

    private View searchBox(Context context, EditText input) {
        LinearLayout box = new LinearLayout(context);
        box.setGravity(Gravity.CENTER_VERTICAL);
        box.setMinimumHeight(Ui.dp(context, 48));
        Ui.padding(box, 8, 0, 8, 0);
        IconButton searchIcon = iconView(Icons.SEARCH, 18);
        searchIcon.setIconColor(theme.current().textTertiary);
        theme.bind(t -> searchIcon.setIconColor(t.textTertiary));
        box.addView(searchIcon, new LinearLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 44)));
        box.addView(input, new LinearLayout.LayoutParams(0, Ui.dp(context, 48), 1f));
        theme.bind(t -> box.setBackground(
                Ui.roundedStroke(context, t.inputBg, 12, t.borderLight)));
        return box;
    }

    private View accountCard(Context context) {
        LinearLayout card = new LinearLayout(context);
        card.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(card, 12, 8, 12, 8);
        card.setOnClickListener(v -> onAccount());
        FrameLayout slot = new FrameLayout(context);
        slot.addView(iconView(Icons.USER, 19), new FrameLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 36), Gravity.CENTER));
        card.addView(slot, new LinearLayout.LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(context, 8);
        card.addView(labels, labelParams);
        labels.addView(accountTitle, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 2);
        labels.addView(accountDescription, descParams);
        card.addView(accountIcon, new LinearLayout.LayoutParams(Ui.dp(context, 28),
                Ui.dp(context, 28)));
        theme.bind(t -> {
            slot.setBackground(Ui.rounded(context, t.accentMuted, 9));
            card.setBackground(Ui.rounded(context, t.surfaceElevated, 12));
        });
        return card;
    }

    private TextView accountTitleView(Context context) {
        TextView view = Ui.medium(context, "", Tokens.FONT_SM, theme.current().text);
        return view;
    }

    private TextView accountDescriptionView(Context context) {
        TextView view = Ui.text(context, "", Tokens.FONT_XS, theme.current().textTertiary,
                Typeface.NORMAL);
        theme.bind(t -> view.setTextColor(t.textTertiary));
        return view;
    }

    private IconButton accountIconView(Context context) {
        IconButton icon = new IconButton(context, Icons.CHEVRON_RIGHT);
        icon.setClickable(false);
        return icon;
    }

    private void onAccount() {
        if (accountState == 0) return;
        if (accountState == 2) {
            loadAccount();
            Toast.makeText(getContext(), R.string.skillhub_checking_account,
                    Toast.LENGTH_SHORT).show();
            return;
        }
        if (accountState == 1 && openLogin != null) {
            openLogin.run();
            return;
        }
        Toast.makeText(getContext(), accountState == 3
                ? R.string.skillhub_logged_in : R.string.skillhub_login_desc,
                Toast.LENGTH_SHORT).show();
    }

    private void loadAccount() {
        accountState = 0;
        updateAccount();
        scheduler.io(() -> {
            boolean signed;
            try {
                signed = new SkillHubSession().signedIn();
            } catch (Exception error) {
                signed = false;
            }
            final boolean result = signed;
            scheduler.main(() -> {
                accountState = result ? 3 : 1;
                updateAccount();
            });
        });
    }

    private void updateAccount() {
        Tokens tokens = theme.current();
        String title;
        String description;
        int icon;
        if (accountState == 0) {
            title = getContext().getString(R.string.skillhub_checking_account);
            description = getContext().getString(R.string.skillhub_login_via_official);
            icon = Icons.EXTERNAL_LINK;
        } else if (accountState == 1) {
            title = getContext().getString(R.string.skillhub_login_account);
            description = getContext().getString(R.string.skillhub_login_desc);
            icon = Icons.CHEVRON_RIGHT;
        } else if (accountState == 3) {
            title = getContext().getString(R.string.skillhub_logged_in);
            description = getContext().getString(R.string.skillhub_login_via_official);
            icon = Icons.CHEVRON_RIGHT;
        } else {
            title = getContext().getString(R.string.skillhub_account_check_failed);
            description = getContext().getString(R.string.skillhub_retry_here);
            icon = Icons.REFRESH_CW;
        }
        accountTitle.setText(title);
        accountDescription.setText(description);
        accountIcon.setIconType(icon);
        accountIcon.setIconColor(tokens.textTertiary);
        renderFeatureCenter();
    }

    private static final String[] SORTS = {"downloads", "stars", "updated_at"};
    private static final int[] SORT_LABELS = {R.string.skillhub_filter_hot_downloads,
            R.string.skillhub_filter_most_stars, R.string.skillhub_filter_recent_update};

    private void renderFilters() {
        Context context = getContext();
        filtersHost.removeAllViews();
        for (int i = 0; i < SORTS.length; i++) {
            final String value = SORTS[i];
            boolean selected = value.equals(sort);
            TextView chip = Ui.medium(context, context.getString(SORT_LABELS[i]), 11,
                    selected ? theme.current().accent : theme.current().textSecondary);
            chip.setGravity(Gravity.CENTER);
            Ui.padding(chip, 8, 8, 8, 8);
            chip.setOnClickListener(v -> {
                if (sort.equals(value)) return;
                sort = value;
                page = 1;
                renderFilters();
                reload();
            });
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0,
                    Ui.dp(context, 34), 1f);
            if (i > 0) params.leftMargin = Ui.dp(context, 4);
            filtersHost.addView(chip, params);
            theme.bind(t -> {
                chip.setTextColor(selected ? t.accent : t.textSecondary);
                chip.setBackground(Ui.roundedStroke(context,
                        selected ? t.accentMuted : t.surfaceElevated, 10,
                        selected ? t.accent : t.border));
            });
        }
    }

    private void renderFeatureCenter() {
        featureHost.removeAllViews();
        boolean signedIn = accountState == 3;
        featureHost.setVisibility(signedIn && openPublish != null ? View.VISIBLE : View.GONE);
        if (!signedIn || openPublish == null) return;
        Context context = getContext();
        TextView action = Ui.medium(context,
                context.getString(R.string.skillhub_feature_center), 13, theme.current().accent);
        action.setGravity(Gravity.CENTER);
        Ui.padding(action, 12, 8, 12, 8);
        action.setOnClickListener(v -> openPublish.run());
        theme.bind(t -> {
            action.setTextColor(t.accent);
            action.setBackground(Ui.roundedStroke(context, t.surfaceElevated, 12, t.accent));
        });
        featureHost.addView(action, new LinearLayout.LayoutParams(-1, Ui.dp(context, 40)));
    }

    private void reload() {
        loading = true;
        renderList(null, null);
        final String keyword = search.getText() == null ? "" : search.getText().toString();
        scheduler.io(() -> {
            try {
                List<SkillHubEntry> entries = api.list(keyword, page, sort);
                scheduler.main(() -> {
                    loading = false;
                    renderList(entries, null);
                });
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> {
                    loading = false;
                    renderList(null, message);
                });
            }
        });
    }

    private void renderList(List<SkillHubEntry> entries, String error) {
        listHost.removeAllViews();
        if (loading) {
            addToHost(spinner(), 16);
            addToHost(center(getContext().getString(R.string.skillhub_loading_page, page)), 8);
            return;
        }
        if (error != null) {
            TextView retry = center(getContext().getString(
                    R.string.skillhub_load_failed_retry));
            retry.setOnClickListener(v -> reload());
            addToHost(retry, 16);
            addToHost(ExtensionKit.muted(getContext(), theme,
                    R.string.skillhub_unknown_error, Tokens.FONT_SM), 4);
            return;
        }
        List<SkillHubEntry> safe = entries == null ? new ArrayList<>() : entries;
        addToHost(center(safe.isEmpty()
                ? getContext().getString(R.string.skillhub_no_matching_skills)
                : getContext().getString(R.string.skillhub_page_count, page,
                        String.valueOf(safe.size()))), 12);
        for (SkillHubEntry entry : safe) {
            addToHost(card(entry), 8);
        }
        addToHost(pager(), 12);
    }

    private View card(SkillHubEntry entry) {
        LinearLayout row = new LinearLayout(getContext());
        row.setGravity(Gravity.CENTER_VERTICAL);
        Ui.padding(row, 12, 12, 8, 12);
        row.setClickable(true);
        row.setBackground(Ui.roundedStroke(getContext(), theme.current().surfaceElevated, 12,
                theme.current().border));
        row.setOnClickListener(v -> openDetail.accept(entry.slug));
        FrameLayout artwork = new FrameLayout(getContext());
        artwork.addView(iconView(Icons.PACKAGE, 25), new FrameLayout.LayoutParams(
                Ui.dp(getContext(), 48), Ui.dp(getContext(), 48), Gravity.CENTER));
        row.addView(artwork, new LinearLayout.LayoutParams(Ui.dp(getContext(), 48), Ui.dp(getContext(), 48)));
        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(0, -2, 1f);
        labelParams.leftMargin = Ui.dp(getContext(), 12);
        labelParams.rightMargin = Ui.dp(getContext(), 4);
        row.addView(labels, labelParams);
        TextView title = Ui.medium(getContext(), entry.name, Tokens.FONT_MD, theme.current().text);
        labels.addView(title, new LinearLayout.LayoutParams(-1, -2));
        TextView author = Ui.text(getContext(), entry.author, Tokens.FONT_XS,
                theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams authorParams = new LinearLayout.LayoutParams(-1, -2);
        authorParams.topMargin = Ui.dp(getContext(), 4);
        labels.addView(author, authorParams);
        TextView description = Ui.text(getContext(), entry.description, Tokens.FONT_SM,
                theme.current().textSecondary, Typeface.NORMAL);
        description.setMaxLines(2);
        description.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(getContext(), 4);
        labels.addView(description, descParams);
        TextView meta = Ui.text(getContext(), getContext().getString(
                R.string.skillhub_card_meta, String.valueOf(entry.downloads), entry.version),
                Tokens.FONT_XS, theme.current().textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = Ui.dp(getContext(), 4);
        labels.addView(meta, metaParams);
        IconButton chevron = iconView(Icons.CHEVRON_RIGHT, 16);
        chevron.setIconColor(theme.current().textTertiary);
        theme.bind(t -> chevron.setIconColor(t.textTertiary));
        row.addView(chevron, new LinearLayout.LayoutParams(
                Ui.dp(getContext(), 24), Ui.dp(getContext(), 24)));
        theme.bind(t -> {
            artwork.setBackground(Ui.rounded(getContext(), t.accentMuted, 12));
            title.setTextColor(t.text);
            author.setTextColor(t.textTertiary);
            description.setTextColor(t.textSecondary);
            meta.setTextColor(t.textTertiary);
            row.setBackground(Ui.roundedStroke(getContext(), t.surfaceElevated, 12, t.border));
        });
        return row;
    }

    private View pager() {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout previous = ExtensionKit.button(getContext(), theme,
                R.string.skillhub_previous_page, false, () -> {
                    if (page <= 1) return;
                    page--;
                    reload();
                });
        LinearLayout next = ExtensionKit.button(getContext(), theme,
                R.string.skillhub_next_page, false, () -> {
                    page++;
                    reload();
                });
        row.addView(previous, new LinearLayout.LayoutParams(0, -2, 1f));
        LinearLayout.LayoutParams nextParams = new LinearLayout.LayoutParams(0, -2, 1f);
        nextParams.leftMargin = Ui.dp(getContext(), 8);
        row.addView(next, nextParams);
        return row;
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

    private TextView center(String text) {
        TextView view = Ui.text(getContext(), text, Tokens.FONT_SM,
                theme.current().textTertiary, Typeface.NORMAL);
        view.setGravity(Gravity.CENTER);
        theme.bind(t -> view.setTextColor(t.textTertiary));
        return view;
    }

    private IconButton iconView(int icon, int sizeDp) {
        IconButton view = new IconButton(getContext(), icon);
        view.setIconSizeDp(sizeDp, sizeDp);
        view.setClickable(false);
        theme.bind(t -> view.setIconColor(t.accent));
        return view;
    }

    private void addRow(View view, int topDp) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        if (topDp > 0) params.topMargin = Ui.dp(getContext(), topDp);
        content.addView(view, params);
    }

    private void addToHost(View view, int topDp) {
        ExtensionKit.topMargin(getContext(), view, topDp);
        listHost.addView(view);
    }
}
