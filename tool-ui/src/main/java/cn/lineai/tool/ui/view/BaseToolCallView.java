package cn.lineai.tool.ui;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.view.View;
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;

/**
 * 抽象基类，集中管理工具调用卡片的公共样式与辅助视图构建逻辑。
 * 子类继承后无需重复设置方向与背景，并可直接复用路径滚动、状态图标、消息行等通用方法。
 */
public abstract class BaseToolCallView extends LinearLayout {

    /**
     * 工具调用结束后的最终 UI 状态。
     * <p>用于简化进度圈逻辑：工具调用结束就根据结果直接决定状态，不再依赖各种中间回调。</p>
     * <ul>
     *   <li>{@link #RUNNING} 仍在运行或等待确认，需要显示进度圈</li>
     *   <li>{@link #SUCCESS} 成功完成，去掉进度圈</li>
     *   <li>{@link #FAILED} 失败，显示失败图标</li>
     *   <li>{@link #UNKNOWN} 未知情况，显示未知图标</li>
     * </ul>
     */
    public enum TerminalStatus {
        RUNNING,
        SUCCESS,
        FAILED,
        UNKNOWN
    }

    public BaseToolCallView(Context context) {
        super(context);
        setOrientation(LinearLayout.VERTICAL);
        setBackground(LineTheme.roundedStroke(context, LineTheme.CODE_BG, 8, LineTheme.CODE_BORDER));
    }

    /**
     * 根据工具调用的最终结果直接决定 UI 状态。
     * <p>判定规则：</p>
     * <ul>
     *   <li>result 为 null 或 reviewState 仍是中间状态（running/pending）→ {@link TerminalStatus#RUNNING}</li>
     *   <li>result.isError() == true → {@link TerminalStatus#FAILED}</li>
     *   <li>result.isError() == false 且 content 非空 → {@link TerminalStatus#SUCCESS}</li>
     *   <li>其它（content 为空且未出错）→ {@link TerminalStatus#UNKNOWN}</li>
     * </ul>
     */
    protected static TerminalStatus computeTerminalStatus(ToolResult result) {
        if (result == null) {
            return TerminalStatus.RUNNING;
        }
        String reviewState = result.getReviewState();
        if ("running".equals(reviewState) || "pending".equals(reviewState)) {
            return TerminalStatus.RUNNING;
        }
        if (result.isError()) {
            return TerminalStatus.FAILED;
        }
        if (result.getContent().trim().length() > 0) {
            return TerminalStatus.SUCCESS;
        }
        return TerminalStatus.UNKNOWN;
    }

    /**
     * 判断工具调用是否已经结束（不再显示进度圈）。
     */
    protected static boolean isTerminal(ToolResult result) {
        return computeTerminalStatus(result) != TerminalStatus.RUNNING;
    }

    /**
     * 构建一个禁用横向滚动条的 HorizontalScrollView，并将 pathView 装入其中。
     * 用于在卡片头部展示可能超长的文件路径。
     */
    protected HorizontalScrollView horizontalPathScroll(TextView pathView) {
        HorizontalScrollView scroll = new HorizontalScrollView(getContext());
        scroll.setHorizontalScrollBarEnabled(false);
        scroll.addView(pathView, new HorizontalScrollView.LayoutParams(
                LayoutParams.WRAP_CONTENT, LayoutParams.WRAP_CONTENT));
        return scroll;
    }

    /**
     * 返回状态视图：running 时显示不确定进度条，完成时显示 CHECK 图标。
     * 子类如需区分成功/失败图标或自定义颜色，可在调用后调整返回的 IconButtonView。
     */
    protected View statusView(boolean running) {
        if (running) {
            ProgressBar bar = new ProgressBar(getContext());
            bar.setIndeterminate(true);
            return bar;
        }
        IconButtonView icon = new IconButtonView(getContext(), IconButtonView.CHECK);
        icon.setIconColor(LineTheme.SUCCESS);
        icon.setIconSizeDp(18, 13);
        icon.setClickable(false);
        return icon;
    }

    /**
     * 向 parent 追加一条消息行：顶部 1px 分割线 + 横向布局（图标 + 文本）。
     * 用于在卡片底部展示错误信息或运行中输出。返回创建的 message TextView。
     */
    protected TextView addMessageRow(LinearLayout parent, int iconType, String text, int color) {
        return addMessageRow(parent, iconType, text, color, 0);
    }

    protected TextView addMessageRow(LinearLayout parent, int iconType, String text, int color, int maxHeightDp) {
        View divider = new View(getContext());
        divider.setBackgroundColor(LineTheme.CODE_BORDER);
        parent.addView(divider, new LayoutParams(LayoutParams.MATCH_PARENT, 1));

        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.TOP);
        LineTheme.padding(row, LineTheme.SM, LineTheme.XS, LineTheme.SM, LineTheme.XS);

        IconButtonView icon = new IconButtonView(getContext(), iconType);
        icon.setIconColor(color);
        icon.setIconSizeDp(14, 12);
        icon.setClickable(false);
        row.addView(icon, new LayoutParams(LineTheme.dp(getContext(), 14), LineTheme.dp(getContext(), 14)));

        TextView message = LineTheme.text(getContext(), text, LineTheme.FONT_XS, color, Typeface.NORMAL);
        if (maxHeightDp > 0) {
            BoundedScrollView scroll = new BoundedScrollView(getContext(), maxHeightDp);
            scroll.setFillViewport(false);
            scroll.addView(message, new android.widget.ScrollView.LayoutParams(
                    LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            LayoutParams scrollParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            scrollParams.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
            row.addView(scroll, scrollParams);
        } else {
            LayoutParams params = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
            params.leftMargin = LineTheme.dp(getContext(), LineTheme.XS);
            row.addView(message, params);
        }
        parent.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        return message;
    }
}
