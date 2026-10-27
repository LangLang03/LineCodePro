package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import java.util.List;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.core.model.SkillHubEntry;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.skillhub.SkillHubApi;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Read-only SkillHub browser with one-tap install. */
public final class SkillHubPage extends SettingsPage {

    private final Scheduler scheduler;
    private final SkillStore store;
    private final SkillHubApi api = new SkillHubApi();
    private final FormField keyword;
    private final TextView status;
    private final LinearLayout results;

    private final java.util.function.Consumer<String> openDetail;

    public SkillHubPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                        SkillStore store, java.util.function.Consumer<String> openDetail) {
        super(context, R.string.skillhub_title_store, tokens, back);
        this.openDetail = openDetail;
        this.scheduler = scheduler;
        this.store = store;
        Ui.padding(content, 16, 16, 16, 100);
        keyword = new FormField(context, theme, R.string.skillhub_search_label, "",
                context.getString(R.string.skillhub_search_hint), false);
        content.addView(keyword, new LinearLayout.LayoutParams(-1, -2));
        TextView search = Ui.bold(context, context.getString(R.string.skillhub_search_action),
                Tokens.FONT_MD, tokens.textOnColor);
        search.setGravity(Gravity.CENTER);
        search.setClickable(true);
        search.setBackground(Ui.rounded(context, tokens.accent, 12));
        search.setOnClickListener(v -> load());
        LinearLayout.LayoutParams searchParams = new LinearLayout.LayoutParams(-1, Ui.dp(context, 44));
        searchParams.topMargin = Ui.dp(context, 10);
        content.addView(search, searchParams);

        status = Ui.text(context, "", Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        status.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = Ui.dp(context, 10);
        content.addView(status, statusParams);

        results = new LinearLayout(context);
        results.setOrientation(LinearLayout.VERTICAL);
        content.addView(results, new LinearLayout.LayoutParams(-1, -2));
        load();
    }

    private void load() {
        status.setText(getContext().getString(R.string.skillhub_loading));
        final String query = keyword.value();
        scheduler.io(() -> {
            try {
                List<SkillHubEntry> entries = api.list(query, 1);
                scheduler.main(() -> render(entries));
            } catch (Exception error) {
                scheduler.main(() -> status.setText(getContext().getString(
                        R.string.skillhub_load_failed, error.getMessage() == null
                                ? error.getClass().getSimpleName() : error.getMessage())));
            }
        });
    }

    private void render(List<SkillHubEntry> entries) {
        results.removeAllViews();
        status.setText(entries.isEmpty() ? getContext().getString(R.string.skillhub_empty) : "");
        for (SkillHubEntry entry : entries) {
            results.addView(row(entry), rowParams());
        }
    }

    private LinearLayout row(SkillHubEntry entry) {
        Context context = getContext();
        Tokens tokens = theme.current();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.roundedStroke(context, tokens.surfaceElevated, 12, tokens.border));
        Ui.padding(card, 14, 12, 14, 12);
        TextView name = Ui.bold(context, entry.name, Tokens.FONT_MD, tokens.text);
        name.setClickable(true);
        name.setOnClickListener(v -> openDetail.accept(entry.slug));
        card.addView(name, new LinearLayout.LayoutParams(-1, -2));
        TextView desc = Ui.text(context, entry.description, Tokens.FONT_XS,
                tokens.textTertiary, Typeface.NORMAL);
        desc.setLineSpacing(Ui.dp(context, 3), 1f);
        desc.setMaxLines(3);
        desc.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(-1, -2);
        descParams.topMargin = Ui.dp(context, 4);
        card.addView(desc, descParams);
        TextView meta = Ui.text(context, entry.author + " · v" + entry.version
                + " · " + entry.downloads, Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        LinearLayout.LayoutParams metaParams = new LinearLayout.LayoutParams(-1, -2);
        metaParams.topMargin = Ui.dp(context, 6);
        card.addView(meta, metaParams);
        TextView install = Ui.bold(context,
                context.getString(R.string.skillhub_install_skill, entry.name),
                Tokens.FONT_SM, tokens.accent);
        install.setClickable(true);
        Ui.padding(install, 0, 10, 0, 2);
        install.setOnClickListener(v -> install(entry, install));
        card.addView(install, new LinearLayout.LayoutParams(-2, -2));
        theme.bind(t -> card.setBackground(
                Ui.roundedStroke(context, t.surfaceElevated, 12, t.border)));
        return card;
    }

    private void install(SkillHubEntry entry, TextView button) {
        button.setEnabled(false);
        status.setText(getContext().getString(R.string.skillhub_installing));
        RemoteSkillInstall.run(scheduler, store,
                () -> api.download(entry.slug, entry.version), entry.slug + ".zip",
                new RemoteSkillInstall.Callback() {
                    public void onSuccess() {
                        status.setText(getContext().getString(R.string.skillhub_install_success));
                        Toast.makeText(getContext(), R.string.skillhub_install_success,
                                Toast.LENGTH_SHORT).show();
                    }
                    public void onError(String message) {
                        button.setEnabled(true);
                        status.setText(getContext().getString(R.string.skillhub_load_failed, message));
                    }
                });
    }

    private LinearLayout.LayoutParams rowParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, -2);
        params.bottomMargin = Ui.dp(getContext(), 8);
        return params;
    }
}
