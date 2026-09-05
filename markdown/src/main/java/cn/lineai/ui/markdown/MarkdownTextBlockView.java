package cn.lineai.ui.markdown;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.TextView;

public final class MarkdownTextBlockView extends TextView {
    public MarkdownTextBlockView(Context context, CharSequence text, int sizeSp, boolean heading) {
        this(context, text, sizeSp, heading, null);
    }

    public MarkdownTextBlockView(Context context, CharSequence text, int sizeSp, boolean heading, MarkdownLinkHandler linkHandler) {
        super(context);
        setTextColor(LineTheme.TEXT);
        setTextSize(sizeSp);
        setIncludeFontPadding(false);
        // Do NOT set textIsSelectable here - it steals touch events from sibling buttons
        // Text selection is triggered programmatically via the "Select" button
        setLineSpacing(LineTheme.dp(context, heading ? 5 : 7), 1.0f);
        MarkdownLinks.apply(this, text, linkHandler);
        if (heading) {
            setTypeface(Typeface.create("sans-serif-medium", Typeface.NORMAL));
        }
    }
}
