package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;

public final class LicensesScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public LicensesScreenView(Context context, Listener listener) {
        super(context, context.getString(R.string.screen_licenses_title), listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.MD, LineTheme.MD, LineTheme.MD, 100);

        addLicense(content, context.getString(R.string.screen_licenses_commonmark_core), "org.commonmark:commonmark:0.28.0 · BSD-2-Clause",
                context.getString(R.string.screen_licenses_commonmark_core_desc));
        addLicense(content, context.getString(R.string.screen_licenses_commonmark_gfm), "org.commonmark:commonmark-ext-gfm-tables:0.28.0 · BSD-2-Clause",
                context.getString(R.string.screen_licenses_commonmark_gfm_desc));
        addLicense(content, context.getString(R.string.screen_licenses_jsch), "com.github.mwiede:jsch:2.28.2 · Revised BSD / ISC",
                context.getString(R.string.screen_licenses_jsch_desc));
        addLicense(content, context.getString(R.string.screen_licenses_lucide), "lucide-react-native:1.14.0 · ISC / MIT",
                context.getString(R.string.screen_licenses_lucide_desc));
    }

    private void addLicense(LinearLayout content, String name, String meta, String text) {
        Context context = content.getContext();
        LinearLayout item = new LinearLayout(context);
        item.setOrientation(LinearLayout.VERTICAL);
        item.setBackground(LineTheme.rounded(context, LineTheme.SURFACE_ELEVATED, 12));
        LineTheme.padding(item, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        item.addView(LineTheme.text(context, name, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        item.addView(LineTheme.text(context, meta, LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        TextView desc = LineTheme.text(context, text, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        desc.setLineSpacing(LineTheme.dp(context, 3), 1f);
        item.addView(desc, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(item, params);
    }
}
