package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewParent;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import cn.lineai.ui.theme.LineTheme;
import java.util.HashMap;
import java.util.Map;

public final class ThinkingBlockView extends LinearLayout {
    private static final Map<String, Boolean> EXPANDED_BY_ID = new HashMap<>();

    private final TextView labelView;
    private final IconButtonView chevronView;
    private final MaxHeightScrollView contentScrollView;
    private final TextView contentView;
    private String messageId = "";
    private boolean expanded;

    public ThinkingBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);

        LinearLayout header = new LinearLayout(context);
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setClickable(true);
        LineTheme.padding(header, 0, 4, 0, 4);

        TextView mark = LineTheme.text(context, "✦", 10, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        header.addView(mark, new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));

        labelView = LineTheme.text(context, "思考中...", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, 4);
        header.addView(labelView, labelParams);

        chevronView = new IconButtonView(context, IconButtonView.CHEVRON_RIGHT);
        chevronView.setIconColor(LineTheme.TEXT_TERTIARY);
        chevronView.setIconSizeDp(12, 12);
        chevronView.setClickable(false);
        LinearLayout.LayoutParams chevronParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 12), LineTheme.dp(context, 12));
        chevronParams.leftMargin = LineTheme.dp(context, 4);
        header.addView(chevronView, chevronParams);
        header.setOnClickListener(v -> {
            expanded = !expanded;
            EXPANDED_BY_ID.put(messageId, expanded);
            updateExpanded();
        });
        addView(header, new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        contentScrollView = new MaxHeightScrollView(context);
        contentScrollView.setFillViewport(false);
        contentScrollView.setOverScrollMode(OVER_SCROLL_IF_CONTENT_SCROLLS);
        contentScrollView.setVerticalScrollBarEnabled(true);

        contentView = LineTheme.text(context, "", LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        contentView.setLineSpacing(LineTheme.dp(context, 4), 1f);
        contentScrollView.addView(contentView, new ScrollView.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        LinearLayout.LayoutParams contentParams = new LinearLayout.LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        contentParams.topMargin = LineTheme.dp(context, LineTheme.SM);
        addView(contentScrollView, contentParams);
    }

    public void bind(String id, String content, boolean streaming) {
        bind(id, content, streaming, false, true);
    }

    public void bind(String id, String content, boolean streaming, boolean autoExpand, boolean scrollable) {
        messageId = id == null ? "" : id;
        Boolean savedExpanded = EXPANDED_BY_ID.get(messageId);
        expanded = savedExpanded == null ? autoExpand : savedExpanded;
        labelView.setText(streaming ? "思考中..." : "思考完毕");
        contentView.setText(content == null ? "" : content);
        contentView.setMaxLines(Integer.MAX_VALUE);
        contentScrollView.setMaxHeightDp(scrollable ? 180 : 0);
        updateExpanded();
    }

    private void updateExpanded() {
        chevronView.setIconType(expanded ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        contentScrollView.setVisibility(expanded ? View.VISIBLE : View.GONE);
    }

    private static final class MaxHeightScrollView extends ScrollView {
        private int maxHeightPx;
        private float lastTouchY;

        MaxHeightScrollView(Context context) {
            super(context);
        }

        @Override
        public boolean dispatchTouchEvent(MotionEvent event) {
            int action = event.getActionMasked();
            if (action == MotionEvent.ACTION_DOWN) {
                lastTouchY = event.getY();
                requestParentDisallowIntercept(canScrollContent());
            } else if (action == MotionEvent.ACTION_MOVE) {
                float nextY = event.getY();
                requestParentDisallowIntercept(shouldHandleDrag(nextY - lastTouchY));
                lastTouchY = nextY;
            } else if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                requestParentDisallowIntercept(false);
            }
            return super.dispatchTouchEvent(event);
        }

        @Override
        public boolean performClick() {
            return super.performClick();
        }

        void setMaxHeightDp(int maxHeightDp) {
            maxHeightPx = maxHeightDp <= 0 ? 0 : LineTheme.dp(getContext(), maxHeightDp);
            requestLayout();
        }

        @Override
        protected void onMeasure(int widthMeasureSpec, int heightMeasureSpec) {
            if (maxHeightPx > 0) {
                int mode = MeasureSpec.getMode(heightMeasureSpec);
                int size = MeasureSpec.getSize(heightMeasureSpec);
                int limitedSize = mode == MeasureSpec.UNSPECIFIED ? maxHeightPx : Math.min(size, maxHeightPx);
                heightMeasureSpec = MeasureSpec.makeMeasureSpec(limitedSize, MeasureSpec.AT_MOST);
            }
            super.onMeasure(widthMeasureSpec, heightMeasureSpec);
        }

        private boolean shouldHandleDrag(float deltaY) {
            if (!canScrollContent()) {
                return false;
            }
            if (deltaY > 0f) {
                return canScrollVertically(-1);
            }
            if (deltaY < 0f) {
                return canScrollVertically(1);
            }
            return true;
        }

        private boolean canScrollContent() {
            if (getChildCount() == 0) {
                return false;
            }
            int viewportHeight = getHeight() - getPaddingTop() - getPaddingBottom();
            return viewportHeight > 0 && getChildAt(0).getHeight() > viewportHeight;
        }

        private void requestParentDisallowIntercept(boolean disallowIntercept) {
            ViewParent parent = getParent();
            if (parent != null) {
                parent.requestDisallowInterceptTouchEvent(disallowIntercept);
            }
        }
    }
}
