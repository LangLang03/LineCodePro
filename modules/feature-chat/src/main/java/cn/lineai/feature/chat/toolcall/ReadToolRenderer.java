package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolPresentation;

/**
 * Read-style status row (legacy {@code ToolCallReadView}): list, search, read and web calls are
 * one non-clickable 48dp line with a 16dp glyph and {@code status  target}; only failures add a
 * code box with the error output.
 */
final class ReadToolRenderer implements ToolCardRenderer {

    static final ReadToolRenderer INSTANCE = new ReadToolRenderer();

    private ReadToolRenderer() {
    }

    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        boolean failed = entry.failed;
        String target = ToolCardSupport.target(entry, presentation);
        frame.bareHeader(ToolGlyphs.of(presentation.icon),
                ToolCardSupport.headerLabel(frame.context(), entry, target),
                failed, false, false, true);
        if (failed && !entry.result.isEmpty()) {
            LinearLayout box = frame.codeBox();
            frame.addCodeText(box, entry.result, 240, t -> t.danger);
            frame.body.addView(box, frame.fill());
            frame.showBodyAlways();
        }
    }

    @Override
    public boolean expandable(ToolCallEntry entry, ToolPresentation presentation) {
        return false;
    }
}
