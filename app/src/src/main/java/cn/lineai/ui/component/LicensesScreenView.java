package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class LicensesScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public LicensesScreenView(Context context, Listener listener) {
        super(context, "开源许可", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.MD, LineTheme.MD, LineTheme.MD, 100);

        addLicense(content, "commonmark-java core", "org.commonmark:commonmark:0.28.0 · BSD-2-Clause",
                "Markdown 块级与行内语法解析。POM 声明许可为 BSD-2-Clause。");
        addLicense(content, "commonmark-java GFM tables", "org.commonmark:commonmark-ext-gfm-tables:0.28.0 · BSD-2-Clause",
                "GitHub Flavored Markdown 表格扩展，继承 commonmark-java 的 BSD-2-Clause 许可。");
        addLicense(content, "JSch", "com.github.mwiede:jsch:2.28.2 · Revised BSD / ISC",
                "SSH2 Java 实现。POM 声明 JSch 与 JZlib 为 Revised BSD，jBCrypt 部分为 ISC。");
        addLicense(content, "Lucide Icons", "lucide-react-native:1.14.0 · ISC / MIT",
                "应用内 vector drawable 图标由 LineAI node_modules 中的 Lucide SVG 转换而来；Lucide 为 ISC，部分源自 Feather 的图标按 MIT 许可。");
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
