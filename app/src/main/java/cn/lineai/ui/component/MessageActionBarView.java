package cn.lineai.ui.component;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.view.Gravity;
import android.widget.LinearLayout;
import cn.lineai.R;

public final class MessageActionBarView extends LinearLayout {
    public static final int ALIGN_LEFT = 0;
    public static final int ALIGN_RIGHT = 1;
    private final IconButtonView copyButton;
    private final IconButtonView quoteButton;
    private final IconButtonView shareButton;
    private final IconButtonView selectButton;
    private final IconButtonView multiSelectButton;
    private final IconButtonView recallButton;

    public MessageActionBarView(Context context, int align, boolean recallEnabled) {
        this(context, align, recallEnabled, false);
    }

    public MessageActionBarView(Context context, int align, boolean recallEnabled, boolean streaming) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(align == ALIGN_RIGHT ? Gravity.END : Gravity.START);
        setMinimumHeight(LineTheme.dp(context, 22));

        copyButton = icon(context, IconButtonView.COPY);
        copyButton.setContentDescription(context.getString(R.string.message_action_copy_desc));
        addView(copyButton, iconParams(context));

        quoteButton = icon(context, IconButtonView.QUOTE);
        quoteButton.setContentDescription(context.getString(R.string.message_action_quote_desc));
        addView(quoteButton, iconParams(context));

        shareButton = icon(context, IconButtonView.SHARE);
        shareButton.setContentDescription(context.getString(R.string.message_action_share_desc));
        addView(shareButton, iconParams(context));

        selectButton = icon(context, IconButtonView.TEXT_CURSOR);
        selectButton.setContentDescription(context.getString(R.string.message_action_select_desc));
        addView(selectButton, iconParams(context));

        multiSelectButton = icon(context, IconButtonView.CHECK_SQUARE);
        multiSelectButton.setContentDescription(context.getString(R.string.message_action_multi_select_desc));
        addView(multiSelectButton, iconParams(context));

        IconButtonView recall = null;
        if (recallEnabled) {
            recall = icon(context, IconButtonView.ROTATE_CCW);
            recall.setContentDescription(context.getString(R.string.message_action_recall_desc));
            addView(recall, iconParams(context));
        }
        recallButton = recall;

        if (streaming) {
            setActionsVisible(false);
        }
    }

    public void setActionListener(ActionListener listener) {
        copyButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onCopy();
            }
        });
        quoteButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onQuote();
            }
        });
        shareButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onShare();
            }
        });
    }

    public void setSelectListener(SelectListener listener) {
        selectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onSelect();
            }
        });
        multiSelectButton.setOnClickListener(v -> {
            if (listener != null) {
                listener.onMultiSelect();
            }
        });
    }

    public void setRecallListener(RecallListener listener) {
        if (recallButton != null) {
            recallButton.setOnClickListener(v -> {
                if (listener != null) {
                    listener.onRecall();
                }
            });
        }
    }

    public void setActionsVisible(boolean visible) {
        int visibility = visible ? VISIBLE : GONE;
        quoteButton.setVisibility(visibility);
        shareButton.setVisibility(visibility);
        selectButton.setVisibility(visibility);
        multiSelectButton.setVisibility(visibility);
    }

    public interface ActionListener {
        void onCopy();

        void onQuote();

        void onShare();
    }

    public interface SelectListener {
        void onSelect();

        void onMultiSelect();
    }

    public interface RecallListener {
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
