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
    private ToolCall lastCall;
    private ToolResult lastResult;
    private String lastStructureProjectPath = "";
    private ToolCallCardView childView;
    private String projectPath = "";
    private ToolReviewListener toolReviewListener;
    private String childIdentity = "";
    private java.util.Map<String, Boolean> expansionState;
    private String expansionKey = "";

    public void setExpansionState(java.util.Map<String, Boolean> state, String key) {
        expansionState = state;
        expansionKey = key;
        if (childView instanceof ToolCallExpansion) ((ToolCallExpansion) childView).setExpansionState(state, key);
    }

    public ToolCallBlockView(Context context) {
        this(context, ToolCallViewFactoryRegistry.getDefault());
    }

    public ToolCallBlockView(Context context, ToolCallViewFactoryRegistry registry) {
        super(context);
        this.registry = registry;
        setOrientation(VERTICAL);
    }

    /**
     * 绑定一次工具调用卡片。
     *
     * <p>{@code ToolCall} / {@code ToolResult} 均不可变，旧实现却每次都把入参全文与结果全文
     * （单条可达 50KB）拼接成签名串；一行包含多个卡片时，滑动与流式刷新的开销会随对话变长
     * 而平方级增长。改为比较实例引用，只对少量结构字段做值比较。
     */
    public void bind(ToolCall toolCall, ToolResult result) {
        boolean sameCall = toolCall == lastCall;
        boolean sameResult = result == lastResult;
        boolean sameProject = projectPath.equals(lastStructureProjectPath);
        if (sameCall && sameResult && sameProject) {
            return;
        }
        // 卡片本身只在“工具名/入参结构”变化时才重建，而这两者都由 ToolCall 实例携带。
        boolean structureChanged = !sameCall || !sameProject || !nonContentFieldsEqual(lastResult, result);
        lastCall = toolCall;
        lastResult = result;
        if (!structureChanged) {
            if (childView != null) {
                childView.updateContent(toolCall, result);
            }
            return;
        }
        lastStructureProjectPath = projectPath;
        String name = toolCall == null ? "" : toolCall.getName();
        ToolDisplayCategory category = resolveDisplayCategory(name);
        String identity = toolCall == null ? "" : toolCall.getId() + ":" + name;
        if (childView != null && childIdentity.equals(identity)) {
            childView.bind(toolCall, result);
            return;
        }
        childIdentity = identity;
        childView = registry.createView(getContext(), resolveViewClass(name), category);
        if (childView != null) {
            removeAllViews();
            if (childView instanceof ToolCallExpansion) ((ToolCallExpansion) childView).setExpansionState(expansionState, expansionKey);
            childView.setToolReviewListener(toolReviewListener);
            childView.setProjectPath(projectPath);
            addView((View) childView, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
            childView.bind(toolCall, result);
        }
    }

    /** 结构签名等同于“除结果正文外的全部字段”，这里直接逐字段比较，不再拼接大段文本。 */
    private static boolean nonContentFieldsEqual(ToolResult left, ToolResult right) {
        if (left == right) {
            return true;
        }
        if (left == null || right == null) {
            return false;
        }
        return left.isError() == right.isError()
                && left.getToolCallId().equals(right.getToolCallId())
                && left.getToolName().equals(right.getToolName())
                && left.getDiffId().equals(right.getDiffId())
                && left.getReviewState().equals(right.getReviewState())
                && left.getReviewMessage().equals(right.getReviewMessage());
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

    /**
     * 工具调用的结构签名（不含结果正文）。
     *
     * <p>{@link #bind(ToolCall, ToolResult)} 已改为直接比较不可变实例引用，不再拼接签名；
     * 本方法保留作为“哪些字段属于结构”的单一口径与测试依据。
     */
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
