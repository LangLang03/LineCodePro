package cn.lineai.ui.kit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;

import cn.lineai.ui.tokens.Tokens;

/** Bottom sheets with the same width, height cap and margin as the legacy dialogs. */
public final class Sheets {

    private Sheets() {
    }

    public static Dialog showBottom(Context context, Tokens tokens, View panel) {
        return showBottom(context, tokens, panel, 0);
    }

    public static Dialog showBottom(Context context, Tokens tokens, View panel, int maxHeightDp) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        BoundedScroll scroll = new BoundedScroll(context,
                maxHeightDp > 0 ? maxHeightDp : maxHeightDp(context));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(Ui.rounded(context, tokens.bg, 24));
        scroll.setClipToOutline(true);
        scroll.addView(panel, new ScrollView.LayoutParams(-1, -2));
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dialogWidth(context), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.y = Ui.dp(context, 16);
            window.setAttributes(attributes);
        }
        return dialog;
    }

    public static int dialogWidth(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 32);
        return Math.max(1, Math.min(Ui.dp(context, 560), width));
    }

    private static int maxHeightDp(Context context) {
        return Math.round(context.getResources().getDisplayMetrics().heightPixels
                / context.getResources().getDisplayMetrics().density * .82f);
    }

    private static final class BoundedScroll extends ScrollView {
        private final int maxHeightDp;

        private BoundedScroll(Context context, int maxHeightDp) {
            super(context);
            this.maxHeightDp = maxHeightDp;
        }

        @Override protected void onMeasure(int widthSpec, int heightSpec) {
            int cap = Ui.dp(getContext(), maxHeightDp);
            int mode = MeasureSpec.getMode(heightSpec);
            int size = MeasureSpec.getSize(heightSpec);
            super.onMeasure(widthSpec, mode == MeasureSpec.UNSPECIFIED
                    ? MeasureSpec.makeMeasureSpec(cap, MeasureSpec.AT_MOST)
                    : MeasureSpec.makeMeasureSpec(Math.min(size, cap), mode));
        }
    }
}
