package cn.lineai.feature.extensions;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.SettingsPage;
import cn.lineai.ui.kit.SettingsSection;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.ThemeBinder;
import cn.lineai.ui.tokens.Tokens;

/**
 * Page scaffold for the extension family: a legacy three-slot header whose trailing slot is either
 * a circular add button or a text save action, plus helpers for the section cards.
 */
public abstract class ExtensionPage extends SettingsPage {

    private final Runnable back;

    /**
     * Builds the page with the default kit header.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param title string resource of the page title
     */
    protected ExtensionPage(Context context, Tokens tokens, Runnable back, int title) {
        super(context, title, tokens, back);
        this.back = back;
    }

    /**
     * Builds the page with a kit header carrying a right-side action.
     *
     * @param context Android context used to inflate views
     * @param tokens design tokens for the current theme
     * @param back action invoked when the user navigates back
     * @param title string resource of the page title
     * @param rightIcon icon resource of the right-side action
     * @param rightDescription content description resource of the right-side action
     * @param rightAction action invoked when the right-side control is tapped
     */
    protected ExtensionPage(Context context, Tokens tokens, Runnable back, int title,
                            int rightIcon, int rightDescription, Runnable rightAction) {
        super(context, title, tokens, back, rightIcon, rightDescription, rightAction);
        this.back = back;
    }

    /** Replaces the default kit header with the legacy editor/detail header. */
    protected final void header(int title, View trailing) {
        removeView(getChildAt(0));
        addView(new ExtensionHeader(getContext(), theme, title, back, trailing),
                0, new LayoutParams(-1, -2));
    }

    /**
     * Replaces the header with the legacy header without a trailing control.
     *
     * @param title string resource of the page title
     */
    protected final void header(int title) {
        header(title, null);
    }

    /**
     * Adds a titled section to the page content.
     *
     * @param title section heading text, may contain data-driven labels
     * @return the created section ready to receive rows
     */
    protected final SettingsSection sectionText(CharSequence title) {
        SettingsSection section = new SettingsSection(getContext(), title, theme);
        content.addView(section, new LayoutParams(-1, -2));
        return section;
    }

    /**
     * Appends a view to the page content with the given top margin.
     *
     * @param view view to append
     * @param topMarginDp top margin in density-independent pixels
     */
    protected final void add(View view, int topMarginDp) {
        LayoutParams params = new LayoutParams(-1, -2);
        params.topMargin = Ui.dp(getContext(), topMarginDp);
        content.addView(view, params);
    }

    /** Legacy 60dp centred header with a 36dp back slot and one trailing slot. */
    public static final class ExtensionHeader extends LinearLayout {

        private final Paint border = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final ThemeBinder theme;
        private final TextView title;

        /**
         * Builds the header; null slots reserve their space so the title stays centred.
         *
         * @param context Android context used to inflate views
         * @param theme theme binder shared with the page
         * @param titleRes string resource of the page title
         * @param back back action; null renders an inert slot
         * @param trailing trailing control; null renders an inert slot
         */
        ExtensionHeader(Context context, ThemeBinder theme, int titleRes, Runnable back,
                        View trailing) {
            super(context);
            this.theme = theme;
            setOrientation(HORIZONTAL);
            setGravity(Gravity.CENTER_VERTICAL);
            setWillNotDraw(false);
            setMinimumHeight(Ui.dp(context, 60));
            Ui.padding(this, 16, 12, 16, 12);
            if (back == null) {
                addView(new View(context), new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
            } else {
                IconButton chevron = new IconButton(context, Icons.CHEVRON_LEFT);
                chevron.setIconSizeDp(36, 22);
                chevron.setContentDescription(context.getString(cn.lineai.ui.i18n.R.string.common_back));
                chevron.setOnClickListener(v -> back.run());
                addView(chevron, new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
                theme.bind(t -> chevron.setIconColor(t.text));
            }
            title = Ui.bold(context, context.getString(titleRes), Tokens.FONT_LG,
                    theme.current().text);
            title.setGravity(Gravity.CENTER);
            if (android.os.Build.VERSION.SDK_INT >= 28) title.setAccessibilityHeading(true);
            addView(title, new LayoutParams(0, -2, 1f));
            if (trailing == null) {
                addView(new View(context), new LayoutParams(Ui.dp(context, 36), Ui.dp(context, 36)));
            } else {
                addView(trailing, new LayoutParams(-2, -2));
            }
            theme.bind(t -> {
                setBackgroundColor(t.bg);
                title.setTextColor(t.text);
                invalidate();
            });
        }

        /** Draws the hairline separator along the header bottom edge. */
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            border.setColor(theme.current().border);
            border.setStrokeWidth(Math.max(1f, Ui.dp(getContext(), 0.5f)));
            canvas.drawLine(0, getHeight() - 0.5f, getWidth(), getHeight() - 0.5f, border);
        }
    }
}
