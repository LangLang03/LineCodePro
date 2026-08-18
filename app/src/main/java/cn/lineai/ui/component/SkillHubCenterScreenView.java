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
        super(context, context.getString(cn.lineai.R.string.skillhub_center_title), listener::onBack, null);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        TextView notice = LineTheme.text(context,
                context.getString(cn.lineai.R.string.skillhub_center_notice),
                LineTheme.FONT_SM, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        notice.setBackground(LineTheme.roundedStroke(
                context, LineTheme.SURFACE_ELEVATED, 12, LineTheme.BORDER_LIGHT));
        LineTheme.padding(notice, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        content.addView(notice);

        section(content, context.getString(cn.lineai.R.string.skillhub_section_account_social));
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_profile),
                context.getString(cn.lineai.R.string.skillhub_entry_profile_desc), "account", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_my_stars),
                context.getString(cn.lineai.R.string.skillhub_entry_my_stars_desc), "stars", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_following),
                context.getString(cn.lineai.R.string.skillhub_entry_following_desc), "following", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_notifications),
                context.getString(cn.lineai.R.string.skillhub_entry_notifications_desc), "notifications", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_account_settings),
                context.getString(cn.lineai.R.string.skillhub_entry_account_settings_desc), "settings", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_verify_identity),
                context.getString(cn.lineai.R.string.skillhub_entry_verify_identity_desc), "verify", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_api_token),
                context.getString(cn.lineai.R.string.skillhub_entry_api_token_desc), "tokens", listener);

        section(content, context.getString(cn.lineai.R.string.skillhub_section_creator));
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_creator_center),
                context.getString(cn.lineai.R.string.skillhub_entry_creator_center_desc), "creator", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_publish_workbench),
                context.getString(cn.lineai.R.string.skillhub_entry_publish_workbench_desc), "publish", listener);

        section(content, context.getString(cn.lineai.R.string.skillhub_section_discover));
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_skillset),
                context.getString(cn.lineai.R.string.skillhub_entry_skillset_desc), "skillsets", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_mcp_server),
                context.getString(cn.lineai.R.string.skillhub_entry_mcp_server_desc), "mcp", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_skill_hunt),
                context.getString(cn.lineai.R.string.skillhub_entry_skill_hunt_desc), "skill-hunt", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_contest),
                context.getString(cn.lineai.R.string.skillhub_entry_contest_desc), "contest", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_enterprise_square),
                context.getString(cn.lineai.R.string.skillhub_entry_enterprise_square_desc), "enterprises", listener);

        section(content, context.getString(cn.lineai.R.string.skillhub_section_enterprise_platform));
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_enterprise_dashboard),
                context.getString(cn.lineai.R.string.skillhub_entry_enterprise_dashboard_desc), "enterprise-dashboard", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_enterprise_publish),
                context.getString(cn.lineai.R.string.skillhub_entry_enterprise_publish_desc), "enterprise-publish", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_merchant),
                context.getString(cn.lineai.R.string.skillhub_entry_merchant_desc), "merchant", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_admin),
                context.getString(cn.lineai.R.string.skillhub_entry_admin_desc), "admin", listener);
        entry(content, context.getString(cn.lineai.R.string.skillhub_entry_admin_reviews),
                context.getString(cn.lineai.R.string.skillhub_entry_admin_reviews_desc), "admin-reviews", listener);
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
