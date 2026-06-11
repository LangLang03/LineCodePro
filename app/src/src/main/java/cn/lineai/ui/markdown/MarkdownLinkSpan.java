package cn.lineai.ui.markdown;

import android.text.TextPaint;
import android.text.style.ClickableSpan;
import android.view.View;
import cn.lineai.ui.theme.LineTheme;

final class MarkdownLinkSpan extends ClickableSpan {
    private final String url;
    private final MarkdownLinkHandler handler;

    MarkdownLinkSpan(String url, MarkdownLinkHandler handler) {
        this.url = url == null ? "" : url;
        this.handler = handler;
    }

    @Override
    public void onClick(View widget) {
        if (handler != null && url.length() > 0) {
            handler.onOpenUrl(url);
        }
    }

    @Override
    public void updateDrawState(TextPaint ds) {
        super.updateDrawState(ds);
        ds.setColor(LineTheme.ACCENT);
        ds.setUnderlineText(true);
    }
}
