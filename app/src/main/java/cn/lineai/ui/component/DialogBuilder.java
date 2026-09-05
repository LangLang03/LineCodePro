package cn.lineai.ui.component;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

/**
 * Centralised helper that creates and shows the custom-themed {@link Dialog}s used throughout
 * the chat workspace.
 *
 * <p>Every dialog created here requests {@link Window#FEATURE_NO_TITLE} and applies a transparent
 * window background. Two layout styles are supported:</p>
 *
 * <ul>
 *   <li><b>Inset centre dialog</b> &ndash; uses {@link DialogDimensions#insetDialogWidth} for the
 *       width and positions the dialog in the screen centre. Suitable for action sheets, detail
 *       editors, and confirmation panels.</li>
 *   <li><b>Bottom-sheet dialog</b> &ndash; full-width, pinned to the bottom of the screen with
 *       {@link Gravity#BOTTOM}. Suitable for picker lists and bottom action sheets.</li>
 * </ul>
 */
public final class DialogBuilder {

    private static View boundedContent(View content) {
        Context context = content.getContext();
        int maxDp = Math.round(context.getResources().getDisplayMetrics().heightPixels / context.getResources().getDisplayMetrics().density * .82f);
        cn.lineai.ui.theme.BoundedScrollView scroll = new cn.lineai.ui.theme.BoundedScrollView(context, maxDp);
        scroll.setVerticalScrollBarEnabled(false);
        scroll.setBackground(cn.lineai.ui.theme.LineTheme.rounded(context, cn.lineai.ui.theme.LineTheme.BG, 24));
        scroll.setClipToOutline(true);
        scroll.addView(content, new android.widget.ScrollView.LayoutParams(-1, -2));
        return scroll;
    }

    private DialogBuilder() {
    }

    /**
     * Create a basic {@link Dialog} with {@link Window#FEATURE_NO_TITLE} already requested.
     *
     * <p>Use this when you need to configure the dialog further (e.g.
     * {@link Dialog#setCanceledOnTouchOutside}) before calling one of the {@code show*} overloads
     * that accept an existing dialog.</p>
     */
    public static Dialog create(Context context) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(Window.FEATURE_NO_TITLE);
        return dialog;
    }

    /**
     * Configure and show an inset centre dialog.
     *
     * <p>Sets the content view, applies a transparent background, and sizes the dialog to
     * {@link DialogDimensions#insetDialogWidth} &times; {@code WRAP_CONTENT}.</p>
     *
     * @param dialog      a dialog previously created via {@link #create} (or equivalent).
     * @param contentView the view to display inside the dialog.
     * @return the same dialog instance, now visible.
     */
    public static Dialog showInset(Dialog dialog, View contentView) {
        dialog.setContentView(boundedContent(contentView));
        dialog.setOnShowListener(d -> {
            Window window = dialog.getWindow();
            if (window != null) {
                window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
                window.setLayout(DialogDimensions.insetDialogWidth(dialog.getContext()),
                        ViewGroup.LayoutParams.WRAP_CONTENT);
            }
        });
        dialog.show();
        return dialog;
    }

    /**
     * Create and show an inset centre dialog in one step.
     *
     * @param context     Android context used to build the dialog.
     * @param contentView the view to display inside the dialog.
     * @return the newly created and visible dialog.
     */
    public static Dialog showInset(Context context, View contentView) {
        return showInset(create(context), contentView);
    }

    /**
     * Configure and show a bottom-sheet-style dialog.
     *
     * <p>Sets the content view, enables {@link Dialog#setCanceledOnTouchOutside}, applies a
     * transparent background, and sizes the dialog to {@code MATCH_PARENT} &times;
     * {@code WRAP_CONTENT} with {@link Gravity#BOTTOM}.</p>
     *
     * @param dialog      a dialog previously created via {@link #create} (or equivalent).
     * @param contentView the view to display inside the dialog.
     * @return the same dialog instance, now visible.
     */
    public static Dialog showBottomSheet(Dialog dialog, View contentView) {
        dialog.setCanceledOnTouchOutside(true);
        dialog.setContentView(boundedContent(contentView));
        dialog.show();
        Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
            window.setLayout(DialogDimensions.insetDialogWidth(dialog.getContext()),
                    ViewGroup.LayoutParams.WRAP_CONTENT);
            window.setGravity(Gravity.BOTTOM);
            android.view.WindowManager.LayoutParams attributes = window.getAttributes();
            attributes.y = cn.lineai.ui.theme.LineTheme.dp(dialog.getContext(), 16);
            window.setAttributes(attributes);
        }
        return dialog;
    }

    /**
     * Create and show a bottom-sheet-style dialog in one step.
     *
     * @param context     Android context used to build the dialog.
     * @param contentView the view to display inside the dialog.
     * @return the newly created and visible dialog.
     */
    public static Dialog showBottomSheet(Context context, View contentView) {
        return showBottomSheet(create(context), contentView);
    }
}
