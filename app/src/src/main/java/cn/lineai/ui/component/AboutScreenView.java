package cn.lineai.ui.component;

import android.content.Context;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.graphics.Typeface;
import android.os.Build;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;

public final class AboutScreenView extends ScreenScaffoldView {
    public interface Listener {
        void onBack();

        void onOpenLicenses();
    }

    public AboutScreenView(Context context, Listener listener) {
        super(context, "关于 LineCode", listener::onBack, null);
        VersionInfo versionInfo = readVersionInfo(context);
        LinearLayout content = getContent();
        LineTheme.padding(content, LineTheme.LG, LineTheme.LG, LineTheme.LG, 100);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(VERTICAL);
        header.setGravity(Gravity.CENTER_HORIZONTAL);
        LineTheme.padding(header, 0, LineTheme.XL, 0, LineTheme.XL);

        FrameLayout iconContainer = new FrameLayout(context);
        iconContainer.setBackground(LineTheme.rounded(context, LineTheme.ACCENT_MUTED, 44));
        IconButtonView logo = new IconButtonView(context, IconButtonView.CODE);
        logo.setIconColor(LineTheme.ACCENT);
        logo.setIconSizeDp(88, 48);
        logo.setClickable(false);
        iconContainer.addView(logo, new FrameLayout.LayoutParams(LineTheme.dp(context, 88), LineTheme.dp(context, 88), Gravity.CENTER));
        header.addView(iconContainer, new LinearLayout.LayoutParams(LineTheme.dp(context, 88), LineTheme.dp(context, 88)));

        TextView name = LineTheme.text(context, versionInfo.appLabel, LineTheme.FONT_XL, LineTheme.TEXT, Typeface.BOLD);
        LinearLayout.LayoutParams nameParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        nameParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        header.addView(name, nameParams);
        TextView version = LineTheme.text(context, "APK " + versionInfo.versionName + " (" + versionInfo.versionCode + ")", LineTheme.FONT_MD, LineTheme.TEXT_SECONDARY, Typeface.NORMAL);
        LinearLayout.LayoutParams versionParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        versionParams.topMargin = LineTheme.dp(context, LineTheme.XS);
        header.addView(version, versionParams);
        content.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        addGroupTitle(content, "版本");
        addItem(content, IconButtonView.PACKAGE, "APK 版本", versionInfo.versionName + " (" + versionInfo.versionCode + ")", null);

        addGroupTitle(content, "开发者");
        addItem(content, IconButtonView.USER, "作者", "LangLang03", null);
        addItem(content, IconButtonView.MESSAGE_CIRCLE, "QQ", "3772548978", null);

        addGroupTitle(content, "法律信息");
        addItem(content, IconButtonView.FILE_TEXT, "开源许可列表", "", listener::onOpenLicenses);

        TextView footer = LineTheme.text(context, "Copyright 2025 LangLang. All rights reserved.", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        footer.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams footerParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        footerParams.topMargin = LineTheme.dp(context, LineTheme.XL);
        content.addView(footer, footerParams);
    }

    private void addGroupTitle(LinearLayout content, String title) {
        TextView text = LineTheme.text(context(), title, LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.topMargin = LineTheme.dp(context(), LineTheme.LG);
        params.bottomMargin = LineTheme.dp(context(), LineTheme.SM);
        params.leftMargin = LineTheme.dp(context(), LineTheme.XS);
        content.addView(text, params);
    }

    private void addItem(LinearLayout content, int icon, String label, String value, Runnable onClick) {
        ActionRowView row = new ActionRowView(context(), icon, label, value, false, onClick != null, onClick);
        row.setBackground(LineTheme.rounded(context(), LineTheme.SURFACE_ELEVATED, 12));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        params.bottomMargin = LineTheme.dp(context(), LineTheme.SM);
        content.addView(row, params);
    }

    private Context context() {
        return getContext();
    }

    @SuppressWarnings("deprecation")
    private static VersionInfo readVersionInfo(Context context) {
        String appLabel = "LineCode Pro";
        String versionName = "";
        long versionCode = 0L;
        try {
            PackageManager packageManager = context.getPackageManager();
            CharSequence label = packageManager.getApplicationLabel(context.getApplicationInfo());
            if (label != null && label.length() > 0) {
                appLabel = label.toString();
            }
            PackageInfo packageInfo = packageManager.getPackageInfo(context.getPackageName(), 0);
            if (packageInfo.versionName != null && packageInfo.versionName.length() > 0) {
                versionName = packageInfo.versionName;
            }
            versionCode = Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
                    ? packageInfo.getLongVersionCode()
                    : packageInfo.versionCode;
        } catch (PackageManager.NameNotFoundException ignored) {
        }
        if (versionName.length() == 0) {
            versionName = "unknown";
        }
        return new VersionInfo(appLabel, versionName, versionCode);
    }

    private static final class VersionInfo {
        final String appLabel;
        final String versionName;
        final long versionCode;

        VersionInfo(String appLabel, String versionName, long versionCode) {
            this.appLabel = appLabel;
            this.versionName = versionName;
            this.versionCode = versionCode;
        }
    }
}
