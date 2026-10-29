package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;

/** Image generation/understanding: legacy status row, saved path and result in the code card. */
final class ImageToolRenderer implements ToolCardRenderer {

    static final ImageToolRenderer INSTANCE = new ImageToolRenderer();

    private ImageToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        String prompt = arguments.text("prompt").trim();
        if (prompt.isEmpty()) prompt = ToolCardSupport.target(entry, presentation);
        boolean expandable = !entry.result.trim().isEmpty();
        frame.bareHeader(ToolGlyphs.of(presentation.icon),
                ToolCardSupport.headerLabel(frame.context(), entry, prompt),
                entry.failed, expandable, entry.running, true);
        if (!expandable || !frame.expanded()) return;
        LinearLayout box = frame.codeBox();
        StringBuilder body = new StringBuilder();
        if (!prompt.isEmpty()) body.append(prompt).append("\n\n");
        body.append(entry.result);
        frame.addCodeText(box, body.toString(), 240,
                t -> ToolCardSupport.statusColor(t, entry));
        frame.body.addView(box, frame.fill());
    }
}
