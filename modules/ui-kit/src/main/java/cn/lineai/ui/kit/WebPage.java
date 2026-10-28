package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.widget.TextView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.Tokens;

public final class WebPage extends SettingsPage {
    private final WebView browser;

    public WebPage(Context context, Tokens tokens, String url, boolean allowHttp,
                   boolean javaScript, Runnable back) {
        super(context, R.string.in_app_browser_default_title, tokens, back);
        if (!allows(url, allowHttp)) throw new IllegalArgumentException("Unsupported URL");
        removeView(scroll);
        TextView address = Ui.text(context, url, 13, tokens.textSecondary, Typeface.NORMAL);
        address.setSingleLine(true);
        address.setEllipsize(TextUtils.TruncateAt.MIDDLE);
        Ui.padding(address, 28, 0, 28, 16);
        addView(address, new LayoutParams(-1, -2));
        browser = new WebView(context);
        browser.setContentDescription(context.getString(R.string.in_app_browser_content_desc));
        WebSettings settings = browser.getSettings();
        settings.setJavaScriptEnabled(javaScript);
        settings.setAllowFileAccess(false);
        settings.setAllowContentAccess(false);
        settings.setAllowFileAccessFromFileURLs(false);
        settings.setAllowUniversalAccessFromFileURLs(false);
        settings.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        settings.setDomStorageEnabled(true);
        browser.setWebViewClient(new WebViewClient() {
            @Override public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                return !allows(request.getUrl().toString(), allowHttp);
            }
            @Override public boolean shouldOverrideUrlLoading(WebView view, String next) {
                return !allows(next, allowHttp);
            }
        });
        addView(browser, new LayoutParams(-1, 0, 1));
        theme.bind(t -> {
            address.setTextColor(t.textSecondary);
            browser.setBackgroundColor(t.bg);
        });
        browser.loadUrl(url);
    }

    public static boolean allows(String value, boolean allowHttp) {
        return cn.lineai.platform.LinkPolicy.allows(value, allowHttp);
    }

    @Override public void dispose() {
        browser.stopLoading();
        removeView(browser);
        browser.destroy();
    }
}
