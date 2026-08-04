package cn.lineai.tool.ui;
import cn.lineai.tool.ToolCallCardView;
import cn.lineai.tool.ToolReviewListener;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;
import cn.lineai.ui.theme.IconButtonView;
import cn.lineai.ui.theme.LineTheme;

import android.content.Context;
import android.graphics.Paint;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.TextView;
import cn.lineai.tool.ui.R;
import cn.lineai.model.TodoItem;
import java.util.ArrayList;
import java.util.List;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

public final class ToolCallTodoView extends BaseToolCallView implements ToolCallCardView {
    private final int circleSize;
    private final int circleStroke;
    private final int minRowHeight;
    private List<TodoItem> lastItems;
    private String lastSignature = "";

    public ToolCallTodoView(Context context) {
        super(context);
        circleSize = LineTheme.dp(context, 14);
        circleStroke = LineTheme.dp(context, 2);
        minRowHeight = LineTheme.dp(context, 28);
    }

    @Override
    public void bind(ToolCall toolCall, ToolResult result) {
        List<TodoItem> items = parseItems(toolCall);
        String signature = signature(items);
        if (signature.equals(lastSignature)) {
            return;
        }
        lastSignature = signature;
        lastItems = items;
        rebuild(items);
    }

    private void rebuild(List<TodoItem> items) {
        removeAllViews();
        if (items == null || items.isEmpty()) {
            addEmptyState();
            return;
        }
        LinearLayout list = new LinearLayout(getContext());
        list.setOrientation(VERTICAL);
        LineTheme.padding(list, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.SM);
        for (TodoItem item : items) {
            LinearLayout row = buildRow(item);
            row.setMinimumHeight(minRowHeight);
            list.addView(row, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
        }
        addView(list, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private void addEmptyState() {
        TextView empty = LineTheme.text(getContext(),
                getContext().getString(R.string.tool_call_todo_empty),
                LineTheme.FONT_XS,
                LineTheme.TEXT_TERTIARY,
                Typeface.NORMAL);
        LineTheme.padding(empty, LineTheme.MD, LineTheme.SM, LineTheme.MD, LineTheme.MD);
        addView(empty, new LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT));
    }

    private LinearLayout buildRow(TodoItem item) {
        LinearLayout row = new LinearLayout(getContext());
        row.setOrientation(HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        LineTheme.padding(row, 0, LineTheme.XS, 0, LineTheme.XS);

        View indicator = indicatorView(item);
        row.addView(indicator, new LayoutParams(circleSize, circleSize));

        TextView text = LineTheme.text(getContext(),
                item.getContent(),
                LineTheme.FONT_SM,
                item.isCompleted() ? LineTheme.TEXT_TERTIARY : LineTheme.TEXT,
                Typeface.NORMAL);
        if (item.isCompleted()) {
            text.setPaintFlags(text.getPaintFlags() | Paint.STRIKE_THRU_TEXT_FLAG);
        } else {
            text.setPaintFlags(text.getPaintFlags() & ~Paint.STRIKE_THRU_TEXT_FLAG);
        }
        text.setSingleLine(false);
        text.setMaxLines(2);
        text.setEllipsize(null);
        LayoutParams textParams = new LayoutParams(0, LayoutParams.WRAP_CONTENT, 1f);
        textParams.leftMargin = LineTheme.dp(getContext(), LineTheme.SM);
        row.addView(text, textParams);
        return row;
    }

    private View indicatorView(TodoItem item) {
        if (item.isCompleted()) {
            IconButtonView done = new IconButtonView(getContext(), IconButtonView.CIRCLE_CHECK);
            done.setIconColor(LineTheme.SUCCESS);
            done.setIconSizeDp(circleSize, circleSize - circleStroke);
            done.setClickable(false);
            return done;
        }
        if (item.isInProgress()) {
            return dashedCircle(LineTheme.ACCENT);
        }
        return emptyCircle(LineTheme.TEXT_TERTIARY);
    }

    private View emptyCircle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(android.graphics.Color.TRANSPARENT);
        drawable.setStroke(circleStroke, color);
        View view = new View(getContext());
        view.setBackground(drawable);
        return view;
    }

    private View dashedCircle(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(android.graphics.Color.TRANSPARENT);
        drawable.setStroke(circleStroke, color,
                LineTheme.dp(getContext(), 1.5f),
                LineTheme.dp(getContext(), 1.5f));
        View view = new View(getContext());
        view.setBackground(drawable);
        return view;
    }

    private static List<TodoItem> parseItems(ToolCall toolCall) {
        if (toolCall == null) {
            return new ArrayList<>();
        }
        String arguments = toolCall.getArguments();
        if (arguments == null || arguments.trim().length() == 0) {
            return new ArrayList<>();
        }
        try {
            JSONObject root = new JSONObject(arguments);
            JSONArray array = root.optJSONArray("items");
            if (array == null) {
                return new ArrayList<>();
            }
            ArrayList<TodoItem> items = new ArrayList<>();
            for (int i = 0; i < array.length(); i++) {
                JSONObject obj = array.optJSONObject(i);
                TodoItem item = TodoItem.fromJson(obj);
                if (item != null) {
                    items.add(item);
                }
            }
            return items;
        } catch (JSONException ignored) {
            return new ArrayList<>();
        }
    }

    private static String signature(List<TodoItem> items) {
        if (items == null) {
            return "null";
        }
        StringBuilder builder = new StringBuilder();
        builder.append(items.size());
        for (TodoItem item : items) {
            builder.append('|').append(item.getStatus()).append(':').append(item.getContent());
        }
        return builder.toString();
    }

    public List<TodoItem> getCurrentItems() {
        return lastItems;
    }

    @Override
    public void setToolReviewListener(ToolReviewListener listener) {
        // Todo view does not use tool review
    }

    @Override
    public void setProjectPath(String projectPath) {
        // Todo view does not use project path
    }
}
