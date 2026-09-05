package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.R;

public final class ExtensionsScreenView extends ScreenSurfaceView {
    public interface Listener {
        void onBack();

        void onOpen(String id);
    }

    private final Listener listener;

    public ExtensionsScreenView(Context context, Listener listener) {
        super(context);
        this.listener = listener;
        setOrientation(VERTICAL);
        setBackgroundColor(LineTheme.BG);

        addView(new ScreenHeaderView(context, getContext().getString(R.string.screen_extensions_title), listener::onBack, null), new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        ScrollView scrollView = new ScrollView(context);
        LinearLayout content = new LinearLayout(context);
        content.setOrientation(VERTICAL);
        LineTheme.padding(content, 12, 8, 12, 48);
        scrollView.addView(content, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        addView(scrollView, new LayoutParams(LayoutParams.MATCH_PARENT, 0, 1f));

        addCard(content, "agent", getContext().getString(R.string.screen_extensions_section_agent), getContext().getString(R.string.screen_extensions_desc_agent), getContext().getString(R.string.screen_extensions_badge_can_add), IconButtonView.BRAIN);
        addCard(content, "mcp", getContext().getString(R.string.screen_extensions_section_mcp), getContext().getString(R.string.screen_extensions_desc_mcp), getContext().getString(R.string.screen_extensions_badge_https), IconButtonView.MCP);
        addCard(content, "skills", getContext().getString(R.string.screen_extensions_section_skills), getContext().getString(R.string.screen_extensions_desc_skills), getContext().getString(R.string.screen_extensions_badge_zip), IconButtonView.ARCHIVE);
        addCard(content, "linecode", getContext().getString(R.string.screen_extensions_section_linecode), getContext().getString(R.string.screen_extensions_desc_linecode), getContext().getString(R.string.screen_extensions_badge_lip), IconButtonView.PACKAGE);
        addCard(content, "terminalProvider", getContext().getString(R.string.screen_extensions_section_terminal_provider), getContext().getString(R.string.screen_extensions_desc_terminal_provider), getContext().getString(R.string.screen_extensions_badge_terminal_provider), IconButtonView.TERMINAL);
    }

    private void addCard(LinearLayout content, String id, String title, String desc, String badge, int iconType) {
        CardViewHelper.addCard(content, id, title, desc, badge, iconType, listener::onOpen);
    }
}
