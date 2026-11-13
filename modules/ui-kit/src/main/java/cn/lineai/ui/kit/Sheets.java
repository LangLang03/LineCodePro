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

    /**
     * Shows a bottom sheet with the default height cap and no minimum height.
     *
     * @param context hosting context
     * @param tokens palette snapshot used for the panel background and scrim
     * @param panel content view placed inside the bounded scroll container
     * @return the showing dialog, also reachable from children via {@link #dialogOf}
     */
    public static Dialog showBottom(Context context, Tokens tokens, View panel, int maxHeightDp) {
        return showBottom(context, tokens, panel, maxHeightDp, 0);
    }

    /**
     * Shows a bottom sheet with an optional minimum panel height.
     *
     * <p>The picker drawers keep the legacy 240dp floor so a short list still reads as a drawer
     * instead of a thin strip; the panel gets an explicit height because a ScrollView measures a
     * wrap-content child without honouring its minimum height.</p>
     *
     * @param context hosting context
     * @param tokens palette snapshot used for the panel background and scrim
     * @param panel content view placed inside the bounded scroll container
     * @param maxHeightDp height cap in dp; 0 falls back to 82 percent of screen height
     * @param minHeightDp floor for the panel height in dp; 0 lets the panel wrap its content
     * @return the showing dialog, also reachable from children via {@link #dialogOf}
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

    /**
     * Computes the standard sheet width: full screen width minus 16dp margins, capped at 560dp.
     *
     * @param context context supplying display metrics
     * @return width in pixels, always at least 1
     */
    public static int dialogWidth(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels - Ui.dp(context, 32);
        return Math.max(1, Math.min(Ui.dp(context, 560), width));
    }

    /** Default height cap: 82 percent of current screen height, expressed in dp. */
    private static int maxHeightDp(Context context) {
        return Math.round(context.getResources().getDisplayMetrics().heightPixels
                / context.getResources().getDisplayMetrics().density * .82f);
    }

    /**
     * Scroll container that caps sheet height so tall panels scroll instead of covering the screen.
     */
    private static final class BoundedScroll extends ScrollView {
        private final int maxHeightDp;

        /** Creates the container with the fixed dp cap applied in {@link #onMeasure}. */
        private BoundedScroll(Context context, int maxHeightDp) {
            super(context);
            this.maxHeightDp = maxHeightDp;
        }

        /** Clamps the measured height to the cap, supplying an AT_MOST spec when the parent left it
         * unspecified so wrap-content panels still respect the limit. */
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
