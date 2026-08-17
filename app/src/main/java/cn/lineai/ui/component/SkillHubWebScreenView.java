package cn.lineai.ui.component;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.Build;
import android.view.Gravity;
import android.webkit.CookieManager;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

public final class SkillHubWebScreenView extends LinearLayout {
    private static final String SITE_ROOT = "https://skillhub.cn";
    private static final Set<String> ALLOWED_HOSTS = new HashSet<>(Arrays.asList(
            "skillhub.cn",
            "www.skillhub.cn",
            "api.skillhub.cn",
            "workspace.tencent.com",
            "account.tencent.com"
    ));
    private static final Map<String, Destination> DESTINATIONS = destinations();

    private final WebView webView;

    @SuppressLint("SetJavaScriptEnabled")
    public SkillHubWebScreenView(Context context, String destinationId, Runnable onBack) {
        super(context);
        Destination destination = requireDestination(destinationId);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);
        addView(new ScreenHeaderView(context, destination.title, onBack, null),
                new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout notice = new LinearLayout(context);
        notice.setOrientation(HORIZONTAL);
        notice.setGravity(Gravity.CENTER_VERTICAL);
        notice.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 10));
        LineTheme.padding(notice, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        IconButtonView shield = new IconButtonView(context, IconButtonView.SHIELD_CHECK);
        shield.setIconColor(LineTheme.ACCENT);
        shield.setIconSizeDp(26, 16);
        shield.setClickable(false);
        shield.setFocusable(false);
        notice.addView(shield, new LayoutParams(LineTheme.dp(context, 26), LineTheme.dp(context, 26)));
        TextView text = LineTheme.text(context,
                "此功能由 SkillHub 官方页面提供，会话仅发送到允许的官方域名。",
                LineTheme.FONT_XS, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        notice.addView(text, textParams);
        LayoutParams noticeParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        noticeParams.setMargins(LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.SM),
                LineTheme.dp(context, LineTheme.MD), LineTheme.dp(context, LineTheme.SM));
        addView(notice, noticeParams);

        CookieManager cookies = CookieManager.getInstance();
        cookies.setAcceptCookie(true);
        webView = new WebView(context);
        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setSupportMultipleWindows(false);
        settings.setJavaScriptCanOpenWindowsAutomatically(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
            cookies.setAcceptThirdPartyCookies(webView, false);
        }
        WebView.setWebContentsDebuggingEnabled(false);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !isAllowed(request.getUrl());
            }

            @Override
            public boolean shouldOverrideUrlLoading(WebView view, String url) {
                return !isAllowed(Uri.parse(url));
            }
        });
        addView(webView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
        webView.loadUrl(SITE_ROOT + destination.path);
    }

    @Override
    protected void onDetachedFromWindow() {
        webView.stopLoading();
        webView.setWebViewClient(null);
        webView.destroy();
        super.onDetachedFromWindow();
    }

    private static boolean isAllowed(Uri uri) {
        return uri != null && "https".equalsIgnoreCase(uri.getScheme())
                && uri.getHost() != null && ALLOWED_HOSTS.contains(uri.getHost().toLowerCase());
    }

    private static Destination requireDestination(String id) {
        Destination destination = DESTINATIONS.get(id);
        if (destination == null && id != null && id.startsWith("skill:")) {
            String[] parts = id.split(":", -1);
            if (parts.length == 3 && safeSegment(parts[1]) && safeSegment(parts[2])) {
                destination = new Destination("SkillHub 完整详情",
                        "/skills/" + parts[1] + "/" + parts[2]);
            }
        }
        if (destination == null) {
            throw new IllegalArgumentException("无效的 SkillHub 功能入口");
        }
        return destination;
    }

    private static boolean safeSegment(String value) {
        return value != null && value.matches("[A-Za-z0-9][A-Za-z0-9._-]{0,127}");
    }

    private static Map<String, Destination> destinations() {
        HashMap<String, Destination> values = new HashMap<>();
        add(values, "account", "个人中心", "/dashboard");
        add(values, "settings", "账号设置", "/dashboard/settings");
        add(values, "verify", "实名认证", "/dashboard/verify");
        add(values, "tokens", "API Token", "/dashboard/keys");
        add(values, "stars", "我的收藏", "/dashboard/stars");
        add(values, "following", "我的关注", "/dashboard/following");
        add(values, "notifications", "通知中心", "/notifications");
        add(values, "creator", "创作者中心", "/dashboard");
        add(values, "publish", "发布 Skill", "/dashboard/publish");
        add(values, "skillsets", "SkillSet", "/skillspackage");
        add(values, "mcp", "MCP Server", "/mcp");
        add(values, "skill-hunt", "Skill Hunt", "/skill-hunt");
        add(values, "contest", "赛事", "/contest");
        add(values, "enterprises", "企业广场", "/enterprise-zone");
        add(values, "enterprise-dashboard", "企业工作台", "/enterprise/dashboard");
        add(values, "enterprise-publish", "企业发布", "/enterprise/dashboard/publish");
        add(values, "merchant", "商户管理", "/admin/merchant");
        add(values, "admin", "管理后台", "/admin");
        add(values, "admin-reviews", "Skill 审核", "/admin/skill-reviews");
        return Collections.unmodifiableMap(values);
    }

    private static void add(Map<String, Destination> values, String id, String title, String path) {
        values.put(id, new Destination(title, path));
    }

    private static final class Destination {
        final String title;
        final String path;

        Destination(String title, String path) {
            this.title = title;
            this.path = path;
        }
    }
}
