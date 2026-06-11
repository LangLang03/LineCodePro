package cn.lineai.ui.component;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.ChatUiState;
import cn.lineai.ui.theme.LineTheme;

public final class HeaderView extends LinearLayout {
    public interface Listener {
        void onMenuClick();

        void onProjectClick();

        void onPermissionClick();

        void onNewConversationClick();

        void onMoreClick();
    }

    private final Paint borderPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final TextView projectText;
    private Listener listener;

    public HeaderView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setWillNotDraw(false);
        LineTheme.padding(this, LineTheme.MD, LineTheme.MD, LineTheme.MD, LineTheme.MD);
        setMinimumHeight(LineTheme.dp(context, 58));

        IconButtonView menu = icon(context, IconButtonView.MENU, LineTheme.TEXT, 20);
        menu.setContentDescription("Menu");
        menu.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenuClick();
            }
        });
        addView(menu);

        LinearLayout projectButton = new LinearLayout(context);
        projectButton.setOrientation(HORIZONTAL);
        projectButton.setGravity(Gravity.CENTER_VERTICAL);
        projectButton.setClickable(true);
        projectButton.setFocusable(true);
        projectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProjectClick();
            }
        });
        LinearLayout.LayoutParams projectParams = new LinearLayout.LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        projectParams.leftMargin = LineTheme.dp(context, 4);
        projectParams.rightMargin = LineTheme.dp(context, 6);
        addView(projectButton, projectParams);

        View dot = new View(context);
        dot.setBackground(LineTheme.rounded(context, LineTheme.ACCENT, 4));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(LineTheme.dp(context, 7), LineTheme.dp(context, 7));
        projectButton.addView(dot, dotParams);

        projectText = LineTheme.textMedium(context, "LineCode", LineTheme.FONT_MD, LineTheme.TEXT);
        projectText.setSingleLine(true);
        LinearLayout.LayoutParams labelParams = new LinearLayout.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        labelParams.leftMargin = LineTheme.dp(context, 6);
        projectButton.addView(projectText, labelParams);

        IconButtonView chevron = icon(context, IconButtonView.CHEVRON_DOWN, LineTheme.TEXT_SECONDARY, 14);
        chevron.setIconSizeDp(20, 14);
        projectButton.addView(chevron, new LinearLayout.LayoutParams(LineTheme.dp(context, 20), LineTheme.dp(context, 20)));

        IconButtonView shield = icon(context, IconButtonView.SHIELD, LineTheme.TEXT_SECONDARY, 18);
        shield.setContentDescription("Permission");
        shield.setOnClickListener(v -> {
            if (listener != null) {
                listener.onPermissionClick();
            }
        });
        addView(shield);

        IconButtonView plus = icon(context, IconButtonView.PLUS, LineTheme.TEXT_SECONDARY, 20);
        plus.setContentDescription("New conversation");
        plus.setOnClickListener(v -> {
            if (listener != null) {
                listener.onNewConversationClick();
            }
        });
        addView(plus);

        IconButtonView more = icon(context, IconButtonView.MORE, LineTheme.TEXT_SECONDARY, 18);
        more.setContentDescription("More");
        more.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMoreClick();
            }
        });
        addView(more);
    }

    public void setListener(Listener listener) {
        this.listener = listener;
    }

    public void render(ChatUiState state) {
        projectText.setText(state.getProjectLabel());
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        borderPaint.setColor(LineTheme.BORDER);
        borderPaint.setStrokeWidth(1f);
        canvas.drawLine(0, getHeight() - 1, getWidth(), getHeight() - 1, borderPaint);
    }

    private IconButtonView icon(Context context, int type, int color, int iconDp) {
        IconButtonView view = new IconButtonView(context, type);
        view.setIconColor(color);
        view.setIconSizeDp(34, iconDp);
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(context, 34), LineTheme.dp(context, 34));
        view.setLayoutParams(params);
        return view;
    }
}
