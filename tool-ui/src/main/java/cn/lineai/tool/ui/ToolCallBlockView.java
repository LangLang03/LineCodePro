package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import android.content.Context;
import android.view.View;
import android.widget.LinearLayout;
import cn.lineai.tool.ToolDisplayCategory;

public final class ToolCallBlockView extends LinearLayout {
    private final ToolCallViewFactoryRegistry registry;
    private String lastStructureSignature = "";
    private String lastContentSignature = "";
    private ToolCallCardView childView;
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;

    public ToolCallBlockView(Context context) {
        this(context, ToolCallViewFactoryRegistry.getDefault());
    }

    public ToolCallBlockView(Context context, ToolCallViewFactoryRegistry registry) {
        super(context);
        this.registry = registry;
        setOrientation(VERTICAL);
    }

    public void bind(ToolCall toolCall, ToolResult result) {
        String structure = structureSignature(projectPath, toolCall, result);
        String content = contentSignature(result);
        if (structure.equals(lastStructureSignature) && content.equals(lastContentSignature)) {
            return;
        }
        boolean structureChanged = !structure.equals(lastStructureSignature);
        lastStructureSignature = structure;
        lastContentSignature = content;
        if (!structureChanged) {
            if (childView != null) {
                childView.updateContent(toolCall, result);
            }
            return;
        }
        String name = toolCall == null ? "" : toolCall.getName();
        ToolDisplayCategory category = resolveDisplayCategory(name);
        childView = registry.createView(getContext(), resolveViewClass(name), category);
        if (childView != null) {
            removeAllViews();
            childView.setToolReviewListener(toolReviewListener);
            childView.setProjectPath(projectPath);
            addView((View) childView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            childView.bind(toolCall, result);
        }
    }

    private Class<? extends ToolCallCardView> resolveViewClass(String name) {
        ToolInfoResolver resolver = ToolInfoResolverProvider.getDefault();
        if (resolver != null) {
            cn.lineai.tool.ToolInfo tool = resolver.getToolInfo(name);
            if (tool != null) {
                return tool.getToolCallViewClass();
            }
        }
        return null;
    }

    public void setToolReviewListener(ToolReviewListener listener) {
        toolReviewListener = listener;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallCardView) {
            ((ToolCallCardView) getChildAt(0)).setToolReviewListener(listener);
        }
    }

    public void setProjectPath(String projectPath) {
        this.projectPath = projectPath == null ? "" : projectPath;
        if (getChildCount() > 0 && getChildAt(0) instanceof ToolCallCardView) {
            ((ToolCallCardView) getChildAt(0)).setProjectPath(this.projectPath);
        }
    }

    private ToolDisplayCategory resolveDisplayCategory(String name) {
        return ToolCallUtils.getDisplayCategory(name);
    }

    public static String structureSignature(String projectPath, ToolCall toolCall, ToolResult result) {
        StringBuilder builder = new StringBuilder();
        builder.append(projectPath == null ? "" : projectPath).append('|');
        if (toolCall != null) {
            builder.append(toolCall.getId()).append('|')
                    .append(toolCall.getName()).append('|')
                    .append(toolCall.getArguments());
        }
        builder.append('|');
        if (result != null) {
            builder.append(result.getToolCallId()).append('|')
                    .append(result.getToolName()).append('|')
                    .append(result.isError()).append('|')
                    .append(result.getDiffId()).append('|')
                    .append(result.getReviewState()).append('|')
                    .append(result.getReviewMessage());
        }
        return builder.toString();
    }

    public static String contentSignature(ToolResult result) {
        return result == null ? "" : result.getContent();
    }
}
