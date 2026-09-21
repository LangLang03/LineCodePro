package cn.lineai;

import android.graphics.Color;
import android.graphics.Insets;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;
import android.view.WindowInsets;
import android.view.WindowMetrics;

import org.huxerui.HuxerUIActivity;

public final class MainActivity extends HuxerUIActivity {
    private static final int DEFAULT_BACKGROUND = Color.rgb(252, 252, 253);

    static {
        System.loadLibrary(BuildConfig.HUXERUI_APP_LIBRARY);
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowChrome();
    }

    @SuppressWarnings("deprecation")
    public float[] lineCodeWindowInsetsDp() {
        int top = 0;
        int right = 0;
        int bottom = 0;
        int left = 0;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            WindowMetrics metrics = getWindowManager().getCurrentWindowMetrics();
            Insets insets = metrics.getWindowInsets().getInsetsIgnoringVisibility(
                    WindowInsets.Type.systemBars() | WindowInsets.Type.displayCutout());
            top = insets.top;
            right = insets.right;
            bottom = insets.bottom;
            left = insets.left;
        } else {
            View decor = getWindow().getDecorView();
            android.view.WindowInsets rootInsets = decor.getRootWindowInsets();
            if (rootInsets != null) {
                top = rootInsets.getStableInsetTop();
                right = rootInsets.getStableInsetRight();
                bottom = rootInsets.getStableInsetBottom();
                left = rootInsets.getStableInsetLeft();
            }
        }
        float density = getResources().getDisplayMetrics().density;
        return new float[]{top / density, right / density, bottom / density, left / density};
    }

    @SuppressWarnings("deprecation")
    private void configureWindowChrome() {
        Window window = getWindow();
        window.setStatusBarColor(DEFAULT_BACKGROUND);
        // HuxerUI renders edge-to-edge. Keeping the navigation bar opaque
        // prevents in-window drawers and dialogs from painting their scrim
        // beneath it, unlike the legacy edge-to-edge Activity/Dialog pair.
        window.setNavigationBarColor(DEFAULT_BACKGROUND);
        View decor = window.getDecorView();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            decor.setForceDarkAllowed(false);
        }
        int flags = decor.getSystemUiVisibility() | View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flags |= View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR;
        }
        decor.setSystemUiVisibility(flags);
    }
}
