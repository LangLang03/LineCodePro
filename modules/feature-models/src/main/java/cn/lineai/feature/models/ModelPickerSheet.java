package cn.lineai.feature.models;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Bottom sheet listing the model ids returned by the provider catalog.
 *
 * <p>Package-private utility built once per invocation; rows report through {@link Listener} and
 * the sheet dismisses itself before the callback so a slow listener cannot interact with a visible
 * sheet. A trailing "custom" row lets callers accept a free-form model id that the catalog did not
 * list.</p>
 */
final class ModelPickerSheet {

    /**
     * Consumer of the picker outcome.
     */
    interface Listener {
        /**
         * Reports the chosen model.
         *
         * @param modelId catalog id when {@code custom} is false, empty string when true
         * @param custom true when the trailing custom-entry row was chosen
         */
        void onModelSelected(String modelId, boolean custom);
    }

    /** Not instantiable; the sheet is created through the static {@link #show}. */
    private ModelPickerSheet() {
    }

    /**
     * Builds and shows the sheet.
     *
     * @param context hosting context
     * @param tokens palette snapshot used for the panel, rows and check glyph
     * @param modelIds catalog ids to list, rendered in order
     * @param selectedId currently selected catalog id, shown with a check glyph
     * @param listener outcome consumer; may be null, in which case the sheet only dismisses
     */
    static void show(Context context, Tokens tokens, List<String> modelIds, String selectedId,
                     Listener listener) {
        Dialog dialog = new Dialog(context);
        dialog.requestWindowFeature(android.view.Window.FEATURE_NO_TITLE);
        LinearLayout panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(Ui.roundedTop(context, tokens.surfaceElevated, 16));
        View handle = new View(context);
        handle.setBackground(Ui.rounded(context, tokens.textTertiary, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = Ui.dp(context, 8);
        handleParams.bottomMargin = Ui.dp(context, 4);
        panel.addView(handle, handleParams);
        TextView title = Ui.bold(context, context.getString(R.string.screen_model_add_picker_title),
                Tokens.FONT_LG, tokens.text);
        Ui.padding(title, 16, 0, 16, 12);
        panel.addView(title, new LinearLayout.LayoutParams(-1, -2));
        View divider = new View(context);
        divider.setBackgroundColor(tokens.borderLight);
        panel.addView(divider, new LinearLayout.LayoutParams(-1, 1));
        ScrollView scroll = new ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout list = new LinearLayout(context);
        list.setOrientation(LinearLayout.VERTICAL);
        scroll.addView(list, new ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));
        for (String id : modelIds) row(context, tokens, list, dialog, id, id.equals(selectedId), false, listener);
        row(context, tokens, list, dialog, context.getString(R.string.screen_model_add_custom_id_picker),
                false, true, listener);
        panel.setPadding(0, 0, 0, Ui.dp(context, 12));
        dialog.show();
        android.view.Window window = dialog.getWindow();
        if (window != null) {
            window.setBackgroundDrawable(new android.graphics.drawable.ColorDrawable(
                    android.graphics.Color.TRANSPARENT));
            window.setLayout(Sheets.dialogWidth(context), -2);
            window.setGravity(Gravity.BOTTOM);
        }
        dialog.setContentView(panel);
    }

    /**
     * Builds one selectable row. Custom rows render in accent color and never show the check glyph,
     * keeping the catalog selection visually distinct from the free-form entry.
     */
    private static void row(Context context, Tokens tokens, LinearLayout list, Dialog dialog,
                            String label, boolean selected, boolean custom, Listener listener) {
        LinearLayout row = new LinearLayout(context);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        Ui.padding(row, 16, 14, 16, 14);
        TextView text = Ui.text(context, label, Tokens.FONT_MD,
                custom ? tokens.accent : tokens.text, Typeface.NORMAL);
        text.setSingleLine(true);
        text.setEllipsize(TextUtils.TruncateAt.END);
        row.addView(text, new LinearLayout.LayoutParams(0, -2, 1f));
        if (!custom && selected) {
            IconButton check = new IconButton(context, Icons.CHECK);
            check.setIconColor(tokens.accent);
            check.setIconSizeDp(18, 16);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(Ui.dp(context, 18), Ui.dp(context, 18)));
        }
        row.setOnClickListener(v -> {
            dialog.dismiss();
            if (listener != null) listener.onModelSelected(custom ? "" : label, custom);
        });
        list.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }
}
