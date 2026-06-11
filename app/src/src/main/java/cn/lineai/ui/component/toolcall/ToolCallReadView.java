package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;
import cn.lineai.ui.component.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import org.json.JSONObject;

public final class ToolCallReadView extends LinearLayout {
    private String projectPath = "";

    public ToolCallReadView(Context context) {
        super(context);
        setOrientation(VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        removeAllViews();
        String name = toolCall == null ? "" : toolCall.getName();
        JSONObject input = ToolCallUtils.parseInput(toolCall);
        String label = ToolCallUtils.displayInputLabel(name, input, projectPath);
        boolean complete = result != null;
        boolean error = result != null && result.isError();
        String actionLabel = actionLabel(name);

        LinearLayout header = new LinearLayout(getContext());
        header.setOrientation(HORIZONTAL);
        header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(getContext(), 42));
        LineTheme.padding(header, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        IconButtonView icon = new IconButtonView(getContext(), iconFor(name));
        icon.setIconColor(error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY);
        icon.setIconSizeDp(26, 13);
        icon.setClickable(false);
        icon.setBackground(LineTheme.roundedStroke(getContext(), android.graphics.Color.TRANSPARENT, 8,
                error ? LineTheme.DANGER_MUTED_2 : LineTheme.CODE_BORDER));
        header.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 26), LineTheme.dp(getContext(), 26)));

        LinearLayout textBlock = new LinearLayout(getContext());
        textBlock.setOrientation(VERTICAL);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        textParams.rightMargin = LineTheme.dp(getContext(), LineTheme.SM);
        header.addView(textBlock, textParams);

        TextView action = LineTheme.text(getContext(), actionLabel, 10, error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY, Typeface.BOLD);
        textBlock.addView(action, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        TextView path = LineTheme.text(getContext(), label, LineTheme.FONT_SM, error ? LineTheme.DANGER : LineTheme.TEXT, Typeface.NORMAL);
        path.setTypeface(Typeface.MONOSPACE);
        path.setSingleLine(true);
        path.setHorizontallyScrolling(true);
        HorizontalScrollView pathScroll = horizontalPathScroll();
        pathScroll.addView(path, new HorizontalScrollView.LayoutParams(LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        textBlock.addView(pathScroll, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        View status = statusView(complete, error);
        header.addView(status, new LayoutParams(LineTheme.dp(getContext(), 18), LineTheme.dp(getContext(), 18)));
        addView(header, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));

        if (error && result.getContent().length() > 0) {
            addError(result.getContent());
        }
    }

    private String actionLabel(String name) {
        if ("image_understanding".equals(name)) return "图片理解";
        if ("web_search".equals(name)) return "搜索";
        if ("web_fetch".equals(name)) return "抓取";
        if ("glob".equals(name)) return "匹配";
        if ("list_dir".equals(name)) return "列目录";
        return "读取";
    }

    private int iconFor(String name) {
        if ("glob".equals(name) || "web_search".equals(name)) {
            return IconButtonView.SEARCH;
        }
        if ("web_fetch".equals(name)) {
            return IconButtonView.GLOBE;
        }
        if ("list_dir".equals(name)) {
            return IconButtonView.FOLDER_OPEN;
        }
        if ("image_understanding".equals(name)) {
            return IconButtonView.PAINTBRUSH;
        }
        return IconButtonView.EXPAND;
    }

    private HorizontalScrollView horizontalPathScroll() {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        return scroll;
    }

    private View statusView(boolean complete, boolean error) {
        if (!complete) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            return bar;
        }
        IconButtonView icon = new IconButtonView(getContext(), error ? IconButtonView.CLOSE : IconButtonView.CHECK);
        icon.setIconColor(error ? LineTheme.DANGER : LineTheme.SUCCESS);
        icon.setIconSizeDp(18, 13);
        icon.setClickable(false);
        return icon;
    }

    private void addError(String text) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LineTheme.padding(row, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);
        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.CLOSE);
        icon.setIconColor(LineTheme.DANGER);
        icon.setIconSizeDp(14, 12);
        icon.setClickable(false);
        row.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 14), LineTheme.dp(getContext(), 14)));
        TextView error = LineTheme.text(getContext(), text, LineTheme.FONT_XS, LineTheme.DANGER, Typeface.NORMAL);
        LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        params.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
        row.addView(error, params);
        addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }
}
