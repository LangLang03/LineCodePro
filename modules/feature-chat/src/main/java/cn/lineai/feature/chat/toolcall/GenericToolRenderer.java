package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;
import android.widget.TextView;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;
import cn.lineai.ui.kit.Icons;
import cn.lineai.ui.kit.Ui;

/**
 * Unknown and MCP tools (legacy {@code ToolCallGenericView}): one 48dp header row with a 16dp MCP
 * glyph, {@code status  name} and a chevron; the body is a bounded code card with an INPUT section
 * and an OUTPUT section separated by a hairline.
 */
final class GenericToolRenderer implements ToolCardRenderer {

    static final GenericToolRenderer INSTANCE = new GenericToolRenderer();

    private GenericToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        boolean hasInput = !ToolCardSupport.prettyJson(arguments).equals("{}");
        boolean hasOutput = !entry.result.trim().isEmpty();
        boolean expandable = hasInput || hasOutput;
        frame.bareHeader(Icons.MCP,
                ToolCardSupport.headerLabel(frame.context(), entry, presentation.title),
                entry.failed, expandable, entry.running, false);
        if (!expandable || !frame.expanded()) return;

        LinearLayout box = frame.codeBox();
        if (hasInput) {
            addSection(frame, box, R.string.tool_call_input,
                    ToolCardSupport.prettyJson(arguments), t -> t.textSecondary, false);
        }
        if (hasOutput) {
            addSection(frame, box, R.string.tool_call_output,
                    ToolCardSupport.displayOutput(entry.result, "linecode_agent_progress",
                            "linecode_agent_ref"),
                    t -> entry.failed ? t.danger : t.textSecondary, hasInput);
        }
        frame.body.addView(frame.bounded(box, 240), frame.fill());
    }

    private void addSection(ToolCardFrame frame, LinearLayout box, int headingRes,
                            String value, ToolCardFrame.Tint tint, boolean dividerAbove) {
        if (dividerAbove) box.addView(frame.divider());
        LinearLayout section = frame.column();
        Ui.padding(section, 14, 12, 14, 12);
        section.addView(frame.inputLabel(frame.context().getString(headingRes)), frame.fill());
        TextView body = frame.mono(value, 13, tint, false);
        body.setTextIsSelectable(true);
        body.setLineSpacing(Ui.dp(frame.context(), 4), 1f);
        section.addView(body, frame.withTop(4));
        box.addView(section, frame.fill());
    }
}
