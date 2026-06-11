package cn.lineai.ui.component;

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
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.SheetOption;
import cn.lineai.ui.theme.LineTheme;
import java.util.List;

public final class BottomSheetView extends FrameLayout {
    public interface Listener {
        void onSheetDismissed();

        void onSheetOptionSelected(String id);
    }

    private final LinearLayout panel;
    private Listener listener;

    public BottomSheetView(Context context) {
        super(context);
        setVisibility(GONE);
        setClickable(true);

        View backdrop = new View(context);
        backdrop.setBackgroundColor(LineTheme.OVERLAY);
        backdrop.setOnClickListener(v -> close());
        addView(backdrop, new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT));

        panel = new LinearLayout(context);
        panel.setOrientation(LinearLayout.VERTICAL);
        panel.setBackground(LineTheme.roundedTop(context, LineTheme.SURFACE_ELEVATED, 16));
        FrameLayout.LayoutParams panelParams = new FrameLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        panelParams.gravity = Gravity.BOTTOM;
        addView(panel, panelParams);
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
        LineTheme.padding(header, LineTheme.LG, 0, LineTheme.LG, LineTheme.MD);
        TextView titleView = LineTheme.text(context, title, LineTheme.FONT_LG, LineTheme.TEXT, Typeface.BOLD);
        header.addView(titleView, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        panel.addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View divider = new View(context);
        divider.setBackgroundColor(LineTheme.BORDER_LIGHT);
        panel.addView(divider, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, 1));

        for (SheetOption option : options) {
            panel.addView(createOptionRow(option), new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }

        View bottomInset = new View(context);
        panel.addView(bottomInset, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LineTheme.dp(context, 34)));

        setVisibility(VISIBLE);
        bringToFront();
    }

    public void close() {
        if (getVisibility() != VISIBLE) {
            return;
        }
        setVisibility(GONE);
        if (listener != null) {
            listener.onSheetDismissed();
        }
    }

    private View createOptionRow(SheetOption option) {
        View row = createOptionContentRow(option);
        if (option.hasDeleteAction()) {
            attachDeleteLongPress(row, () -> showDeleteConfirmation(option));
        }
        return row;
    }

    private View createOptionContentRow(SheetOption option) {
        Context context = getContext();
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
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
        AlertDialog dialog = new AlertDialog.Builder(getContext())
                .setTitle("移除工作区")
                .setMessage("从已打开目录中移除「" + option.getLabel() + "」？\n\n不会删除真实目录。")
                .setNegativeButton("取消", null)
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
