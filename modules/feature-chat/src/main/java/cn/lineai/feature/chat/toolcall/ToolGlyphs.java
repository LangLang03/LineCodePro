package cn.lineai.feature.chat.toolcall;

import cn.lineai.tool.api.ToolIcon;
import cn.lineai.ui.kit.Icons;

/** Maps the semantic {@link ToolIcon} to a concrete lucide glyph. */
final class ToolGlyphs {

    private ToolGlyphs() {
    }

    static int of(ToolIcon icon) {
        if (icon == null) return Icons.WRENCH;
        switch (icon) {
            case READ:
                return Icons.FILE_TEXT;
            case WRITE:
                return Icons.FILE_PLUS;
            case EDIT:
                return Icons.FILE_PEN_LINE;
            case DIFF:
                return Icons.GIT_COMPARE;
            case DELETE:
                return Icons.TRASH_2;
            case LIST:
                return Icons.FOLDER;
            case SEARCH:
                return Icons.SEARCH;
            case FETCH:
                return Icons.GLOBE;
            case TERMINAL:
                return Icons.TERMINAL;
            case MCP:
                return Icons.MCP;
            case IMAGE:
                return Icons.IMAGE;
            case SPARKLES:
                return Icons.SPARKLES;
            case TODO:
                return Icons.SCROLL_TEXT;
            case AGENT:
                return Icons.BOT;
            case PIPELINE:
                return Icons.GIT_BRANCH;
            case MEMORY:
                return Icons.BOOK_OPEN;
            case PHONE:
                return Icons.SMARTPHONE;
            case GENERIC:
            default:
                return Icons.WRENCH;
        }
    }
}
