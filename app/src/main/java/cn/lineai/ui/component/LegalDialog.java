package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;

public final class LegalDialog {

    private LegalDialog() {
    }

    public static void show(Context context, String title, String message,
                            String agreeLabel, String disagreeLabel,
                            Runnable onAgree, Runnable onDisagree) {
        if (context == null) {
            return;
        }

        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(false);
        dialog.setCancelable(false);

        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.rounded(context, LineTheme.BG, 24));
        LineTheme.padding(panel, 24, 24, 24, 24);

        TextView titleView = LineTheme.text(context, title == null ? "" : title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        panel.addView(titleView, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        LinearLayout.LayoutParams dividerParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 1);
        dividerParams.topMargin = LineTheme.dp(context, LineTheme.MD);
        dividerParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        panel.addView(divider, dividerParams);

        ScrollView scrollView = new ScrollView(context);
        LinearLayout.LayoutParams scrollParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f);
        scrollParams.bottomMargin = LineTheme.dp(context, LineTheme.MD);
        scrollView.setLayoutParams(scrollParams);

        TextView textView = LineTheme.text(context, message == null ? "" : message, LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        textView.setLineSpacing(LineTheme.dp(context, 4), 1f);
        scrollView.addView(textView, new ScrollView.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        panel.addView(scrollView);

        LinearLayout actions = new AdaptiveActionsView(context);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.END);

        TextView disagreeButton = createActionButton(context, disagreeLabel == null || disagreeLabel.length() == 0
                ? context.getString(R.string.common_cancel) : disagreeLabel, LineTheme.TEXT_SECONDARY);
        disagreeButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (onDisagree != null) {
                onDisagree.run();
            }
        });
        actions.addView(disagreeButton, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView agreeButton = createActionButton(context, agreeLabel == null || agreeLabel.length() == 0
                ? context.getString(R.string.user_agreement_agree) : agreeLabel, LineTheme.ACCENT);
        agreeButton.setOnClickListener(v -> {
            dialog.dismiss();
            if (onAgree != null) {
                onAgree.run();
            }
        });
        LinearLayout.LayoutParams agreeParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        agreeParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
        actions.addView(agreeButton, agreeParams);

        panel.addView(actions, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        }
    }

    private static TextView createActionButton(Context context, String label, int color) {
        TextView button = LineTheme.textMedium(context, label, LineTheme.FONT_MD, color);
        button.setGravity(Gravity.CENTER);
        LineTheme.padding(button, LineTheme.LG, LineTheme.MD, LineTheme.LG, LineTheme.MD);
        return button;
    }
}
