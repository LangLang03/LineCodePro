package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Insets;
import android.os.Build;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowInsets;
import android.widget.FrameLayout;
import android.widget.LinearLayout;

/**
 * Builds the primitive layout containers that {@link cn.lineai.ui.MainChatView} stacks
 * underneath its business widgets.
 *
 * <p>{@link cn.lineai.ui.MainChatView} is itself the root {@link FrameLayout}; the views
 * produced here are the two primary children that must exist before any header / message
 * list / composer / sheet / drawer is added:</p>
 * <ul>
 *     <li>{@code contentView} — a vertical {@link LinearLayout} that hosts the header, the
 *         message list, and the composer in that order.</li>
 *     <li>{@code screenHost} — a full-bleed {@link FrameLayout} used to overlay standalone
 *         "screens" (settings, about, model management, ...) on top of the chat. It starts
 *         hidden, clickable, and focusable so it can intercept touches when shown.</li>
 * </ul>
 *
 * <p>Business views (header, composer, drawers, sheets, message list) are intentionally
 * <em>not</em> created here — those are wired up in {@code MainChatView}'s constructor
 * after this builder returns. The result is exposed through {@link Result} so the caller
 * keeps direct access to the two base containers without leaking the builder's internals.</p>
 */
public final class MainChatViewLayoutBuilder {

    /**
     * Bundle of base layout containers created by {@link #build(Context)}.
     *
     * <p>The two fields are intended to be {@code addView}-ed to the root chat view
     * (which is itself a {@link FrameLayout}).</p>
     */
    public static final class Result {
        /** Vertical container for header / message list / composer. */
        public final LinearLayout contentView;
        /** Full-bleed overlay container for standalone screens. */
        public final FrameLayout screenHost;

        Result(LinearLayout contentView, FrameLayout screenHost) {
            this.contentView = contentView;
            this.screenHost = screenHost;
        }
    }

    private MainChatViewLayoutBuilder() {
    }

    /**
     * Build the two base layout containers for a chat view. The returned views are not
     * attached to a parent; the caller is responsible for adding them to the chat view
     * root with the appropriate {@link FrameLayout.LayoutParams}.
     *
     * @param context Android context used to instantiate the views.
     * @return a {@link Result} carrying {@code contentView} and {@code screenHost}.
     */
    public static Result build(Context context) {
        LinearLayout contentView = new LinearLayout(context);
        contentView.setOrientation(LinearLayout.VERTICAL);
        contentView.setLayoutParams(new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT));
        contentView.setGravity(Gravity.TOP);

        FrameLayout screenHost = new FrameLayout(context);
        screenHost.setBackgroundColor(LineTheme.BG);
        screenHost.setClickable(true);
        screenHost.setFocusable(true);
        screenHost.setFocusableInTouchMode(true);
        screenHost.setVisibility(View.GONE);

        return new Result(contentView, screenHost);
    }

    /**
     * Install a window-insets listener on {@code host} that propagates the system bar and
     * IME insets to {@code contentView} and {@code screenHost} as padding. This keeps the
     * chat list and any full-screen overlay clear of the status bar, navigation bar, and
     * the soft keyboard.
     *
     * <p>No-op on API &lt; 30, where {@code WindowInsets.Type} is unavailable.</p>
     *
     * @param host        the view that receives the listener (typically the chat view root).
     * @param contentView the chat content container; padded with the system bar height.
     * @param screenHost  the full-screen overlay container; padded the same way.
     */
    @SuppressWarnings("deprecation")
    public static void installSystemBarInsetsHandling(View host, View contentView, View screenHost) {
        if (host == null || contentView == null || screenHost == null) {
            return;
        }
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) {
            return;
        }
        host.setOnApplyWindowInsetsListener((view, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsets.Type.systemBars());
            Insets ime = insets.getInsets(WindowInsets.Type.ime());
            int bottomInset = Math.max(systemBars.bottom, ime.bottom);
            contentView.setPadding(0, systemBars.top, 0, bottomInset);
            screenHost.setPadding(0, systemBars.top, 0, bottomInset);
            return insets;
        });
        host.post(host::requestApplyInsets);
    }
}
