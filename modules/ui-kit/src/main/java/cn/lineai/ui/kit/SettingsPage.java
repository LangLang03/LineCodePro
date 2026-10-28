package cn.lineai.ui.kit;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

public class SettingsPage extends LinearLayout {
    public final ThemeBinder theme;
    public final LinearLayout content;
    public final ScrollView scroll;

    public SettingsPage(Context context, int title, Tokens tokens, Runnable back) {
        this(context, title, tokens, back, 0, 0, null);
    }

    public SettingsPage(Context context, int title, Tokens tokens, Runnable back, int rightIcon,
                        int rightDescription, Runnable rightAction) {
        super(context);
        theme = new ThemeBinder(tokens);
        setOrientation(VERTICAL);
        theme.bind(t -> setBackgroundColor(t.bg));
        addView(header(title, back, rightIcon, rightDescription, rightAction), new LayoutParams(-1, -2));
        content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        Ui.padding(content, 0, 0, 0, 100);
        scroll = new ScrollView(context);
        scroll.setFillViewport(false);
        scroll.addView(content, new ScrollView.LayoutParams(-1, -2));
        addView(scroll, new LayoutParams(-1, 0, 1));
    }

    private View header(int title, Runnable back, int rightIcon, int rightDescription,
                        Runnable rightAction) {
        return new ScreenBar(getContext(), theme, title, Icons.CHEVRON_LEFT,
                R.string.common_back, back, rightIcon, rightDescription, rightAction);
    }

    public SettingsSection section(int title) {
        SettingsSection section = new SettingsSection(getContext(), title, theme);
        content.addView(section, new LayoutParams(-1, -2));
        return section;
    }

    public void applyTokens(Tokens tokens) {
        theme.apply(tokens);
    }

    public void dispose() { }
}
