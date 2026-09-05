package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;

/**
 * Shared width math for the dialogs the chat workspace shows (file-action sheet, image
 * preview, extension editor, ...).
 *
 * <p>Every such dialog uses the same rules: leave a 16&nbsp;dp margin on each side, and
 * never collapse below 280&nbsp;dp wide even on narrow devices.</p>
 */
public final class DialogDimensions {

    private DialogDimensions() {
    }

    /**
     * Compute the dialog width in pixels for the given context.
     *
     * @return {@code max(280dp, screenWidth - 32dp)} in pixels.
     */
    public static int insetDialogWidth(Context context) {
        int width = context.getResources().getDisplayMetrics().widthPixels - LineTheme.dp(context, 32);
        return Math.max(1, Math.min(LineTheme.dp(context, 560), width));
    }
}
