package cn.lineai.ui.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.R;
import cn.lineai.data.service.ContextResourceProvider;
import cn.lineai.data.service.SkillHubSessionClient;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

public final class SkillHubLoginScreenView extends LinearLayout {
    private static final String LOGIN_URL = "https://skillhub.cn/";
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "skillhub.cn",
            "www.skillhub.cn",
            "api.skillhub.cn",
            "workspace.tencent.com",
            "account.tencent.com"
    ));

    private static final long SESSION_CHECK_INTERVAL_MS = 1000L;

    private final SkillHubSessionClient sessionClient;
    private final Handler main = new Handler(Looper.getMainLooper());
    private final Runnable onLoginComplete;
    private final WebView webView;
    private final TextView status;
    private boolean completing;
    private boolean checkInFlight;
    private boolean detached;

    public SkillHubLoginScreenView(Context context, Runnable onBack, Runnable onLoginComplete) {
        super(context);
        this.onLoginComplete = onLoginComplete;
        this.sessionClient = new SkillHubSessionClient(new ContextResourceProvider(context));
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        addView(new ScreenHeaderView(context, context.getString(R.string.skillhub_official_login), onBack, null),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout notice = new LinearLayout(context);
        notice.setOrientation(HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 10));
        LineTheme.padding(notice, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        IconButtonView shield = new IconButtonView(context, IconButtonView.SHIELD_CHECK);
        shield.setIconColor(LineTheme.ACCENT);
        shield.setIconSizeDp(28, 17);
        shield.setClickable(false);
        shield.setFocusable(false);
        notice.addView(shield, new LayoutParams(LineTheme.dp(context, 28), LineTheme.dp(context, 28)));
        TextView noticeText = LineTheme.text(context,
                context.getString(R.string.skillhub_credential_notice),
                LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LayoutParams noticeTextParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        noticeTextParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        notice.addView(noticeText, noticeTextParams);
        LayoutParams noticeParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        noticeParams.setMargins(LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.LG), 0);
        addView(notice, noticeParams);

        status = LineTheme.text(context, context.getString(R.string.skillhub_auto_return_notice),
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        status.setGravity(Gravity.CENTER);
        LayoutParams statusParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        statusParams.setMargins(LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.LG), LineTheme.dp(context, LineTheme.SM));
        addView(status, statusParams);

        webView = new WebView(context);
        webView.setContentDescription(context.getString(R.string.skillhub_login_page_desc));
        harden(webView);
        CookieManager cookieManager = CookieManager.getInstance();
        cookieManager.setAcceptCookie(true);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            cookieManager.setAcceptThirdPartyCookies(webView, false);
        }
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowed(request == null ? null : request.getUrl());
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowed(url == null ? null : Uri.parse(url));
            }

            @Override
            public void onPageFinished(WebView view, String url) {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                    CookieManager.getInstance().flush();
                }
                checkLogin();
            }
        });
        webView.loadUrl(LOGIN_URL);
        addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        main.post(sessionCheck);
    }

    private final Runnable sessionCheck = new Runnable() {
        @Override
        public void run() {
            checkLogin();
            if (!completing && !detached) {
                main.postDelayed(this, SESSION_CHECK_INTERVAL_MS);
            }
        }
    };

    private void checkLogin() {
        if (completing || detached || checkInFlight) {
            return;
        }
        checkInFlight = true;
        new Thread(() -> {
            try {
                SkillHubSessionClient.Session session = sessionClient.currentSession();
                main.post(() -> {
                    checkInFlight = false;
                    if (!session.isAuthenticated() || completing || detached) {
                        return;
                    }
                    completing = true;
                    main.removeCallbacks(sessionCheck);
                    String name = session.getAccount().getDisplayName();
                    status.setText(getContext().getString(R.string.skillhub_logged_in)
                            + (name.length() == 0 ? "" : "：" + name));
                    status.setTextColor(LineTheme.ACCENT);
                    Toast.makeText(getContext(), getContext().getString(R.string.skillhub_login_success),
                            Toast.LENGTH_SHORT).show();
                    main.postDelayed(onLoginComplete, 150);
                });
            } catch (Exception ignored) {
                main.post(() -> checkInFlight = false);
            }
        }, "skillhub-login-check").start();
    }

    @Override
    protected void onDetachedFromWindow() {
        detached = true;
        main.removeCallbacksAndMessages(null);
        webView.stopLoading();
        webView.setWebViewClient(null);
        webView.destroy();
        super.onDetachedFromWindow();
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void harden(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
        WebView.setWebContentsDebuggingEnabled(false);
    }

    static boolean isAllowed(Uri uri) {
        return uri != null
                && "https".equalsIgnoreCase(uri.getScheme())
                && ALLOWED_HOSTS.contains(uri.getHost());
    }
}
