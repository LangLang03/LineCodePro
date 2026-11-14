package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolPresentation;
import cn.lineai.ui.i18n.R;

/**
 * Compact agent-family cards in the file/shell style: one status row with the bot glyph and a
 * human summary, then the meaningful content when expanded — the task for a spawn, the
 * message for a send, the wait window for a wait, and a readable session list for a list.
 * Provider identifiers and raw result JSON stay out of the display; what the model receives
 * and what the user reads are deliberately different.
 */
final class AgentToolRenderers {

    /** Output characters kept before truncation. */
    private static final int MAX_TEXT = 48_000;

    private AgentToolRenderers() {
    }

    /** Spawn card: task summary in the header, the full task/context as the body. */
    static final ToolCardRenderer SPAWN = new ToolCardRenderer() {
        @Override public void render(ToolCardFrame frame, ToolCallEntry entry,
                                     ToolPresentation presentation) {
            ToolJson arguments = ToolCardSupport.arguments(entry);
            String task = arguments.text("task").trim();
            if (task.isEmpty()) task = ToolCardSupport.target(entry, presentation);
            frame.bareHeader(ToolGlyphs.of(presentation.icon),
                    ToolCardSupport.headerLabel(frame.context(), entry,
                            ToolCardSupport.firstLine(task)),
                    entry.failed, true, entry.running, true);
            if (!frame.expanded()) return;
            String context = arguments.text("context").trim();
            StringBuilder body = new StringBuilder(task);
            if (!context.isEmpty()) {
                body.append("\n\n").append(frame.context().getString(
                        R.string.tool_call_agent_context_label))
                        .append('\n').append(context);
            }
            if (body.length() == 0) {
                body.append(AgentText.describe(frame.context(), entry));
            }
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, AgentText.cap(body.toString()), 240,
                    t -> ToolCardSupport.statusColor(t, entry));
            frame.body.addView(box, frame.fill());
        }
    };

    /** Send card: recipient in the header, the message text as the body. */
    static final ToolCardRenderer SEND = new ToolCardRenderer() {
        @Override public void render(ToolCardFrame frame, ToolCallEntry entry,
                                     ToolPresentation presentation) {
            ToolJson arguments = ToolCardSupport.arguments(entry);
            String agentId = arguments.text("agent_id").trim();
            String message = arguments.text("message").trim();
            String header = agentId.isEmpty() ? presentation.action
                    : frame.context().getString(R.string.tool_call_agent_send_to, agentId);
            frame.bareHeader(ToolGlyphs.of(presentation.icon),
                    ToolCardSupport.headerLabel(frame.context(), entry, header),
                    entry.failed, true, entry.running, true);
            if (!frame.expanded()) return;
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, AgentText.cap(message), 200,
                    t -> ToolCardSupport.statusColor(t, entry));
            frame.body.addView(box, frame.fill());
        }
    };

    /** Wait card: the wait window in the header, readable session states in the body. */
    static final ToolCardRenderer WAIT = new ToolCardRenderer() {
        @Override public void render(ToolCardFrame frame, ToolCallEntry entry,
                                     ToolPresentation presentation) {
            String window = ToolCardSupport.firstLine(presentation.action);
            frame.bareHeader(ToolGlyphs.of(presentation.icon),
                    ToolCardSupport.headerLabel(frame.context(), entry, window),
                    entry.failed, true, entry.running, true);
            if (!frame.expanded()) return;
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, AgentText.sessions(frame.context(), entry.result), 200,
                    t -> ToolCardSupport.statusColor(t, entry));
            frame.body.addView(box, frame.fill());
        }
    };

    /** List card: session count in the header, readable session states in the body. */
    static final ToolCardRenderer LIST = new ToolCardRenderer() {
        @Override public void render(ToolCardFrame frame, ToolCallEntry entry,
                                     ToolPresentation presentation) {
            String sessions = AgentText.sessions(frame.context(), entry.result);
            frame.bareHeader(ToolGlyphs.of(presentation.icon),
                    ToolCardSupport.headerLabel(frame.context(), entry,
                            frame.context().getString(R.string.tool_call_agent_list_summary,
                                    AgentText.sessionCount(entry.result))),
                    entry.failed, true, entry.running, true);
            if (!frame.expanded()) return;
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, sessions, 200, t -> ToolCardSupport.statusColor(t, entry));
            frame.body.addView(box, frame.fill());
        }
    };
}
