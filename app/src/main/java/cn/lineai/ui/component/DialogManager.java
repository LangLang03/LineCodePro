package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.app.AlertDialog;
import android.content.Context;
import android.text.InputType;
import android.view.WindowManager;
import android.widget.EditText;
import cn.lineai.R;

/**
 * Centralised helper that builds and shows the {@link AlertDialog}s used by the chat workspace.
 *
 * <p>This class exists so the various view controllers do not have to know how to wire up a
 * themed confirm / message / input dialog themselves. Callers provide the title, message, and
 * callbacks; {@code DialogManager} handles text defaults, the soft-input focus, and the danger
 * label colour for destructive confirmations.</p>
 *
 * <p>Each method is null-tolerant: {@code null} titles/messages are coerced to empty strings,
 * and a {@code null} callback is treated as a no-op.</p>
 */
public final class DialogManager {

    /** Listener invoked with the user-confirmed input value. */
    public interface OnInputConfirmed {
        void onInputConfirmed(String value);
    }

    public DialogManager() {
    }

    /**
     * Show a confirmation dialog with a "Confirm" / "Cancel" button pair.
     *
     * @param context   Android context used to build the dialog.
     * @param title     dialog title; {@code null} renders as an empty title.
     * @param message   dialog message; {@code null} renders as an empty message.
     * @param onConfirm invoked on the positive button; may be {@code null}.
     * @param onCancel  invoked on the negative button or back press; may be {@code null}.
     */
    public void showConfirm(Context context, String title, String message,
                            Runnable onConfirm, Runnable onCancel) {
        showConfirm(context, title, message, null, false, onConfirm, onCancel);
    }

    /**
     * Show a confirmation dialog with a custom confirm label and optional danger styling.
     *
     * @param context      Android context used to build the dialog.
     * @param title        dialog title; {@code null} renders as an empty title.
     * @param message      dialog message; {@code null} renders as an empty message.
     * @param confirmLabel text for the positive button; {@code null}/empty falls back to the
     *                     localized "Confirm" string.
     * @param danger       when {@code true}, the positive button label is rendered in the danger
     *                     colour to signal destructive actions.
     * @param onConfirm    invoked on the positive button; may be {@code null}.
     * @param onCancel     invoked on the negative button or back press; may be {@code null}.
     */
    public void showConfirm(Context context, String title, String message,
                            String confirmLabel, boolean danger,
                            Runnable onConfirm, Runnable onCancel) {
        if (context == null) {
            return;
        }
        AlertDialog dialog = new LineAlertDialog.Builder(context)
                .setTitle(title == null ? "" : title)
                .setMessage(message == null ? "" : message)
                .setNegativeButton(context.getString(R.string.common_cancel), (d, which) -> {
                    if (onCancel != null) {
                        onCancel.run();
                    }
                })
                .setPositiveButton(confirmLabel == null || confirmLabel.length() == 0
                                ? context.getString(R.string.common_confirm)
                                : confirmLabel,
                        (d, which) -> {
                            if (onConfirm != null) {
                                onConfirm.run();
                            }
                        })
                .create();
        if (danger) {
            dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE)
                    .setTextColor(LineTheme.DANGER));
        }
        dialog.show();
    }

    /**
     * Show a read-only information dialog with a single "Confirm" dismiss button.
     *
     * @param context Android context used to build the dialog.
     * @param title   dialog title; {@code null} renders as an empty title.
     * @param message dialog message; {@code null} renders as an empty message.
     */
    public void showMessage(Context context, String title, String message) {
        if (context == null) {
            return;
        }
        new LineAlertDialog.Builder(context)
                .setTitle(title == null ? "" : title)
                .setMessage(message == null ? "" : message)
                .setPositiveButton(context.getString(R.string.common_confirm), null)
                .show();
    }

    /**
     * Show a single-line text input dialog and report the entered value back through
     * {@code onInputConfirmed}. The input field is auto-focused and the soft keyboard is
     * forced visible.
     *
     * @param context       Android context used to build the dialog.
     * @param title         dialog title; {@code null} renders as an empty title.
     * @param message       dialog message shown above the input; {@code null} or empty
     *                      suppresses the message line. May be {@code null}.
     * @param hint          hint text for the EditText; {@code null}/empty leaves the
     *                      default hint.
     * @param prefill       initial value of the input; {@code null} becomes an empty string.
     * @param onInputConfirmed invoked with the typed text on confirm; may be {@code null}.
     */
    public void showInput(Context context, String title, String message, String hint,
                          String prefill, OnInputConfirmed onInputConfirmed) {
        if (context == null) {
            return;
        }
        final EditText input = new EditText(context);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        input.setText(prefill == null ? "" : prefill);
        input.setSelectAllOnFocus(true);
        if (hint != null && hint.length() > 0) {
            input.setHint(hint);
        }
        int horizontalPadding = LineTheme.dp(context, LineTheme.LG);
        input.setPadding(horizontalPadding,
                LineTheme.dp(context, LineTheme.SM),
                horizontalPadding,
                LineTheme.dp(context, LineTheme.SM));

        AlertDialog.Builder builder = new LineAlertDialog.Builder(context)
                .setTitle(title == null ? "" : title)
                .setNegativeButton(context.getString(R.string.common_cancel), null)
                .setPositiveButton(context.getString(R.string.common_confirm), (d, which) -> {
                    if (onInputConfirmed != null) {
                        onInputConfirmed.onInputConfirmed(input.getText().toString());
                    }
                });
        if (message != null && message.length() > 0) {
            builder.setMessage(message);
        }
        builder.setView(input);

        AlertDialog dialog = builder.create();
        dialog.setOnShowListener(d -> {
            input.requestFocus();
            if (dialog.getWindow() != null) {
                dialog.getWindow().setSoftInputMode(
                        WindowManager.LayoutParams.SOFT_INPUT_STATE_ALWAYS_VISIBLE);
            }
        });
        dialog.show();
    }
}
