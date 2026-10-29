package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;

/** Memory saves (legacy memory header): status row with the memory title and a bounded code card. */
final class MemoryToolRenderer implements ToolCardRenderer {

    static final MemoryToolRenderer INSTANCE = new MemoryToolRenderer();

    private MemoryToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        String title = arguments.text("title").trim();
        if (title.isEmpty()) title = ToolCardSupport.target(entry, presentation);
        frame.bareHeader(ToolGlyphs.of(presentation.icon),
                ToolCardSupport.headerLabel(frame.context(), entry, title),
                entry.failed, true, entry.running, true);
        if (!frame.expanded()) return;
        StringBuilder body = new StringBuilder();
        String scope = arguments.text("scope").trim();
        if (!scope.isEmpty()) body.append(scope).append('\n');
        String content = arguments.text("content").trim();
        if (!content.isEmpty()) body.append(content);
        if (entry.failed && !entry.result.isEmpty()) {
            if (body.length() > 0) body.append("\n\n");
            body.append(entry.result);
        }
        if (body.length() == 0) body.append(entry.result);
        if (body.length() == 0) return;
        LinearLayout box = frame.codeBox();
        frame.addCodeText(box, body.toString(), 240,
                t -> ToolCardSupport.statusColor(t, entry));
        frame.body.addView(box, frame.fill());
    }
}
