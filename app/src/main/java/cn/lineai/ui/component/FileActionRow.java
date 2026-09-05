package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.SheetOption;

/**
 * Builds the tappable row used inside the "file action" dialog shown by
 * {@code MainChatView.showFileActionDialog(...)}.
 *
 * <p>Each row renders a label and (optionally) a secondary description, and reports the
 * selected option's id back through {@link OptionClickListener} while dismissing the host
 * dialog. The label is rendered in the danger colour when the option id starts with
 * {@code file:delete:}.</p>
 */
public final class FileActionRow {

    /** Listener notified when the user taps a row. */
    public interface OptionClickListener {
        void onOptionSelected(String id);
    }

    private FileActionRow() {
    }

    /**
     * Build a single file-action row view. Clicking the row dismisses {@code dialog} and
     * forwards {@code option.getId()} to {@code listener}.
     *
     * @param context Android context used to inflate views.
     * @param dialog  host dialog that should be dismissed when a row is tapped.
     * @param option  the option whose label/description is rendered. May be {@code null}.
     * @param listener invoked with the option id; may be {@code null}.
     */
    public static View create(Context context, Dialog dialog, SheetOption option, OptionClickListener listener) {
        String id = option == null ? "" : option.getId();
        boolean danger = id.startsWith("file:delete:");

        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (dialog != null) {
                dialog.dismiss();
            }
            if (listener != null) {
                listener.onOptionSelected(id);
            }
        });
        LineTheme.padding(row, 0, 16, 0, 16);
        row.setMinimumHeight(LineTheme.dp(context, 52));
        row.setBackground(LineTheme.pressable(context));

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.WRAP_CONTENT, 1f));

        TextView label = LineTheme.text(context,
                option == null ? "" : option.getLabel(),
                LineTheme.FONT_MD,
                danger ? LineTheme.DANGER : LineTheme.TEXT,
                Typeface.NORMAL);
        labels.addView(label, new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        String description = option == null ? "" : option.getDescription();
        if (description != null && description.length() > 0) {
            TextView desc = LineTheme.text(context, description, LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            desc.setSingleLine(false);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 6);
            labels.addView(desc, descParams);
        }
        return row;
    }
}
