package cn.lineai.ui.component.toolcall;

import android.content.Context;
import android.widget.LinearLayout;
import cn.lineai.tool.ToolCall;
import cn.lineai.tool.ToolResult;

public final class ToolCallBlockView extends LinearLayout {
    private String lastSignature = "";
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;

    public ToolCallBlockView(Context context) {
        super(context);
        setOrientation(VERTICAL);
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        String signature = signature(toolCall, result);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        String name = toolCall == null ? "" : toolCall.getName();
        if (ToolCallUtils.isShellTool(name)) {
            ToolCallShellView view;
            if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallShellView) {
                view = (ToolCallShellView) getChildAt(0);
            } else {
                removeAllViews();
                view = new ToolCallShellView(getContext());
                view.setToolReviewListener(toolReviewListener);
                addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            }
            view.bind(toolCall, result);
            return;
        }
        removeAllViews();
        if (ToolCallUtils.isAgentTool(name)) {
            ToolCallAgentView view = new ToolCallAgentView(getContext());
            view.setToolReviewListener(toolReviewListener);
            view.setProjectPath(projectPath);
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        if (ToolCallUtils.isAgentPipelineTool(name)) {
            ToolCallAgentPipelineView view = new ToolCallAgentPipelineView(getContext());
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        if (ToolCallUtils.isReadTool(name)) {
            ToolCallReadView view = new ToolCallReadView(getContext());
            view.setProjectPath(projectPath);
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        if (ToolCallUtils.isWriteTool(name)) {
            ToolCallWriteView view = new ToolCallWriteView(getContext());
            view.setToolReviewListener(toolReviewListener);
            view.setProjectPath(projectPath);
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        if (ToolCallUtils.isDeleteTool(name)) {
            ToolCallDeleteView view = new ToolCallDeleteView(getContext());
            view.setToolReviewListener(toolReviewListener);
            view.setProjectPath(projectPath);
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        if (ToolCallUtils.isHttpTool(name)) {
            ToolCallGenericView view = new ToolCallGenericView(getContext(), "HTTP 服务");
            view.bind(toolCall, result);
            addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            return;
        }
        ToolCallGenericView view = new ToolCallGenericView(getContext(), "MCP 调用");
        view.bind(toolCall, result);
        addView(view, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallWriteView) {
            ((ToolCallWriteView) getChildAt(0)).setToolReviewListener(listener);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallDeleteView) {
            ((ToolCallDeleteView) getChildAt(0)).setToolReviewListener(listener);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallAgentView) {
            ((ToolCallAgentView) getChildAt(0)).setToolReviewListener(listener);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallShellView) {
            ((ToolCallShellView) getChildAt(0)).setToolReviewListener(listener);
        }
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallWriteView) {
            ((ToolCallWriteView) getChildAt(0)).setProjectPath(this.projectPath);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallReadView) {
            ((ToolCallReadView) getChildAt(0)).setProjectPath(this.projectPath);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallDeleteView) {
            ((ToolCallDeleteView) getChildAt(0)).setProjectPath(this.projectPath);
        } else if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallAgentView) {
            ((ToolCallAgentView) getChildAt(0)).setProjectPath(this.projectPath);
        }
    }

    private String signature(ToolCall toolCall, ToolResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(projectPath).append('|');
        if (toolCall != null) {
            builder.append(toolCall.getId()).append('|')
                    .append(toolCall.getName()).append('|')
                    .append(toolCall.getArguments());
        }
        builder.append('|');
        if (result != null) {
            builder.append(result.getToolCallId()).append('|')
                    .append(result.getToolName()).append('|')
                    .append(result.getContent()).append('|')
                    .append(result.isError()).append('|')
                    .append(result.getDiffId()).append('|')
                    .append(result.getReviewState()).append('|')
                    .append(result.getReviewMessage());
        }
        return builder.toString();
    }
}
