package cn.lineai.ui.markdown;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class MarkdownTextBlockView extends TextView {
    public MarkdownTextBlockView(Context context, CharSequence text, int sizeSp, boolean heading) {
        this(context, text, sizeSp, heading, null);
    }

    public MarkdownTextBlockView(Context context, CharSequence text, int sizeSp, boolean heading, MarkdownLinkHandler linkHandler) {
        super(context);
        setTextColor(LineTheme.TEXT);
        setTextSize(sizeSp);
        setIncludeFontPadding(false);
        setLineSpacing(LineTheme.dp(context, heading ? 3 : 4), 1.0f);
        MarkdownLinks.apply(this, text, linkHandler);
        if (heading) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }
}
