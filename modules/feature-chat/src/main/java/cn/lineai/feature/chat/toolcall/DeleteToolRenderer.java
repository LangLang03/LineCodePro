package cn.lineai.feature.chat.toolcall;

import android.widget.LinearLayout;

import java.util.ArrayList;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;
import cn.lineai.tool.api.ToolPresentation;

/**
 * Delete calls (legacy {@code ToolCallDeleteView}): one status header row, with the reason and
 * affected paths in the expandable code card.
 */
final class DeleteToolRenderer implements ToolCardRenderer {

    private static final String ITEM_SEPARATOR = " \u00b7 ";

    /** Shared stateless instance. */
    static final DeleteToolRenderer INSTANCE = new DeleteToolRenderer();

    private DeleteToolRenderer() {
    }

    /** Renders the status header plus the reason/paths code card when expanded. */
    @Override
    public void render(ToolCardFrame frame, ToolCallEntry entry, ToolPresentation presentation) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        ArrayList<String> paths = paths(arguments);
        StringBuilder label = new StringBuilder(
                ToolCardSupport.headerLabel(frame.context(), entry, presentation.title));
        if (paths.size() > 1) label.append(ITEM_SEPARATOR).append(paths.size());
        frame.bareHeader(ToolGlyphs.of(presentation.icon), label, entry.failed, true,
                entry.running, true);
        if (!frame.expanded()) return;

        StringBuilder body = new StringBuilder();
        String reason = arguments.text("reason").trim();
        if (!reason.isEmpty()) body.append(reason).append("\n\n");
        for (String path : paths) body.append(path).append('\n');
        if (entry.failed && !entry.result.isEmpty()) body.append("\n").append(entry.result);
        if (body.length() == 0) body.append(entry.result);
        if (body.length() == 0) return;
        LinearLayout box = frame.codeBox();
        frame.addCodeText(box, body.toString().trim(), 200,
                t -> ToolCardSupport.statusColor(t, entry));
        frame.body.addView(box, frame.fill());
    }

    /** Affected paths from the "paths" array or the singular path keys, deduplicated. */
    private static ArrayList<String> paths(ToolJson arguments) {
        ArrayList<String> values = new ArrayList<>();
        ToolJsonArray array = arguments.array("paths");
        if (array != null) {
            for (int i = 0; i < array.length(); i++) {
                String value = array.text(i).trim();
                if (!value.isEmpty()) values.add(value);
            }
        }
        for (String key : new String[] {"path", "file_path", "filePath"}) {
            String value = arguments.text(key).trim();
            if (!value.isEmpty() && !values.contains(value)) values.add(value);
        }
        return values;
    }
}
