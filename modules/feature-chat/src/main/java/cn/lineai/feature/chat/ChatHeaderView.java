package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.RectF;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Conversation header: navigation, project selector, context ring and primary actions. */
public final class ChatHeaderView extends LinearLayout {

    public interface Listener {
        void onMenu();

        void onProject();

        void onPermission();

        void onNewConversation();

        void onMore();

        void onContext();
    }

    private Listener listener;
    private final TextView projectText;
    private final ContextRing ring;
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
        menu.setIconSizeDp(40, 19);
        menu.setContentDescription(context.getString(R.string.header_menu_desc));
        menu.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMenu();
            }
        });
        addView(menu, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));

        LinearLayout brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setMinimumHeight(Ui.dp(context, 48));
        brand.setFocusable(true);
        brand.setOnClickListener(v -> {
            if (listener != null) {
                listener.onProject();
            }
        });
        projectText = Ui.medium(context, context.getString(R.string.header_project_default), 16, tokens.text);
        projectText.setSingleLine(true);
        projectText.setEllipsize(TextUtils.TruncateAt.END);
        brand.addView(projectText, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        IconButton chevron = new IconButton(context, Icons.CHEVRON_DOWN);
        chevron.setIconSizeDp(24, 14);
        chevron.setIconColor(tokens.textSecondary);
        chevron.setClickable(false);
        brand.addView(chevron, new LayoutParams(Ui.dp(context, 24), Ui.dp(context, 32)));
        addView(brand, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f));

        ring = new ContextRing(context, tokens);
        ring.setContentDescription(context.getString(R.string.context_usage_title));
        ring.setOnClickListener(v -> {
            if (listener != null) {
                listener.onContext();
            }
        });
        addView(ring, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));

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

    private void addAction(Context context, int icon, int description, Runnable action) {
        IconButton button = new IconButton(context, icon);
        button.setIconSizeDp(40, 19);
        button.setIconColor(tokens.textSecondary);
        button.setContentDescription(context.getString(description));
        button.setOnClickListener(v -> action.run());
        addView(button, new LayoutParams(Ui.dp(context, 40), Ui.dp(context, 48)));
    }

    public void setListener(Listener value) {
        listener = value;
    }

    @Override
    protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        projectText.setMaxWidth(Math.max(0,
                width - getPaddingLeft() - getPaddingRight() - Ui.dp(getContext(), 224)));
    }

    public void render(ChatState state, Tokens current) {
        applyTokens(current);
        String label = state.projectLabel.isEmpty()
                ? getContext().getString(R.string.header_project_default) : state.projectLabel;
        projectText.setText(label);
        ring.setPercent(state.contextPercent);
        ring.setContentDescription(getContext().getString(
                R.string.header_context_desc, state.contextPercent));
    }

    /** Restyles in place; no view is recreated, so scroll and focus survive a theme switch. */
    public void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        projectText.setTextColor(current.text);
        ring.applyTokens(current);
    }

    private static final class ContextRing extends android.view.View {
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
