package cn.lineai.ui.component;
import android.content.Context;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
public final class SectionHeaderView extends TextView {
    public SectionHeaderView(Context context, String title) {
        super(context);
        setText(title == null ? "" : title);
        setTextColor(LineTheme.TEXT_SECONDARY);
        setTextSize(14);
        setIncludeFontPadding(false);
        setTypeface(android.graphics.Typeface.create("sans-serif-medium", android.graphics.Typeface.NORMAL));
        LineTheme.padding(this, 28, 0, 28, 0);
        if (android.os.Build.VERSION.SDK_INT >= 28) setAccessibilityHeading(true);
    }
}
