package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;

public final class AdvancedFeaturesScreenView extends ScreenSurfaceView {
    public interface Listener {
        void onBack();

        void onOpen(String id);
    }

    private final Listener listener;

    public AdvancedFeaturesScreenView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, context.getString(R.string.screen_advanced_title), listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 12, 8, 12, 48);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        addCard(content, "phoneControl", context.getString(R.string.screen_advanced_phone_control_title), context.getString(R.string.screen_advanced_phone_control_desc), context.getString(R.string.screen_advanced_phone_control_badge), IconButtonView.SMARTPHONE);
    }

    private void addCard(LinearLayout content, String id, String title, String desc, String badge, int iconType) {
        CardViewHelper.addCard(content, id, title, desc, badge, iconType, listener::onOpen);
    }
}
