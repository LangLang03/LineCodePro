package cn.lineai.ui.component;

import android.content.Context;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class SectionHeaderView extends TextView {
    public SectionHeaderView(Context context, String title) {
        super(context);
        setText(title == null ? "" : title.toUpperCase(java.util.Locale.ROOT));
        setTextColor(LineTheme.TEXT_TERTIARY);
        setTextSize(LineTheme.FONT_XS);
        setIncludeFontPadding(false);
        setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        setLetterSpacing(0.05f);
        LineTheme.padding(this, LineTheme.LG, 0, LineTheme.LG, 0);
    }
}
