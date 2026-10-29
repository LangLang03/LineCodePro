package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.data.api.SkillStore;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.FormField;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Downloads a GitHub archive and installs the SKILL.md inside it. */
public final class GitHubSkillPage extends SettingsPage {

    private final Scheduler scheduler;
    private final SkillStore store;
    private final FormField url;
    private final TextView status;

    public GitHubSkillPage(Context context, Tokens tokens, Runnable back, Scheduler scheduler,
                           SkillStore store) {
        super(context, R.string.skill_github_title, tokens, back);
        this.scheduler = scheduler;
        this.store = store;
        Ui.padding(content, 16, 16, 16, 100);
        content.addView(ExtensionKit.notice(context, theme, Icons.GLOBE,
                R.string.screen_extension_detail_install_github_desc),
                new LinearLayout.LayoutParams(-1, -2));
        url = new FormField(context, theme, R.string.screen_extension_detail_field_github_url, "",
                context.getString(R.string.skill_github_hint), false);
        url.input().setTextSize(Tokens.FONT_SM);
        LinearLayout.LayoutParams urlParams = new LinearLayout.LayoutParams(-1, -2);
        urlParams.topMargin = Ui.dp(context, 16);
        content.addView(url, urlParams);
        LinearLayout install = ExtensionKit.primaryButton(context, theme,
                R.string.skill_github_install, 48, this::install);
        LinearLayout.LayoutParams installParams = new LinearLayout.LayoutParams(-1, -2);
        installParams.topMargin = Ui.dp(context, 12);
        content.addView(install, installParams);
        status = Ui.text(context, "", Tokens.FONT_SM, theme.current().textTertiary,
                Typeface.NORMAL);
        status.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams statusParams = new LinearLayout.LayoutParams(-1, -2);
        statusParams.topMargin = Ui.dp(context, 12);
        content.addView(status, statusParams);
        theme.bind(t -> status.setTextColor(t.textTertiary));
    }

    private void install() {
        String value = url.value();
        if (!value.startsWith("https://")) {
            status.setText(getContext().getString(R.string.skill_github_invalid_url));
            return;
        }
        status.setText(getContext().getString(R.string.skillhub_loading));
        RemoteSkillInstall.run(scheduler, store,
                () -> GitHubSkillDownloader.download(value), "github.zip",
                new RemoteSkillInstall.Callback() {
                    public void onSuccess() {
                        status.setText(getContext().getString(R.string.screen_skill_imported));
                        Toast.makeText(getContext(), R.string.screen_skill_imported,
                                Toast.LENGTH_SHORT).show();
                    }

                    public void onError(String message) {
                        status.setText(getContext().getString(R.string.skill_github_failed,
                                message));
                    }
                });
    }
}
