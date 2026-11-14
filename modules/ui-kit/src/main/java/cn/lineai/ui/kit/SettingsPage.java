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

/**
 * Base page for settings-style screens: a {@link ScreenBar} header over a scrolling content column.
 *
 * <p>Subclasses append sections into {@link #content} and restyle through the shared
 * {@link #theme} binder; theme changes mutate the existing view tree in place and never rebuild
 * children. The class also owns the scroll-restore convention used by the navigation host when a
 * page returns from the back stack.</p>
 */
public class SettingsPage extends LinearLayout {
    /** Binder shared with every child; subclasses must register new views on it. */
    public final ThemeBinder theme;
    /** Vertical content column inside {@link #scroll}; children are appended, never replaced. */
    public final LinearLayout content;
    /** Scrolling container around {@link #content}; removed only by frames that embed the page. */
    public final ScrollView scroll;

    /**
     * Creates a page with only a back action in the header.
     *
     * @param context hosting context
     * @param title resource id of the header title
     * @param tokens initial palette snapshot
     * @param back action run when the back button is pressed
     */
    public SettingsPage(Context context, int title, Tokens tokens, Runnable back) {
        this(context, title, tokens, back, 0, 0, null);
    }

    /**
     * Creates a page with an optional secondary header action on the right.
     *
     * @param context hosting context
     * @param title resource id of the header title
     * @param tokens initial palette snapshot
     * @param back action run when the back button is pressed
     * @param rightIcon icon id from {@link Icons}, or 0 for no right action
     * @param rightDescription resource id of the right action's content description; unused when
     *     {@code rightIcon} is 0
     * @param rightAction action run on the right button; a null value is treated like no action
     */
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

    /** Builds the shared header bar; subclasses that need a custom header override the assembly. */
    private View header(int title, Runnable back, int rightIcon, int rightDescription,
                        Runnable rightAction) {
        return new ScreenBar(getContext(), theme, title, Icons.CHEVRON_LEFT,
                R.string.common_back, back, rightIcon, rightDescription, rightAction);
    }

    /**
     * Appends a titled {@link SettingsSection} to the content column.
     *
     * @param title resource id of the section heading
     * @return the created section; append rows to it via {@link SettingsSection#row}
     */
    public SettingsSection section(int title) {
        SettingsSection section = new SettingsSection(getContext(), title, theme);
        content.addView(section, new LayoutParams(-1, -2));
        return section;
    }

    /**
     * Applies a new palette to the page and every registered child, in place.
     *
     * @param tokens replacement palette snapshot; must carry the same layout-affecting metrics as
     *     the original because views are restyled, not rebuilt
     */
    public void applyTokens(Tokens tokens) {
        theme.apply(tokens);
    }

    /**
     * Restores a saved vertical scroll offset. Pages that build their content asynchronously
     * override this so the offset is applied after the content exists instead of being clamped.
     *
     * @param offset saved scroll position in pixels, from a previous instance
     */
    public void restoreScroll(int offset) {
        post(() -> scroll.scrollTo(0, offset));
    }

    /**
     * Releases page-held resources; the base implementation owns none, so it is a no-op. Subclasses
     * holding system resources such as WebViews must override this and must not rebuild the view
     * tree afterwards.
     */
    public void dispose() { }
}
