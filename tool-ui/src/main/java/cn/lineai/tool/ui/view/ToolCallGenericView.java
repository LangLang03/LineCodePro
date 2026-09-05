package cn.lineai.tool.ui;
import android.content.Context;
import android.graphics.Typeface;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.ui.theme.BoundedScrollView;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;
import java.util.Map;

/** Unknown and extension tools share the same manual disclosure as built-in tools. */
public final class ToolCallGenericView extends BaseToolCallView implements ToolCallCardView, ToolCallExpansion {
    private final String label;
    private Map<String,Boolean> expansion;
    private String key = "";
    private boolean open;
    private ToolCall call;
    private ToolResult result;
    public ToolCallGenericView(Context context, String label) {
        super(context); this.label = label == null ? "" : label;
    }
    @Override public void setExpansionState(Map<String,Boolean> state, String key) {
        expansion = state; this.key = key; open = state != null && Boolean.TRUE.equals(state.get(key));
    }
    @Override public void bind(ToolCall call, ToolResult result) {
        this.call = call; this.result = result; removeAllViews();
        boolean error = result != null && result.isError();
        LinearLayout header = new LinearLayout(getContext()); header.setGravity(Gravity.CENTER_VERTICAL);
        header.setMinimumHeight(LineTheme.dp(getContext(),48));
        LineTheme.padding(header,0,12,0,12); header.setBackground(LineTheme.pressable(getContext()));
        IconButtonView icon = new IconButtonView(getContext(),IconButtonView.MCP);
        icon.setIconColor(error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY); icon.setIconSizeDp(16,16); icon.setClickable(false);
        header.addView(icon,new LayoutParams(LineTheme.dp(getContext(),16),LineTheme.dp(getContext(),16)));
        String name = call == null ? label : call.getName();
        int status = error ? R.string.tool_call_status_failed : isTerminal(result) ? R.string.tool_call_status_done : R.string.tool_call_status_running;
        TextView title = LineTheme.text(getContext(), getContext().getString(status) + "  " + name,14,error ? LineTheme.DANGER : LineTheme.TEXT_SECONDARY,Typeface.NORMAL);
        title.setMaxLines(2); title.setEllipsize(android.text.TextUtils.TruncateAt.END);
        LayoutParams tp = new LayoutParams(0,-2,1); tp.leftMargin=LineTheme.dp(getContext(),10);header.addView(title,tp);
        IconButtonView arrow = new IconButtonView(getContext(),open ? IconButtonView.CHEVRON_DOWN : IconButtonView.CHEVRON_RIGHT);
        arrow.setIconSizeDp(24,14); arrow.setIconColor(LineTheme.TEXT_SECONDARY); arrow.setClickable(false);
        header.addView(arrow,new LayoutParams(LineTheme.dp(getContext(),24),LineTheme.dp(getContext(),24)));
        header.setOnClickListener(v -> {open=!open;if(expansion!=null)expansion.put(key,open);bind(this.call,this.result);});
        addView(header,new LayoutParams(-1,-2));
        if (!open) return;
        LinearLayout content = new LinearLayout(getContext());content.setOrientation(VERTICAL);
        content.setBackground(LineTheme.rounded(getContext(),LineTheme.INPUT_BG,12));LineTheme.padding(content,16,16,16,16);
        String input = ToolCallUtils.prettyJson(ToolCallUtils.parseInput(call));
        if (!"{}".equals(input)) section(content,R.string.tool_call_input,input,LineTheme.TEXT_SECONDARY);
        if (result != null && !result.getContent().isEmpty()) {
            String raw=result.getContent();
            String output=AgentToolResultDisplay.progressPayload(raw)!=null ? AgentToolResultDisplay.displayOutput(raw) : raw;
            section(content,R.string.tool_call_output,output,error?LineTheme.DANGER:LineTheme.TEXT);
        }
        BoundedScrollView scroll = new BoundedScrollView(getContext(),280);scroll.addView(content,new android.widget.ScrollView.LayoutParams(-1,-2));
        addView(scroll,new LayoutParams(-1,-2));
    }
    private void section(LinearLayout parent,int title,String value,int color) {
        TextView heading = LineTheme.text(getContext(),getContext().getString(title),13,LineTheme.TEXT_SECONDARY,Typeface.NORMAL);
        LineTheme.padding(heading,0,8,0,8); parent.addView(heading);
        String preview=value==null?"":value.length()>65536?value.substring(0,65536)+"…":value;
        TextView body=LineTheme.text(getContext(),preview,13,color,Typeface.NORMAL);
        body.setTypeface(Typeface.MONOSPACE);body.setTextIsSelectable(true);body.setLineSpacing(LineTheme.dp(getContext(),6),1);
        parent.addView(body,new LayoutParams(-1,-2));
    }
    @Override public void updateContent(ToolCall call,ToolResult result) {bind(call,result);}
    @Override public void setToolReviewListener(ToolReviewListener listener) { }
    @Override public void setProjectPath(String path) { }
}
