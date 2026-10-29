package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;

/** Shell calls (legacy {@code ToolCallShellView}): runnable header row plus a monospace code card. */
final class ShellToolRenderer implements ToolCardRenderer {

    private static final int MAX_TEXT = 48_000;

    static final ShellToolRenderer INSTANCE = new ShellToolRenderer();

    private ShellToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        String command = arguments.text("command");
        if (command.isEmpty()) command = ToolCardSupport.target(entry, presentation);
        frame.bareHeader(ToolGlyphs.of(presentation.icon),
                ToolCardSupport.headerLabel(frame.context(), entry, command),
                entry.failed, true, entry.running, false);
        if (!frame.expanded()) return;
        String output = entry.result;
        if (output.length() > MAX_TEXT) output = output.substring(0, MAX_TEXT) + "\n\u2026";
        StringBuilder body = new StringBuilder("$ ").append(command);
        if (entry.running && output.isEmpty()) {
            body.append("\n\n").append(frame.context().getString(
                    R.string.tool_call_status_running));
        } else if (!output.isEmpty()) {
            body.append("\n\n").append(output);
        }
        LinearLayout box = frame.codeBox();
        frame.addCodeText(box, body, 240, t -> ToolCardSupport.statusColor(t, entry));
        frame.body.addView(box, frame.fill());
    }
}
