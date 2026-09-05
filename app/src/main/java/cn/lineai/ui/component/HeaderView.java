package cn.lineai.ui.component;

import android.content.Context;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.R;
import cn.lineai.model.ChatUiState;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

/** Conversation navigation and the current workspace selector. */
public final class HeaderView extends LinearLayout {
    public interface Listener {
        void onMenuClick();
        void onProjectClick();
        void onPermissionClick();
        void onNewConversationClick();
        void onMoreClick();
    }
    private Listener listener;
    private final LinearLayout brand;
    private final TextView projectText;

    public HeaderView(Context context) {
        super(context);
        setOrientation(HORIZONTAL);
        setGravity(Gravity.CENTER_VERTICAL);
        setBackgroundColor(LineTheme.BG);
        setMinimumHeight(LineTheme.dp(context, 56));
        LineTheme.padding(this, 4, 2, 8, 2);
        brand = new LinearLayout(context);
        brand.setGravity(Gravity.CENTER_VERTICAL);
        brand.setMinimumHeight(LineTheme.dp(context, 48));
        brand.setFocusable(true);
        brand.setOnClickListener(v -> { if (listener != null) listener.onProjectClick(); });
        IconButtonView menu = new IconButtonView(context, IconButtonView.MENU);
        menu.setIconColor(LineTheme.TEXT_SECONDARY);
        menu.setIconSizeDp(40, 19);
        menu.setContentDescription(context.getString(R.string.header_menu_desc));
        menu.setOnClickListener(v -> { if (listener != null) listener.onMenuClick(); });
        addView(menu, new LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 48)));
        projectText = LineTheme.textMedium(context, context.getString(R.string.header_project_default), 16, LineTheme.TEXT);
        projectText.setSingleLine(true);
        projectText.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams titleParams = new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT);
        brand.addView(projectText, titleParams);
        IconButtonView chevron = new IconButtonView(context, IconButtonView.CHEVRON_DOWN);
        chevron.setIconSizeDp(24, 14);
        chevron.setIconColor(LineTheme.TEXT_SECONDARY);
        chevron.setClickable(false);
        chevron.setImportantForAccessibility(IMPORTANT_FOR_ACCESSIBILITY_NO);
        brand.addView(chevron, new LayoutParams(LineTheme.dp(context, 24), LineTheme.dp(context, 32)));
        addView(brand, new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1));
        IconButtonView permissions = new IconButtonView(context, IconButtonView.SHIELD);
        permissions.setIconSizeDp(40, 19);
        permissions.setIconColor(LineTheme.TEXT_SECONDARY);
        permissions.setContentDescription(context.getString(R.string.header_permission_desc));
        permissions.setOnClickListener(v -> { if (listener != null) listener.onPermissionClick(); });
        addView(permissions, new LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 48)));
        IconButtonView create = new IconButtonView(context, IconButtonView.PLUS);
        create.setIconSizeDp(40, 19);
        create.setIconColor(LineTheme.TEXT_SECONDARY);
        create.setContentDescription(context.getString(R.string.header_new_conversation_desc));
        create.setOnClickListener(v -> { if (listener != null) listener.onNewConversationClick(); });
        addView(create, new LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 48)));
        IconButtonView more = new IconButtonView(context, IconButtonView.MORE);
        more.setIconSizeDp(40, 19);
        more.setIconColor(LineTheme.TEXT_SECONDARY);
        more.setContentDescription(context.getString(R.string.chat_context_more));
        more.setOnClickListener(v -> { if (listener != null) listener.onMoreClick(); });
        addView(more, new LayoutParams(LineTheme.dp(context, 40), LineTheme.dp(context, 48)));
    }
    public void setListener(Listener listener) { this.listener = listener; }
    @Override protected void onSizeChanged(int width, int height, int oldWidth, int oldHeight) {
        super.onSizeChanged(width, height, oldWidth, oldHeight);
        projectText.setMaxWidth(Math.max(0, width - getPaddingLeft() - getPaddingRight() - LineTheme.dp(getContext(), 184)));
    }
    public void render(ChatUiState state) {
        String label = state.getProjectLabel();
        if (label == null || label.isEmpty()) label = getContext().getString(R.string.header_project_default);
        projectText.setText(label);
        brand.setContentDescription(label);
    }
}
