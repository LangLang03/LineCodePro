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

/**
 * In-app browser page built on the {@link SettingsPage} frame.
 *
 * <p>The page replaces the scroll container with an address label and a sandboxed {@link WebView}.
 * Navigation is filtered through {@link cn.lineai.core.service.LinkPolicy} so neither the initial
 * load nor any in-page redirect can leave the allowed URL scheme space; the HTTP allowance is fixed
 * at construction time. Disposal must go through {@link #dispose} so the WebView is stopped and
 * destroyed instead of leaking its renderer.</p>
 */
public final class WebPage extends SettingsPage {
    private final WebView browser;

    /**
     * Creates the browser page and starts loading {@code url} immediately.
     *
     * @param context hosting context
     * @param tokens initial palette; further theme changes flow through the inherited binder
     * @param url target URL; must satisfy {@link #allows} for the given {@code allowHttp} flag
     * @param allowHttp true to permit plain HTTP targets in addition to secure schemes
     * @param javaScript true to enable JavaScript inside the WebView; file and content access are
     *     always disabled regardless of this flag
     * @param back action run when the header back button is pressed
     * @throws IllegalArgumentException when {@code url} is rejected by {@link #allows}
     */
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

    /**
     * Delegates the URL allow-list decision to the central {@code LinkPolicy}.
     *
     * @param value candidate URL string
     * @param allowHttp true when plain HTTP targets are acceptable
     * @return true when the URL may be loaded
     */
    public static boolean allows(String value, boolean allowHttp) {
        return cn.lineai.core.service.LinkPolicy.allows(value, allowHttp);
    }

    /**
     * Stops pending loads, detaches and destroys the WebView. Must be called exactly once when the
     * page is torn down; the parent screen lifecycle is responsible for ordering, and no other
     * method may touch the WebView afterwards.
     */
    @Override public void dispose() {
        browser.stopLoading();
        removeView(browser);
        browser.destroy();
    }
}
