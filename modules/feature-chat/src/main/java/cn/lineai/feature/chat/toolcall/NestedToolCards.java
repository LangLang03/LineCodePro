package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.tool.api.ToolPresentation;

/** Renders the nested tool calls carried by agent/pipeline progress payloads. */
final class NestedToolCards {

    private NestedToolCards() {
    }

    static void add(ToolCardFrame frame, LinearLayout parent, ToolJsonArray calls) {
        if (calls == null || calls.length() == 0) return;
        for (int i = 0; i < calls.length(); i++) {
            ToolJson item = calls.object(i);
            if (item == null) continue;
            String name = item.text("name");
            if (name.isEmpty()) continue;
            String arguments = item.text("arguments", "{}");
            ToolJson result = item.child("result");
            String content = result == null ? "" : result.text("content");
            boolean error = result != null && result.flag("is_error");
            ToolCallEntry nested = new ToolCallEntry(item.text("id", "nested-" + i), name,
                    arguments, content, error, result == null);
            ToolCallCardView card = new ToolCallCardView(frame.context(), frame.tokens());
            card.setProvider(frame.provider());
            card.setDiffLookup(frame.diffLookup());
            card.bind(nested, frame.tokens());
            frame.addTokenApplier(() -> card.applyTokens(frame.tokens()));
            parent.addView(card, frame.withTop(2));
        }
    }

    /** Resolved presentation for a nested call, falling back to the generic metadata. */
    static ToolPresentation presentation(ToolCardFrame frame, String name) {
        ToolPresentation presentation = frame.provider() == null ? null
                : frame.provider().presentation(name);
        return presentation == null ? ToolPresentation.generic(name) : presentation;
    }
}
