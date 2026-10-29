package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/**
 * Conversation header from C++ {@code chat_screen.cpp Header()}: 56dp minimum height with a
 * 4/2/8/2 padding, a 40x48 menu action with a 19dp glyph, a growing project title (16sp medium,
 * 21dp max height) with a 14dp chevron, a 40x48 context ring (2dp stroke, warning at >=80%) and
 * the permission / new-conversation / more actions.
 */
public final class ChatHeaderView extends LinearLayout {

    public interface Listener {
        void onMenu();

        void onProject();

        void onPermission();

        void onNewConversation();

        void onMore();

        void onContext();
    }

    private static final int ACTION_WIDTH_DP = 40;
    private static final int ACTION_HEIGHT_DP = 48;
    private static final int ACTION_ICON_DP = 19;
    private static final int RESERVED_WIDTH_DP = 212;

    private final List<IconButton> actions = new ArrayList<>();
    private final TextView projectText;
    private final IconButton chevron;
    private final ContextRing ring;
    private Listener listener;
    private Tokens tokens;

    public ChatHeaderView(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(tokens.bg);
        setMinimumHeight(Ui.dp(context, 56));
        Ui.padding(this, 4, 2, 8, 2);

        IconButton menu = new IconButton(context, Icons.MENU);
        menu.setIconColor(tokens.textSecondary);
        menu.setIconSizeDp(ACTION_WIDTH_DP, ACTION_ICON_DP);
        menu.setContentDescription(context.getString(R.string.header_menu_desc));
        menu.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenu();
            }
        });
        actions.add(menu);
        addView(menu, actionParams(context));

        LinearLayout brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setMinimumHeight(Ui.dp(context, 48));
        brand.setFocusable(true);
        brand.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProject();
            }
        });
        projectText = Ui.medium(context,
                context.getString(R.string.header_project_default), Tokens.FONT_MD, tokens.text);
        projectText.setSingleLine(true);
        projectText.setEllipsize(TextUtils.TruncateAt.END);
        projectText.setMaxHeight(Ui.dp(context, 21));
        brand.addView(projectText, new LayoutParams(-2, -2));
        chevron = new IconButton(context, Icons.CHEVRON_DOWN);
        chevron.setIconColor(tokens.textSecondary);
        chevron.setIconSizeDp(24, 14);
        chevron.setClickable(false);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(chevron, new LayoutParams(Ui.dp(context, 24), Ui.dp(context, 32)));
        addView(brand, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        ring = new ContextRing(context, tokens);
        ring.setContentDescription(context.getString(R.string.context_usage_title));
        ring.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContext();
            }
        });
        addView(ring, actionParams(context));

        addAction(context, Icons.SHIELD, R.string.header_permission_desc, () -> {
            if (listener != null) {
                listener.onPermission();
            }
        });
        addAction(context, Icons.PLUS, R.string.header_new_conversation_desc, () -> {
            if (listener != null) {
                listener.onNewConversation();
            }
        });
        addAction(context, Icons.MORE, R.string.chat_context_more, () -> {
            if (listener != null) {
                listener.onMore();
            }
        });
    }

    private LinearLayout.LayoutParams actionParams(Context context) {
        return new LayoutParams(Ui.dp(context, ACTION_WIDTH_DP),
                Ui.dp(context, ACTION_HEIGHT_DP));
    }

    private void addAction(Context context, int icon, int description, Runnable action) {
        IconButton button = new IconButton(context, icon);
        button.setIconSizeDp(ACTION_WIDTH_DP, ACTION_ICON_DP);
        button.setIconColor(tokens.textSecondary);
        button.setContentDescription(context.getString(description));
        button.setOnClickListener(v -> action.run());
        actions.add(button);
        addView(button, actionParams(context));
    }

    public void setListener(Listener value) {
        listener = value;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        projectText.setMaxWidth(Math.max(0, width - getPaddingLeft() - getPaddingRight()
                - Ui.dp(getContext(), RESERVED_WIDTH_DP)));
    }

    public void render(ChatState state, Tokens current) {
        applyTokens(current);
        String label = state.projectLabel.isEmpty()
                ? getContext().getString(R.string.header_project_default) : state.projectLabel;
        projectText.setText(label);
        findBrand().setContentDescription(label);
        ring.setPercent(state.contextPercent);
        ring.setContentDescription(getContext().getString(
                R.string.header_context_desc, state.contextPercent));
    }

    private View findBrand() {
        return getChildAt(1);
    }

    /** Restyles in place; no view is recreated, so focus and layout survive a theme switch. */
    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        projectText.setTextColor(current.text);
        chevron.setIconColor(current.textSecondary);
        for (IconButton action : actions) {
            action.setIconColor(current.textSecondary);
        }
        ring.applyTokens(current);
    }

    private static final class ContextRing extends View {
        private final Paint track = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final Paint progress = new Paint(Paint.ANTI_ALIAS_FLAG);
        private final RectF bounds = new RectF();
        private Tokens tokens;
        private int percent;

        ContextRing(Context context, Tokens tokens) {
            super(context);
            this.tokens = tokens;
            track.setStyle(Paint.Style.STROKE);
            track.setStrokeWidth(Ui.dp(context, 2));
            progress.setStyle(Paint.Style.STROKE);
            progress.setStrokeWidth(Ui.dp(context, 2));
            progress.setStrokeCap(Paint.Cap.ROUND);
        }

        void setPercent(int value) {
            percent = Math.max(0, Math.min(100, value));
            invalidate();
        }

        void applyTokens(Tokens current) {
            tokens = current;
            invalidate();
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float cx = getWidth() / 2f;
            float cy = getHeight() / 2f;
            float radius = Ui.dp(getContext(), 8);
            bounds.set(cx - radius, cy - radius, cx + radius, cy + radius);
            track.setColor(tokens.border);
            canvas.drawArc(bounds, -90f, 360f, false, track);
            if (percent > 0) {
                progress.setColor(percent >= 80 ? tokens.warning : tokens.textSecondary);
                canvas.drawArc(bounds, -90f, 360f * percent / 100f, false, progress);
            }
        }
    }
}
