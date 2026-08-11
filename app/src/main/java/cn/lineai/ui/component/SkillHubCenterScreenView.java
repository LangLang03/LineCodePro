package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

public final class SkillHubCenterScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();
        void onOpen(String destination);
    }

    public SkillHubCenterScreenView(Context context, Listener listener) {
        super(context, "SkillHub 功能中心", listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        TextView notice = LineTheme.text(context,
                "浏览、安装、评论和收藏使用 LineCode 原生界面；账号、创作者、企业及平台功能在受限 SkillHub 官方页面中完成。",
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        notice.setBackground(LineTheme.roundedStroke(
                context, LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(notice, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        content.addView(notice);

        section(content, "账号与社交");
        entry(content, "个人中心", "资料、已发布内容和账号概览", "account", listener);
        entry(content, "我的收藏", "查看全部已收藏 Skill", "stars", listener);
        entry(content, "我的关注", "查看关注的创作者与动态", "following", listener);
        entry(content, "通知中心", "评论、审核和平台通知", "notifications", listener);
        entry(content, "账号设置", "头像、绑定、通知与账号安全", "settings", listener);
        entry(content, "实名认证", "发布和企业功能所需的官方认证", "verify", listener);
        entry(content, "API Token", "创建、查看和撤销平台 Token", "tokens", listener);

        section(content, "创作者");
        entry(content, "创作者中心", "管理发布、审核状态、版本和申诉", "creator", listener);
        entry(content, "官方发布工作台", "图标、GitHub 导入、认领和版本发布", "publish", listener);

        section(content, "发现");
        entry(content, "SkillSet", "浏览 Skill 组合与主题包", "skillsets", listener);
        entry(content, "MCP Server", "搜索和查看 MCP Server", "mcp", listener);
        entry(content, "Skill Hunt", "榜单、投票与称号", "skill-hunt", listener);
        entry(content, "赛事", "赛事作品、排名和获奖信息", "contest", listener);
        entry(content, "企业广场", "企业主页、热门 Skill 和关注", "enterprises", listener);

        section(content, "企业与平台");
        entry(content, "企业工作台", "团队 Skill、成员、审核和密钥", "enterprise-dashboard", listener);
        entry(content, "企业发布", "发布和维护企业 Skill", "enterprise-publish", listener);
        entry(content, "商户管理", "协议、商户状态和开发者密钥", "merchant", listener);
        entry(content, "管理后台", "仅对 SkillHub 管理员账号开放", "admin", listener);
        entry(content, "Skill 审核", "仅对有审核权限的账号开放", "admin-reviews", listener);
    }

    private void section(LinearLayout content, String title) {
        TextView value = LineTheme.textMedium(getContext(), title,
                LineTheme.FONT_MD, LineTheme.TEXT);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.LG);
        params.bottomMargin = LineTheme.dp(getContext(), LineTheme.XS);
        content.addView(value, params);
    }

    private void entry(
            LinearLayout content, String title, String description,
            String destination, Listener listener) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setFocusable(true);
        row.setBackground(LineTheme.rounded(getContext(), LineTheme.SURFACE_ELEVATED, 11));
        LineTheme.padding(row, LineTheme.MD, LineTheme.SM, LineTheme.SM, LineTheme.SM);

        LinearLayout labels = new LinearLayout(getContext());
        labels.setOrientation(VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));
        labels.addView(LineTheme.textMedium(getContext(), title, LineTheme.FONT_SM, LineTheme.TEXT));
        TextView detail = LineTheme.text(getContext(), description,
                LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams detailParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        detailParams.topMargin = LineTheme.dp(getContext(), 2);
        labels.addView(detail, detailParams);

        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.CHEVRON_RIGHT);
        icon.setIconColor(LineTheme.TEXT_TERTIARY);
        icon.setIconSizeDp(26, 16);
        icon.setClickable(false);
        icon.setFocusable(false);
        row.addView(icon, new LinearLayout.LayoutParams(
                LineTheme.dp(getContext(), 26), LineTheme.dp(getContext(), 26)));
        row.setOnClickListener(v -> listener.onOpen(destination));

        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(getContext(), LineTheme.SM);
        content.addView(row, params);
    }
}
