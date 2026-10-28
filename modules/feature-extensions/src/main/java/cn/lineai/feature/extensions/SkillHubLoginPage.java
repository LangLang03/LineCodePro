package cn.lineai.feature.extensions;

import android.annotation.SuppressLint;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import cn.lineai.core.kernel.time.Cancellable;
import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** SkillHub official login inside a hardened WebView. */
public final class SkillHubLoginPage extends SettingsPage {

    public interface Listener {
        void onLoggedIn();
    }

    private static final String LOGIN_URL = "https://skillhub.cn/";
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "skillhub.cn", "www.skillhub.cn", "api.skillhub.cn",
            "workspace.tencent.com", "account.tencent.com"));

    private final Scheduler scheduler;
    private final Listener listener;
    private final WebView webView;
    private Cancellable poll;
    private boolean checking;
    private boolean finished;

    public SkillHubLoginPage(Context context, Tokens tokens, Runnable back,
                             Scheduler scheduler, Listener listener) {
        super(context, R.string.skillhub_official_login, tokens, back);
        this.scheduler = scheduler;
        this.listener = listener;
        Ui.padding(content, 16, 8, 16, 24);
        TextView notice = Ui.text(context,
                context.getString(R.string.skillhub_credential_notice),
                Tokens.FONT_XS, tokens.textSecondary, android.graphics.Typeface.NORMAL);
        notice.setBackground(Ui.rounded(context, tokens.accentMuted, 10));
        Ui.padding(notice, 12, 8, 12, 8);
        content.addView(notice, new LinearLayout.LayoutParams(-1, -2));
        TextView status = Ui.text(context,
                context.getString(R.string.skillhub_auto_return_notice),
                Tokens.FONT_XS, tokens.textTertiary, android.graphics.Typeface.NORMAL);
        Ui.padding(status, 0, 8, 0, 8);
        content.addView(status, new LinearLayout.LayoutParams(-1, -2));

        webView = new WebView(context);
        harden(webView);
        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= 21) cookies.setAcceptThirdPartyCookies(webView, false);
        webView.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allowed(request == null ? null : request.getUrl().toString());
            }

            @SuppressWarnings("deprecation")
            @Override public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !allowed(url);
            }

            @Override public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT >= 21) CookieManager.getInstance().flush();
                check();
            }
        });
        webView.loadUrl(LOGIN_URL);
        int height = Math.round(context.getResources().getDisplayMetrics().heightPixels * 0.62f);
        content.addView(webView, new LinearLayout.LayoutParams(-1, height));
        poll = scheduler.main(1200, this::check);
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void harden(WebView view) {
        WebSettings settings = view.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setDatabaseEnabled(false);
        if (Build.VERSION.SDK_INT >= 21) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
    }

    private static boolean allowed(String url) {
        if (url == null) return false;
        try {
            Uri uri = Uri.parse(url);
            String host = uri.getHost();
            if ("https".equalsIgnoreCase(uri.getScheme()) && host != null
                    && ALLOWED_HOSTS.contains(host.toLowerCase(java.util.Locale.ROOT))) {
                return true;
            }
        } catch (Exception error) {
            return false;
        }
        return false;
    }

    private void check() {
        if (finished || checking || !isAttachedToWindow()) return;
        checking = true;
        scheduler.io(() -> {
            boolean signedIn;
            try {
                signedIn = new SkillHubSession().signedIn();
            } catch (Exception error) {
                signedIn = false;
            }
            final boolean result = signedIn;
            scheduler.main(() -> {
                checking = false;
                if (result && !finished) {
                    finished = true;
                    listener.onLoggedIn();
                } else if (!finished) {
                    poll = scheduler.main(1200, this::check);
                }
            });
        });
    }

    @Override protected void onDetachedFromWindow() {
        if (poll != null) {
            poll.cancel();
            poll = null;
        }
        webView.stopLoading();
        super.onDetachedFromWindow();
    }
}
