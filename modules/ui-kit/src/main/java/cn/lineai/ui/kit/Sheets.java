package cn.lineai.ui.kit;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ScrollView;

import cn.lineai.ui.tokens.Tokens;

/** Bottom sheets with the same width, height cap and margin as the legacy dialogs. */
public final class Sheets {

    /**
     * Tag key carrying the owning dialog on its decor view.
     *
     * <p>{@link Dialog} is not a {@link ViewParent}, so a row inside a sheet cannot walk up the
     * view tree to find its dialog. Tagging the decor keeps the lookup local and makes every
     * in-sheet action able to dismiss the sheet it came from.</p>
     */
    private static final int DIALOG_TAG = 0x4C534454;

    private Sheets() {
    }

    /** Returns the bottom-sheet dialog that owns {@code view}, or null when it is not in one. */
    public static Dialog dialogOf(View view) {
        if (view == null) {
            return null;
        }
        ViewParent parent = view.getParent();
        while (parent != null) {
            if (parent instanceof View) {
                Object tag = ((View) parent).getTag(DIALOG_TAG);
                if (tag instanceof Dialog) {
                    return (Dialog) tag;
                }
            }
            parent = parent.getParent();
        }
        return null;
    }

    /** Dismisses the sheet that owns {@code view}; a no-op outside a sheet. */
    public static void dismiss(View view) {
        Dialog dialog = dialogOf(view);
        if (dialog != null && dialog.isShowing()) {
            dialog.dismiss();
        }
    }

    public static Dialog showBottom(Context context, Tokens tokens, View panel) {
        return showBottom(context, tokens, panel, 0);
    }

    public static Dialog showBottom(Context context, Tokens tokens, View panel, int maxHeightDp) {
        return showBottom(context, tokens, panel, maxHeightDp, 0);
    }

    /**
     * Shows a bottom sheet with an optional minimum panel height.
     *
     * <p>The picker drawers keep the legacy 240dp floor so a short list still reads as a drawer
     * instead of a thin strip; the panel gets an explicit height because a ScrollView measures a
     * wrap-content child without honouring its minimum height.</p>
     */
    public static Dialog showBottom(Context context, Tokens tokens, View panel, int maxHeightDp,
                                    int minHeightDp) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        dialog.setCanceledOnTouchOutside(true);
        BoundedScroll scroll = new BoundedScroll(context,
                maxHeightDp > 0 ? maxHeightDp : maxHeightDp(context));
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(Ui.rounded(context, tokens.bg, 24));
        scroll.setClipToOutline(true);
        scroll.addView(panel, new ScrollView.LayoutParams(-1,
                minHeightDp > 0 ? Ui.dp(context, minHeightDp) : -2));
        dialog.setContentView(scroll);
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(dialogWidth(context), WindowManager.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            window.setWindowAnimations(R.style.LineSheetAnimation);
            window.getDecorView().setTag(DIALOG_TAG, dialog);
            WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.y = Ui.dp(context, 16);
            attributes.dimAmount = scrim(tokens);
            window.setAttributes(attributes);
        }
        return dialog;
    }

    /** Legacy sheet scrim: the palette overlay, not the darker dialog barrier. */
    public static float scrim(Tokens tokens) {
        int alpha = Color.alpha(tokens.overlay);
        if (alpha <= 0 || alpha >= 250) {
            return 0.26f;
        }
        return alpha / 255f;
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
