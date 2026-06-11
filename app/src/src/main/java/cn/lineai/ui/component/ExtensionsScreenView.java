package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class ExtensionsScreenView extends LinearLayout {
    public interface Listener {
        void onBack();

        void onOpen(String id);
    }

    private final Listener listener;

    public ExtensionsScreenView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, "扩展", listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        addCard(content, "agent", "Agent 扩展", "添加带触发条件和工具权限的自定义 Agent，可单独启停。", "可添加", IconButtonView.BRAIN);
        addCard(content, "mcp", "MCP 扩展", "添加 HTTP/S MCP 服务，查询 tools 列表，可单独启停。", "HTTP/S", IconButtonView.MCP);
        addCard(content, "skills", "Skills 扩展", "选择 ZIP 安装技能包；SSH 模式会推送到远端 ~/.linecode。", "ZIP", IconButtonView.ARCHIVE);
        addCard(content, "linecode", "LineCode 扩展", "导入 .lip 原生扩展包，可注册页面、菜单和 Hook。", "LIP", IconButtonView.PACKAGE);
    }

    private void addCard(LinearLayout content, String id, String title, String desc, String badge, int iconType) {
        Context context = content.getContext();
        LinearLayout card = new LinearLayout(context);
        card.setOrientation(HORIZONTAL);
        card.setGravity(Gravity.CENTER_VERTICAL);
        card.setClickable(true);
        card.setOnClickListener(v -> listener.onOpen(id));
        card.setBackground(LineTheme.roundedStroke(context, LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER));
        LineTheme.padding(card, LineTheme.LG, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        LinearLayout.LayoutParams cardParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        cardParams.bottomMargin = LineTheme.dp(context, LineTheme.SM);
        content.addView(card, cardParams);

        FrameLayout iconWrap = new FrameLayout(context);
        iconWrap.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 12));
        IconButtonView icon = new IconButtonView(context, iconType);
        icon.setIconColor(LineTheme.ACCENT);
        icon.setIconSizeDp(44, 22);
        icon.setClickable(false);
        iconWrap.addView(icon, new FrameLayout.LayoutParams(LineTheme.dp(context, 44), LineTheme.dp(context, 44), Gravity.CENTER));
        card.addView(iconWrap, new LayoutParams(LineTheme.dp(context, 44), LineTheme.dp(context, 44)));

        LinearLayout text = new LinearLayout(context);
        text.setOrientation(VERTICAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(context, LineTheme.MD);
        textParams.rightMargin = LineTheme.dp(context, LineTheme.MD);
        card.addView(text, textParams);

        LinearLayout titleRow = new LinearLayout(context);
        titleRow.setOrientation(HORIZONTAL);
        titleRow.setGravity(Gravity.CENTER_VERTICAL);
        text.addView(titleRow, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        titleRow.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        TextView badgeView = LineTheme.text(context, badge, LineTheme.FONT_XS, LineTheme.ACCENT, Typeface.BOLD);
        badgeView.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 999));
        LineTheme.padding(badgeView, LineTheme.SM, 3, LineTheme.SM, 3);
        LinearLayout.LayoutParams badgeParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        badgeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        titleRow.addView(badgeView, badgeParams);

        TextView descView = LineTheme.text(context, desc, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        descView.setLineSpacing(LineTheme.dp(context, 3), 1f);
        LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        descParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        text.addView(descView, descParams);

        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevron.setIconColor(LineTheme.TEXT_TERTIARY);
        chevron.setIconSizeDp(20, 17);
        chevron.setClickable(false);
        card.addView(chevron, new LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));
    }
}
