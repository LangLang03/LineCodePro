package cn.lineai.ui.component;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Color;
import android.widget.EditText;
import android.widget.ScrollView;
import cn.lineai.R;

public final class TextSelectionDialog {

    private TextSelectionDialog() {}

    public static void show(Context context, String content) {
        EditText editText = new EditText(context);
        editText.setText(content);
        editText.setTextSize(15);
        editText.setTextColor(cn.lineai.ui.theme.LineTheme.TEXT);
        editText.setBackgroundColor(cn.lineai.ui.theme.LineTheme.BG);
        cn.lineai.ui.theme.LineTheme.padding(editText, 24, 24, 24, 24);
        editText.setFocusable(true);
        editText.setFocusableInTouchMode(true);
        editText.setKeyListener(null);          // Read-only
        editText.setTextIsSelectable(true);     // Dialog has its own Window, doesn't steal touch
        editText.selectAll();

        ScrollView scrollView = new ScrollView(context);
        scrollView.addView(editText);

        new LineAlertDialog.Builder(context)
                .setTitle(R.string.dialog_select_text_title)
                .setView(scrollView)
                .setPositiveButton(R.string.common_close, null)
                .show();
    }
}
