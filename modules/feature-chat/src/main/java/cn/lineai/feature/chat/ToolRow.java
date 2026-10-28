package cn.lineai.feature.chat;

import android.content.Context;
import android.graphics.BitmapFactory;
import android.graphics.Typeface;
import android.text.TextUtils;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.core.model.Message;
import cn.lineai.tool.api.ToolIcon;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.IconButton;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;
import cn.lineai.ui.tokens.Tokens;

/** Compact result card for one tool execution. */
final class ToolRow extends LinearLayout {

    private final LinearLayout card;
    private final IconButton icon;
    private final TextView name;
    private final TextView status;
    private final TextView body;
    private final LinearLayout images;
    private final TextView toggle;
    private Tokens tokens;
    private boolean expanded;
    private boolean collapsible;
    private boolean autoExpand;
    private boolean failed;
    private String lastImageKey = "";

    ToolRow(Context context, Tokens tokens) {
        super(context);
        this.tokens = tokens;
        setOrientation(VERTICAL);
        Ui.padding(this, 16, 0, 16, 10);
        setBackgroundColor(tokens.bg);

        card = new LinearLayout(context);
        card.setOrientation(VERTICAL);
        Ui.padding(card, 12, 10, 12, 10);
        addView(card, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        LinearLayout header = new LinearLayout(context);
        header.setGravity(Gravity.CENTER_VERTICAL);
        icon = new IconButton(context, Icons.WRENCH);
        icon.setIconSizeDp(28, 15);
        icon.setClickable(false);
        header.addView(icon, new LayoutParams(Ui.dp(context, 28), Ui.dp(context, 28)));

        name = Ui.medium(context, "", Tokens.FONT_SM, tokens.text);
        name.setSingleLine(true);
        name.setEllipsize(TextUtils.TruncateAt.END);
        LayoutParams nameParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        nameParams.leftMargin = Ui.dp(context, 10);
        nameParams.rightMargin = Ui.dp(context, 8);
        header.addView(name, nameParams);

        status = Ui.text(context, "", Tokens.FONT_XS, tokens.textTertiary, Typeface.NORMAL);
        header.addView(status, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        card.addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        images = new LinearLayout(context);
        images.setOrientation(LinearLayout.HORIZONTAL);
        images.setVisibility(GONE);
        LinearLayout.LayoutParams imagesParams = new LinearLayout.LayoutParams(
                LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        imagesParams.topMargin = Ui.dp(context, 8);
        card.addView(images, imagesParams);

        body = Ui.text(context, "", Tokens.FONT_XS, tokens.textSecondary, Typeface.NORMAL);
        body.setTypeface(Typeface.MONOSPACE);
        body.setLineSpacing(Ui.dp(context, 3), 1f);
        body.setMaxLines(3);
        body.setEllipsize(TextUtils.TruncateAt.END);
        body.setClickable(true);
        body.setOnClickListener(v -> toggle());
        LayoutParams bodyParams = new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT);
        bodyParams.topMargin = Ui.dp(context, 8);
        card.addView(body, bodyParams);

        toggle = Ui.text(context, context.getString(R.string.chat_tool_show_more),
                Tokens.FONT_XS, tokens.accent, Typeface.NORMAL);
        toggle.setClickable(true);
        toggle.setFocusable(true);
        Ui.padding(toggle, 0, 6, 0, 0);
        toggle.setOnClickListener(v -> toggle());
        card.addView(toggle, new LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        applyTokens(tokens);
    }

    void bind(Message message, Tokens current, ToolPresentationProvider provider) {
        tokens = current;
        String content = message.plainText();
        failed = message.status == Message.Status.FAILED;
        ToolPresentation presentation = provider == null ? null : provider.presentation(message.toolName);
        if (presentation == null) presentation = ToolPresentation.generic(message.toolName, null);
        name.setText(presentation.title);
        icon.setIconType(iconFor(presentation.icon));
        status.setText(failed ? getContext().getString(R.string.chat_tool_failed) : "");
        status.setVisibility(failed ? VISIBLE : GONE);
        if (!content.equals(body.getText().toString())) {
            expanded = autoExpand;
            body.setText(content);
        }
        renderImages(ImagePaths.of(message));
        collapsible = content.length() > 220 || countLines(content) > 3;
        body.setVisibility(content.isEmpty() ? GONE : VISIBLE);
        toggle.setVisibility(collapsible ? VISIBLE : GONE);
        applyExpandedState();
        applyTokens(current);
    }

    void preferences(boolean value) {
        autoExpand = value;
        if (value && collapsible) expanded = true;
        applyExpandedState();
    }

    void applyTokens(Tokens current) {
        tokens = current;
        setBackgroundColor(current.bg);
        card.setBackground(Ui.roundedStroke(getContext(), current.surfaceElevated, 12, current.borderLight));
        icon.setIconColor(current.accent);
        icon.setBackground(Ui.rounded(getContext(), current.accentMuted, 14));
        name.setTextColor(current.text);
        status.setTextColor(failed ? current.danger : current.textTertiary);
        body.setTextColor(current.textSecondary);
        toggle.setTextColor(current.accent);
    }

    private void toggle() {
        if (!collapsible) {
            return;
        }
        expanded = !expanded;
        applyExpandedState();
    }

    private void applyExpandedState() {
        body.setMaxLines(expanded || !collapsible ? Integer.MAX_VALUE : 3);
        body.setEllipsize(expanded || !collapsible ? null : TextUtils.TruncateAt.END);
        toggle.setText(getContext().getString(expanded
                ? R.string.chat_tool_show_less : R.string.chat_tool_show_more));
    }

    private static int countLines(String value) {
        int lines = 1;
        for (int i = 0; i < value.length(); i++) {
            if (value.charAt(i) == '\n') lines++;
        }
        return lines;
    }

    private void renderImages(java.util.List<String> paths) {
        StringBuilder key = new StringBuilder();
        for (String path : paths) key.append(path).append('\u0001');
        if (key.toString().equals(lastImageKey)) return;
        lastImageKey = key.toString();
        images.removeAllViews();
        for (String path : paths) {
            android.graphics.Bitmap bitmap = BitmapFactory.decodeFile(path);
            if (bitmap == null) continue;
            ImageView view = new ImageView(getContext());
            view.setImageBitmap(bitmap);
            view.setScaleType(ImageView.ScaleType.CENTER_CROP);
            view.setBackground(Ui.rounded(getContext(), tokens.surfaceLight, 10));
            view.setClipToOutline(true);
            LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                    Ui.dp(getContext(), 180), Ui.dp(getContext(), 140));
            params.rightMargin = Ui.dp(getContext(), 6);
            images.addView(view, params);
        }
        images.setVisibility(paths.isEmpty() ? GONE : VISIBLE);
    }

    private static int iconFor(ToolIcon iconType) {
        switch (iconType) {
            case READ:
                return Icons.FILE_TEXT;
            case WRITE:
                return Icons.FILE_PLUS;
            case EDIT:
                return Icons.FILE_PEN_LINE;
            case DELETE:
                return Icons.TRASH_2;
            case LIST:
                return Icons.FOLDER;
            case SEARCH:
                return Icons.SEARCH;
            case TERMINAL:
                return Icons.TERMINAL;
            case MCP:
                return Icons.MCP;
            case IMAGE:
                return Icons.IMAGE;
            case SPARKLES:
                return Icons.SPARKLES;
            case TODO:
                return Icons.SCROLL_TEXT;
            case AGENT:
                return Icons.BRAIN;
            case MEMORY:
                return Icons.BOOK_OPEN;
            case PHONE:
                return Icons.SMARTPHONE;
            case GENERIC:
            default:
                return Icons.WRENCH;
        }
    }
}
