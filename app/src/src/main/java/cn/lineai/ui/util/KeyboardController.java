package cn.lineai.ui.util;

import android.content.Context;
import android.view.View;
import android.view.inputmethod.InputMethodManager;

public final class KeyboardController {
    private KeyboardController() {
    }

    public static void clearFocusAndHide(View root) {
        if (root == null) {
            return;
        }
        View focused = root.findFocus();
        if (focused == null) {
            return;
        }
        if (focused.isAttachedToWindow()) {
            Object service = focused.getContext().getSystemService(Context.INPUT_METHOD_SERVICE);
            if (service instanceof InputMethodManager) {
                ((InputMethodManager) service).hideSoftInputFromWindow(focused.getWindowToken(), 0);
            }
        }
        focused.clearFocus();
    }
}
