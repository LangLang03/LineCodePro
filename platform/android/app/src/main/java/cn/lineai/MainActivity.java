package cn.lineai;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import org.huxerui.HuxerUIActivity;

public final class MainActivity extends HuxerUIActivity {
    private static final int DEFAULT_BACKGROUND = Color.rgb(252, 252, 253);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowChrome();
    }

    @SuppressWarnings("deprecation")
    private void configureWindowChrome() {
        Window window = getWindow();
        window.setStatusBarColor(DEFAULT_BACKGROUND);
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
