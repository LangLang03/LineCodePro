package cn.lineai.feature.chat;

import android.app.Dialog;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.view.ViewParent;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Sheets;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Composer plus-button menu: one row per drawer, opened instead of jumping straight into the
 * file picker. Rows are 52dp tall with a 16dp leading glyph and a 15sp label; picking a row
 * dismisses the sheet first and then opens the matching drawer.
 */
final class ComposerActionMenu {

    private static final int ROW_MIN_HEIGHT_DP = 52;
    private static final int ICON_DP = 16;
    private static final int ICON_GAP_DP = 8;

    private ComposerActionMenu() {
    }

    static void show(Context context, Tokens tokens, ChatComposer.Listener listener,
                     ChatCapabilities capabilities) {
        if (listener == null) {
            return;
        }
        ChatCapabilities caps = capabilities == null
                ? new ChatCapabilities(true, false, false) : capabilities;
        LinearLayout panel = column(context);
        LinearLayout grabberRow = row(context);
        grabberRow.setGravity(Gravity.CENTER_HORIZONTAL);
        Ui.padding(grabberRow, 0, 8, 0, 4);
        View grabber = new View(context);
        grabber.setBackground(Ui.rounded(context, tokens.textTertiary, 2));
        grabberRow.addView(grabber, new LinearLayout.LayoutParams(
                Ui.dp(context, 36), Ui.dp(context, 4)));
        panel.addView(grabberRow, new LinearLayout.LayoutParams(-1, -2));
        addRow(context, tokens, panel, Icons.BRAIN, R.string.composer_menu_thinking,
                caps.reasoning ? listener::onReasoning : () -> Toast.makeText(context,
                        R.string.model_reasoning_locked, Toast.LENGTH_SHORT).show());
        addRow(context, tokens, panel, Icons.ZAP, R.string.composer_menu_mode,
                listener::onMode);
        addRow(context, tokens, panel, Icons.FOLDER_OPEN, R.string.composer_menu_files,
                listener::onAttachFiles);
        if (caps.vision) {
            addRow(context, tokens, panel, Icons.IMAGE, R.string.composer_menu_image,
                    listener::onAttachImage);
        }
        if (caps.video) {
            addRow(context, tokens, panel, Icons.VIDEO, R.string.composer_menu_video,
                    listener::onVideoPicker);
        }
        View spacer = new View(context);
        spacer.setLayoutParams(new LinearLayout.LayoutParams(1, Ui.dp(context, 8)));
        panel.addView(spacer);
        Sheets.showBottom(context, tokens, panel);
    }

    private static void addRow(Context context, Tokens tokens, LinearLayout panel, int icon,
                               int label, Runnable action) {
        LinearLayout row = row(context);
        row.setMinimumHeight(Ui.dp(context, ROW_MIN_HEIGHT_DP));
        Ui.padding(row, 16, 14, 16, 14);
        row.setClickable(true);
        row.setFocusable(true);
        ImageView glyph = new ImageView(context);
        glyph.setImageResource(Icons.drawable(icon));
        glyph.setColorFilter(tokens.textSecondary);
        glyph.setScaleType(ImageView.ScaleType.FIT_CENTER);
        row.addView(glyph, new LinearLayout.LayoutParams(
                Ui.dp(context, ICON_DP), Ui.dp(context, ICON_DP)));
        TextView text = Ui.text(context, context.getString(label), 15, tokens.text,
                Typeface.NORMAL);
        LinearLayout.LayoutParams textParams = new LinearLayout.LayoutParams(0, -2, 1f);
        textParams.leftMargin = Ui.dp(context, ICON_GAP_DP);
        row.addView(text, textParams);
        row.setOnClickListener(v -> {
            dismiss(row);
            action.run();
        });
        panel.addView(row, new LinearLayout.LayoutParams(-1, -2));
    }

    private static LinearLayout column(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        return layout;
    }

    private static LinearLayout row(Context context) {
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.HORIZONTAL);
        layout.setGravity(Gravity.CENTER_VERTICAL);
        return layout;
    }

    private static void dismiss(View view) {
        ViewParent parent = view.getParent();
        while (parent != null && !(parent instanceof Dialog)) {
            parent = parent.getParent();
        }
        if (parent instanceof Dialog) {
            ((Dialog) parent).dismiss();
        }
    }
}
