package cn.lineai.ui.component;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import cn.lineai.ui.theme.LineTheme;

public final class MessageActionBarView extends LinearLayout {
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_RIGHT = 1;
    private final IconButtonView copyButton;
    private final IconButtonView recallButton;

    public MessageActionBarView(Context context, int align, boolean recallEnabled) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(align == ALIGN_RIGHT ? Gravity.END : Gravity.START);
        setMinimumHeight(LineTheme.dp(context, 22));

        copyButton = icon(context, IconButtonView.COPY);
        copyButton.setContentDescription("复制消息");
        addView(copyButton, iconParams(context));

        IconButtonView recall = null;
        if (recallEnabled) {
            recall = icon(context, IconButtonView.ROTATE_CCW);
            recall.setContentDescription("撤回消息");
            addView(recall, iconParams(context));
        }
        recallButton = recall;
    }

    public void setListener(Listener listener) {
        copyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCopy();
            }
        });
        if (recallButton != null) {
            recallButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecall();
                }
            });
        }
    }

    public interface Listener {
        void onCopy();

        void onRecall();
    }

    private IconButtonView icon(Context context, int type) {
        IconButtonView icon = new IconButtonView(context, type);
        icon.setIconColor(LineTheme.TEXT_TERTIARY);
        icon.setIconPaddingDp(4, 3, 5, 4);
        icon.setClickable(true);
        return icon;
    }

    private LinearLayout.LayoutParams iconParams(Context context) {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 22));
        params.rightMargin = LineTheme.dp(context, LineTheme.XS);
        return params;
    }
}
