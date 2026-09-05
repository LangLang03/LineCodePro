package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.security.UrlPolicy;

public final class InAppBrowserScreenView extends ScreenSurfaceView {
    public interface Listener {
        void onBack();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

    public InAppBrowserScreenView(Context context, String url, boolean javaScriptEnabled, Listener listener) {
        super(context);
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, context.getString(R.string.in_app_browser_default_title), listener::onBack, null), new LayoutParams(-1,-2));

        TextView address = LineTheme.text(context, url == null ? "" : url, 13, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        address.setSingleLine(true); address.setEllipsize(android.text.TextUtils.TruncateAt.MIDDLE);
        LineTheme.padding(address,28,0,28,16); addView(address,new LayoutParams(-1,-2));
        WebView webView = new WebView(context);
        webView.setBackgroundColor(LineTheme.BG);
        webView.setContentDescription(getContext().getString(R.string.in_app_browser_content_desc));
        hardenWebView(webView);
        setJavaScriptEnabled(webView, javaScriptEnabled);
        webView.getSettings().setDomStorageEnabled(true);
        webView.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                String nextUrl = request == null || request.getUrl() == null ? "" : request.getUrl().toString();
                return UrlPolicy.normalizeHttpOrLocalCleartextUrl(nextUrl).length() == 0;
            }

            @Override
            @SuppressWarnings("deprecation")
            public boolean shouldOverrideUrlLoading(WebView view, String nextUrl) {
                return UrlPolicy.normalizeHttpOrLocalCleartextUrl(nextUrl).length() == 0;
            }
        });
        String safeUrl = UrlPolicy.normalizeHttpOrLocalCleartextUrl(url);
        if (safeUrl.length() > 0) {
            webView.loadUrl(safeUrl);
        } else {
            webView.loadDataWithBaseURL(null, "Unsupported URL", "text/plain", "utf-8", null);
        }
        addView(webView, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));
    }

    @SuppressLint("SetJavaScriptEnabled")
    private static void setJavaScriptEnabled(WebView webView, boolean enabled) {
        webView.getSettings().setJavaScriptEnabled(enabled);
    }

    private static void hardenWebView(WebView webView) {
        WebSettings settings = webView.getSettings();
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.JELLY_BEAN) {
            settings.setAllowFileAccessFromFileURLs(false);
            settings.setAllowUniversalAccessFromFileURLs(false);
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        }
    }
}
