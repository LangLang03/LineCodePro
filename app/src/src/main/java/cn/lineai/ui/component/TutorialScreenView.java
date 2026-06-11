package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class TutorialScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
    }

    public TutorialScreenView(Context context, Listener listener) {
        super(context, "使用教程", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.MD, LineTheme.LG, 100);

        LinearLayout selector = new LinearLayout(context);
        selector.setOrientation(LinearLayout.VERTICAL);
        selector.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 16, LineTheme.BORDER_LIGHT));
        addVariant(selector, "新手版", "零基础、一步一步照做", true);
        addVariant(selector, "专业版", "协议、执行环境、MCP 与安全细节", false);
        content.addView(selector, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView subtitle = LineTheme.text(context, "从项目、模型、权限和工具调用开始，快速完成 LineCode 的基础配置。", LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        subtitle.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams subtitleParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subtitleParams.topMargin = LineTheme.dp(context, LineTheme.LG);
        subtitleParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(subtitle, subtitleParams);

        addHeading(content, "1. 选择工作区");
        addParagraph(content, "点击顶部项目名，可以切换当前项目、打开已有目录或创建新项目。所有文件操作都会以当前项目作为上下文。");
        addHeading(content, "2. 添加模型");
        addParagraph(content, "进入设置 → 模型管理 → 添加模型，选择预置提供商或自定义 OpenAI 兼容地址。手机本地模型使用 GGUF 文件入口。");
        addHeading(content, "3. 配置工具");
        addParagraph(content, "在工具与执行里选择本地工作区或 SSH Shell，按需启用文件、Shell、HTTP 服务和网页搜索工具。");
        addHeading(content, "4. 使用扩展");
        addParagraph(content, "扩展页用于添加 Agent、MCP、Skills 和 .lip 包。每类扩展都有独立详情页和编辑页。");
        addCode(content, "Base URL: https://api.example.com/v1\nModel ID: qwen/qwen3-coder\nPermission: 每次询问");
    }

    private void addVariant(LinearLayout selector, String title, String desc, boolean active) {
        Context context = selector.getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 62));
        if (active) row.setBackgroundColor(LineTheme.ACCENT_MUTED);
        LineTheme.padding(row, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        LinearLayout text = new LinearLayout(context);
        text.setOrientation(LinearLayout.VERTICAL);
        row.addView(text, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        text.addView(LineTheme.text(context, title, LineTheme.FONT_MD, active ? LineTheme.ACCENT : LineTheme.TEXT, Typeface.BOLD));
        TextView sub = LineTheme.text(context, desc, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams subParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        subParams.topMargin = LineTheme.dp(context, 2);
        text.addView(sub, subParams);
        if (active) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }
        selector.addView(row, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addHeading(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView heading = LineTheme.text(context, text, 21, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.XL);
        params.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(heading, params);
    }

    private void addParagraph(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView paragraph = LineTheme.text(context, text, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        paragraph.setLineSpacing(LineTheme.dp(context, 4), 1f);
        content.addView(paragraph, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addCode(LinearLayout content, String text) {
        Context context = content.getContext();
        TextView code = LineTheme.text(context, text, LineTheme.FONT_SM, LineTheme.TEXT, Typeface.NORMAL);
        code.setTypeface(Typeface.MONOSPACE);
        code.setLineSpacing(LineTheme.dp(context, 4), 1f);
        code.setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 12, LineTheme.CODE_BORDER));
        LineTheme.padding(code, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context, LineTheme.MD);
        content.addView(code, params);
    }
}
