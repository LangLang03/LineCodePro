package cn.lineai;

import android.graphics.Color;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.view.Window;

import org.huxerui.HuxerUIActivity;

public final class MainActivity extends HuxerUIActivity {
    private static final int COFFEE_BACKGROUND = Color.rgb(244, 239, 230);

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        configureWindowChrome();
    }

    @SuppressWarnings("deprecation")
    private void configureWindowChrome() {
        Window window = getWindow();
        window.setStatusBarColor(COFFEE_BACKGROUND);
        window.setNavigationBarColor(COFFEE_BACKGROUND);
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
