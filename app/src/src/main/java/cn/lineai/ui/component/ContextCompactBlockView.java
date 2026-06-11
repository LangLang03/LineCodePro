package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.model.ChatMessage;
import cn.lineai.ui.theme.LineTheme;

public final class ContextCompactBlockView extends LinearLayout {
    private final IconButtonView icon;
    private final TextView label;
    private final ProgressBar progressBar;
    private final IconButtonView statusIcon;
    private String lastStatus = "";

    public ContextCompactBlockView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setMinimumHeight(LineTheme.dp(context, 34));
        setBackground(LineTheme.rounded(context, LineTheme.CODE_BG, 6));
        LineTheme.padding(this, LineTheme.MD, LineTheme.XS, LineTheme.MD, LineTheme.XS);

        icon = new IconButtonView(context, IconButtonView.ARCHIVE);
        icon.setClickable(false);
        icon.setIconSizeDp(18, 14);
        addView(icon, new LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));

        label = LineTheme.text(context, "压缩", LineTheme.FONT_SM, LineTheme.TEXT_TERTIARY, Typeface.NORMAL);
        label.setTypeface(Typeface.MONOSPACE);
        LayoutParams labelParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, 6);
        addView(label, labelParams);

        addView(new android.view.View(context), new LayoutParams(0, 1, 1f));

        progressBar = new ProgressBar(context);
        progressBar.setIndeterminate(true);
        addView(progressBar, new LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));

        statusIcon = new IconButtonView(context, IconButtonView.CHECK);
        statusIcon.setClickable(false);
        statusIcon.setIconSizeDp(18, 13);
        addView(statusIcon, new LayoutParams(LineTheme.dp(context, 18), LineTheme.dp(context, 18)));
    }

    public void bind(String status) {
        String safeStatus = status == null || status.length() == 0 ? ChatMessage.COMPACT_STATUS_RUNNING : status;
        if (safeStatus.equals(lastStatus)) {
            return;
        }
        lastStatus = safeStatus;
        boolean running = ChatMessage.COMPACT_STATUS_RUNNING.equals(safeStatus);
        boolean error = ChatMessage.COMPACT_STATUS_ERROR.equals(safeStatus);
        int color = error ? LineTheme.DANGER : LineTheme.TEXT_TERTIARY;
        icon.setIconColor(color);
        label.setTextColor(color);
        progressBar.setVisibility(running ? VISIBLE : GONE);
        statusIcon.setVisibility(running ? GONE : VISIBLE);
        statusIcon.setIconType(error ? IconButtonView.CLOSE : IconButtonView.CHECK);
        statusIcon.setIconColor(error ? LineTheme.DANGER : LineTheme.TEXT_TERTIARY);
    }
}
