package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.app.AlertDialog;
import android.content.Context;
import android.graphics.Typeface;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.HapticFeedbackConstants;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewGroup;
import android.view.animation.AccelerateInterpolator;
import android.view.animation.DecelerateInterpolator;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.SheetOption;
import java.util.List;

public final class BottomSheetView extends FrameLayout {
    private static final long OPEN_MS = 180L;
    private static final long CLOSE_MS = 150L;

    public interface Listener {
        void onSheetDismissed();

        void onSheetOptionSelected(String id);
    }

    private final LinearLayout panel;
    private final View backdrop;
    private Listener listener;

    public BottomSheetView(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(true);

        backdrop = new View(context);
        backdrop.setBackgroundColor(LineTheme.OVERLAY);
        backdrop.setOnClickListener(v -> close());
        addView(backdrop, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new InsetSheetLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setClipToOutline(true);
        panel.setBackground(LineTheme.roundedStroke(context, LineTheme.BG, 24, LineTheme.BORDER_LIGHT));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM | Gravity.CENTER_HORIZONTAL;
        panelParams.leftMargin = panelParams.rightMargin = LineTheme.dp(context, 16);
        panelParams.bottomMargin = LineTheme.dp(context, 16);
        addView(panel, panelParams);
    }

    @Override protected void onMeasure(int width, int height) {
        int available = Math.max(1, MeasureSpec.getSize(height)-LineTheme.dp(getContext(),64));
        ((InsetSheetLayout)panel).setAvailableHeight(available);
        super.onMeasure(width,height);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void show(String title, List<SheetOption> options) {
        Context context = getContext();
        panel.removeAllViews();

        View handle = new View(context);
        handle.setBackground(LineTheme.rounded(context, LineTheme.TEXT_TERTIARY, 2));
        LinearLayout.LayoutParams handleParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 36), LineTheme.dp(context, 4));
        handleParams.gravity = Gravity.CENTER_HORIZONTAL;
        handleParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        handleParams.bottomMargin = LineTheme.dp(context, LineTheme.XS);
        panel.addView(handle, handleParams);

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setOrientation(LinearLayout.HORIZONTAL);
        LineTheme.padding(header, 24, 12, 24, 20);
        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        android.widget.ScrollView scroll = new android.widget.ScrollView(context);
        scroll.setVerticalScrollBarEnabled(false);
        LinearLayout choices = new LinearLayout(context); choices.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(choices, 0, 0, 0, 16);
        if (options != null) for (SheetOption option : options) {
            choices.addView(createOptionRow(option), new LinearLayout.LayoutParams(-1, -2));
        }
        scroll.addView(choices, new android.widget.ScrollView.LayoutParams(-1, -2));
        panel.addView(scroll, new LinearLayout.LayoutParams(-1, -2));

        openAnimated();
    }

    public void close() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        closeAnimated();
    }

    private View createOptionRow(SheetOption option) {
        View row = createOptionContentRow(option);
        if (option.hasDeleteAction()) {
            attachDeleteLongPress(row, () -> showDeleteConfirmation(option));
        }
        return row;
    }

    private void openAnimated() {
        panel.animate().cancel();
        backdrop.animate().cancel();
        setVisibility(VISIBLE);
        bringToFront();
        panel.setTranslationY(panelOffset());
        backdrop.setAlpha(0f);
        panel.animate()
                .translationY(0f)
                .setDuration(OPEN_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
        backdrop.animate()
                .alpha(1f)
                .setDuration(OPEN_MS)
                .setInterpolator(new DecelerateInterpolator())
                .start();
    }

    private void closeAnimated() {
        panel.animate().cancel();
        backdrop.animate().cancel();
        panel.animate()
                .translationY(panelOffset())
                .setDuration(CLOSE_MS)
                .setInterpolator(new AccelerateInterpolator())
                .start();
        backdrop.animate()
                .alpha(0f)
                .setDuration(CLOSE_MS)
                .setInterpolator(new AccelerateInterpolator())
                .withEndAction(() -> {
                    setVisibility(GONE);
                    panel.setTranslationY(0f);
                    backdrop.setAlpha(1f);
                    if (listener != null) {
                        listener.onSheetDismissed();
                    }
                })
                .start();
    }

    private int panelOffset() {
        int height = panel.getHeight();
        return height > 0 ? height : getResources().getDisplayMetrics().heightPixels;
    }

    private View createOptionContentRow(SheetOption option) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setMinimumHeight(LineTheme.dp(context, 52));
        row.setBackgroundColor(option.isSelected() ? LineTheme.ACCENT_MUTED : android.graphics.Color.TRANSPARENT);
        LineTheme.padding(row, LineTheme.LG, 14, LineTheme.LG, 14);
        row.setClickable(true);
        row.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSheetOptionSelected(option.getId());
            }
        });

        LinearLayout labels = new LinearLayout(context);
        labels.setOrientation(LinearLayout.VERTICAL);
        row.addView(labels, new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        TextView label = LineTheme.text(context, option.getLabel(), LineTheme.FONT_MD, LineTheme.TEXT, Typeface.NORMAL);
        labels.addView(label, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (option.getDescription() != null && option.getDescription().length() > 0) {
            TextView desc = LineTheme.text(context, option.getDescription(), LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
            descParams.topMargin = LineTheme.dp(context, 2);
            labels.addView(desc, descParams);
        }

        if (option.isSelected()) {
            IconButtonView check = new IconButtonView(context, IconButtonView.CHECK);
            check.setIconColor(LineTheme.ACCENT);
            check.setClickable(false);
            row.addView(check, new LinearLayout.LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
        }
        return row;
    }

    private void showDeleteConfirmation(SheetOption option) {
        Context context = getContext();
        AlertDialog dialog = new LineAlertDialog.Builder(context)
                .setTitle(context.getString(R.string.drawer_project_remove_title))
                .setMessage(context.getString(R.string.drawer_project_remove_message, option.getLabel()))
                .setNegativeButton(context.getString(R.string.common_cancel), null)
                .setPositiveButton(option.getDeleteActionLabel(), (d, which) -> {
                    if (listener != null) {
                        listener.onSheetOptionSelected(option.getDeleteActionId());
                    }
                })
                .create();
        dialog.setOnShowListener(d -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).setTextColor(LineTheme.DANGER));
        dialog.show();
    }

    private void attachDeleteLongPress(View view, Runnable action) {
        LongPressTouchHandler handler = new LongPressTouchHandler(action);
        attachDeleteLongPress(view, handler);
    }

    private void attachDeleteLongPress(View view, LongPressTouchHandler handler) {
        view.setHapticFeedbackEnabled(true);
        view.setOnTouchListener(handler);
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                attachDeleteLongPress(group.getChildAt(i), handler);
            }
        }
    }

    private static final class LongPressTouchHandler implements View.OnTouchListener {
        private final Handler handler = new Handler(Looper.getMainLooper());
        private final Runnable action;
        private float downX;
        private float downY;
        private int touchSlop;
        private boolean fired;
        private Runnable pending;

        LongPressTouchHandler(Runnable action) {
            this.action = action;
        }

        @Override
        public boolean onTouch(View view, MotionEvent event) {
            int eventAction = event.getActionMasked();
            if (eventAction == MotionEvent.ACTION_DOWN) {
                touchSlop = ViewConfiguration.get(view.getContext()).getScaledTouchSlop();
                downX = event.getRawX();
                downY = event.getRawY();
                fired = false;
                cancel();
                pending = () -> {
                    fired = true;
                    view.performHapticFeedback(HapticFeedbackConstants.LONG_PRESS);
                    action.run();
                };
                handler.postDelayed(pending, ViewConfiguration.getLongPressTimeout());
                return false;
            }
            if (eventAction == MotionEvent.ACTION_MOVE) {
                float dx = Math.abs(event.getRawX() - downX);
                float dy = Math.abs(event.getRawY() - downY);
                if (dx > touchSlop || dy > touchSlop) {
                    cancel();
                }
                return fired;
            }
            if (eventAction == MotionEvent.ACTION_UP) {
                if (!fired) {
                    view.performClick();
                }
                cancel();
                return fired;
            }
            if (eventAction == MotionEvent.ACTION_CANCEL) {
                cancel();
                return fired;
            }
            return fired;
        }

        private void cancel() {
            if (pending != null) {
                handler.removeCallbacks(pending);
                pending = null;
            }
        }
    }
}
