package cn.lineai.ui.component;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.ColorDrawable;
import android.os.Handler;
import android.os.Looper;
import android.text.Spannable;
import android.text.SpannableString;
import android.text.TextUtils;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.PopupWindow;
import android.widget.TextView;
import java.util.List;

/**
 * composer 中 {@code /} 触发的命令 popup 组件。仅负责"标题 + 可点击行列表"的渲染
 * 与点击事件回调；不包含输入解析与状态管理（由 {@link ComposerView} 配合
 * {@link cn.lineai.ui.util.SlashCommandCatalog} 完成）。
 *
 * <p>视觉规范：圆角描边、行高 38dp、单选态圆点指示，与 composer 现有的
 * modelPopup / modePopup 保持一致。</p>
 */
public final class SlashCommandPopup {

    /**
     * 单行数据。{@link #label} 为主标题（粗体），{@link #description} 为副标题（灰色小字）。
     */
    public static final class Row {
        public final String label;
        public final String description;
        public final Runnable onClick;

        public Row(String label, String description, Runnable onClick) {
            this.label = label == null ? "" : label;
            this.description = description == null ? "" : description;
            this.onClick = onClick;
        }
    }

    private final Context context;
    private final PopupWindow popup;
    private final LinearLayout content;
    private final android.widget.ScrollView viewport;
    private int selectedIndex = -1;
    private int lastRowCount = 0;
    private String lastTitle = null;

    public SlashCommandPopup(Context context) {
        this.context = context;
        content = new LinearLayout(context);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setBackground(LineTheme.roundedStroke(context, LineTheme.INPUT_BG, 14, LineTheme.BORDER_LIGHT));
        LineTheme.padding(content, 8, 8, 8, 8);
        viewport = new android.widget.ScrollView(context);
        viewport.setFillViewport(false);viewport.addView(content);
        viewport.setBackground(LineTheme.rounded(context,LineTheme.INPUT_BG,14));viewport.setClipToOutline(true);
        popup = new PopupWindow(context);
        popup.setOutsideTouchable(true);
        popup.setBackgroundDrawable(new ColorDrawable(Color.TRANSPARENT));
        popup.setFocusable(false);
    }

    /**
     * 渲染并展示 popup。{@code title} 为顶部标题（可空）；{@code rows} 不可为空。
     * 重复调用相同 title+rows 数时不重建视图。
     */
    public void show(String title, List<Row> rows) {
        if (rows == null || rows.isEmpty()) {
            dismiss();
            return;
        }
        String safeTitle = title == null ? "" : title;
        content.removeAllViews();
        lastTitle = safeTitle;
        lastRowCount = rows.size();
        if (safeTitle.length() > 0) {
            content.addView(titleView(safeTitle), titleParams());
        }
        for (int i = 0; i < rows.size(); i++) {
            Row row = rows.get(i);
            content.addView(rowView(row, i), rowParams());
        }
    }

    /**
     * 在 {@code anchor}（composer 容器）上方显示 popup。宽度与 anchor 减去两侧
     * {@link LineTheme#LG} 缝隙保持一致；x 与 anchor 左边对齐，y 在 anchor 顶部上方 8dp。
     */
    public void showAtAnchor(View anchor) {
        if (anchor == null || anchor.getWidth() == 0 || anchor.getHeight() == 0) {
            return;
        }
        if (content.getChildCount() == 0) {
            return;
        }
        int widthMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        int heightMeasureSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED);
        content.measure(widthMeasureSpec, heightMeasureSpec);
        int popupWidth = anchor.getWidth() - 2 * LineTheme.dp(context, LineTheme.LG);
        int[] anchorLocation = new int[2]; anchor.getLocationOnScreen(anchorLocation);
        int popupHeight = Math.min(content.getMeasuredHeight(),Math.max(LineTheme.dp(context,52),anchorLocation[1]-LineTheme.dp(context,24)));
        if (popupWidth <= 0 || popupHeight <= 0) {
            return;
        }
        popup.setWidth(popupWidth);
        popup.setHeight(popupHeight);
        if (popup.isShowing()) {
            popup.update(popupWidth, popupHeight);
            return;
        }
        int[] location = new int[2];
        anchor.getLocationOnScreen(location);
        int x = location[0] + LineTheme.dp(context, LineTheme.LG);
        int y = Math.max(0, location[1] - popupHeight - LineTheme.dp(context, 8));
        popup.setContentView(viewport);
        popup.showAtLocation(anchor, Gravity.NO_GRAVITY, x, y);
    }

    public void dismiss() {
        if (popup.isShowing()) {
            popup.dismiss();
        }
        lastTitle = null;
        lastRowCount = 0;
    }

    public boolean isShowing() {
        return popup.isShowing();
    }

    public void setSelectedIndex(int index) {
        this.selectedIndex = index;
    }

    private TextView titleView(String text) {
        TextView view = LineTheme.textMedium(context, text, LineTheme.FONT_SM, LineTheme.TEXT);
        view.setSingleLine(true);
        view.setEllipsize(TextUtils.TruncateAt.END);
        return view;
    }

    private LinearLayout.LayoutParams titleParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LineTheme.dp(context, 22)
        );
        params.leftMargin = LineTheme.dp(context, LineTheme.MD);
        params.rightMargin = LineTheme.dp(context, LineTheme.MD);
        params.topMargin = LineTheme.dp(context, 1);
        return params;
    }

    private View rowView(Row row, int index) {
        LinearLayout container = new LinearLayout(context);
        container.setOrientation(LinearLayout.VERTICAL);
        LineTheme.padding(container, 16, 14, 16, 14);
        container.setMinimumHeight(LineTheme.dp(context, 52));
        container.setGravity(Gravity.CENTER_VERTICAL);

        LinearLayout row1 = new LinearLayout(context);
        row1.setOrientation(LinearLayout.HORIZONTAL);
        row1.setGravity(Gravity.CENTER_VERTICAL);
        container.addView(row1, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        ));

        TextView label = LineTheme.textMedium(context, row.label, LineTheme.FONT_SM, LineTheme.TEXT);
        label.setSingleLine(true);
        label.setEllipsize(TextUtils.TruncateAt.END);
        Spannable formattedLabel = formatLabel(row.label);
        if (formattedLabel != null) {
            label.setText(formattedLabel);
        }
        row1.addView(label, new LinearLayout.LayoutParams(
                0,
                LinearLayout.LayoutParams.WRAP_CONTENT,
                1f
        ));

        if (index == selectedIndex) {
            View dot = new View(context);
            dot.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 4));
            LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                    LineTheme.dp(context, 7),
                    LineTheme.dp(context, 7)
            );
            dotParams.leftMargin = LineTheme.dp(context, LineTheme.SM);
            row1.addView(dot, dotParams);
        }

        if (row.description.length() > 0) {
            TextView desc = LineTheme.text(context, row.description,
                    LineTheme.FONT_XS, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
            desc.setSingleLine(true);
            desc.setEllipsize(TextUtils.TruncateAt.END);
            LinearLayout.LayoutParams descParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT,
                    LinearLayout.LayoutParams.WRAP_CONTENT
            );
            descParams.topMargin = LineTheme.dp(context, 1);
            container.addView(desc, descParams);
        }

        container.setClickable(true);
        container.setFocusable(true);
        container.setOnClickListener(v -> {
            Runnable onClick = row.onClick;
            dismiss();
            if (onClick != null) {
                new Handler(Looper.getMainLooper()).post(onClick);
            }
        });
        return container;
    }

    private Spannable formatLabel(String text) {
        SpannableString span = new SpannableString(text == null ? "" : text);
        if (span.length() > 0 && span.charAt(0) == '/') {
            int end = 1;
            while (end < span.length() && span.charAt(end) != ' ' && span.charAt(end) != '\t') {
                end++;
            }
            span.setSpan(new ForegroundColorSpan(LineTheme.ACCENT), 0, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
            span.setSpan(new StyleSpan(Typeface.BOLD), 0, end, Spannable.SPAN_INCLUSIVE_EXCLUSIVE);
        }
        return span;
    }

    private LinearLayout.LayoutParams rowParams() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
        );
    }
}
