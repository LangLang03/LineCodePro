package cn.lineai.ui.kit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.Window;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.function.Consumer;

import cn.lineai.ui.tokens.Tokens;

/** Small themed confirmation dialogs (title, message, cancel / confirm). */
public final class Dialogs {

    private Dialogs() {
    }

    public static Dialog confirm(Context context, Tokens tokens, CharSequence title,
                                 CharSequence message, CharSequence cancel, CharSequence confirm,
                                 Consumer<Boolean> result) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.rounded(context, tokens.surfaceElevated, 16));
        Ui.padding(panel, 20, 20, 20, 12);
        TextView heading = Ui.bold(context, title, 18, tokens.text);
        panel.addView(heading, new LinearLayout.LayoutParams(-1, -2));
        TextView body = Ui.text(context, message, 14, tokens.textSecondary, Typeface.NORMAL);
        body.setLineSpacing(Ui.dp(context, 3), 1f);
        LinearLayout.LayoutParams bodyParams = new LinearLayout.LayoutParams(-1, -2);
        bodyParams.topMargin = Ui.dp(context, 12);
        panel.addView(body, bodyParams);
        LinearLayout actions = new LinearLayout(context);
        actions.setGravity(Gravity.END | Gravity.CENTER_VERTICAL);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout.LayoutParams actionsParams = new LinearLayout.LayoutParams(-1, -2);
        actionsParams.topMargin = Ui.dp(context, 18);
        panel.addView(actions, actionsParams);
        TextView negative = Ui.medium(context, cancel, 15, tokens.textSecondary);
        negative.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        negative.setOnClickListener(v -> {
            dialog.dismiss();
            result.accept(false);
        });
        actions.addView(negative, new LinearLayout.LayoutParams(-2, -2));
        TextView positive = Ui.medium(context, confirm, 15, tokens.accent);
        positive.setPadding(Ui.dp(context, 12), Ui.dp(context, 10), Ui.dp(context, 12), Ui.dp(context, 10));
        positive.setOnClickListener(v -> {
            dialog.dismiss();
            result.accept(true);
        });
        LinearLayout.LayoutParams positiveParams = new LinearLayout.LayoutParams(-2, -2);
        positiveParams.leftMargin = Ui.dp(context, 8);
        actions.addView(positive, positiveParams);
        dialog.setOnCancelListener(d -> result.accept(false));
        dialog.setContentView(panel);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout((int) (context.getResources().getDisplayMetrics().widthPixels * 0.86f), -2);
        }
        return dialog;
    }
}
