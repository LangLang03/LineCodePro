package cn.lineai.feature.settings;

import java.util.ArrayList;
import java.util.List;

/**
 * Extracts {@code ##} headings from a tutorial document for its table of contents.
 *
 * <p>Headings inside fenced code blocks are ignored so code samples cannot leak into the
 * navigation list.</p>
 */
final class TutorialSections {

    /** Utility class; not instantiable. */
    private TutorialSections() {
    }

    /**
     * Extracts level-two headings in document order.
     *
     * @param markdown tutorial document text; may be null
     * @return list of heading titles; empty for null or heading-free input
     */
    static List<String> parse(String markdown) {
        List<String> sections = new ArrayList<>();
        if (markdown == null) return sections;
        boolean inCode = false;
        for (String line : markdown.split("\n")) {
            String trimmed = line.trim();
            if (trimmed.startsWith("```")) {
                inCode = !inCode;
                continue;
            }
            if (!inCode && trimmed.startsWith("## ")) {
                String title = trimmed.substring(3).trim();
                if (!title.isEmpty()) sections.add(title);
            }
        }
        return sections;
    }

    /**
     * Shortens a chapter title for chip display by dropping the numbering prefix and any
     * full-width-colon lead-in.
     *
     * @param title full section title
     * @return shortened display title
     */
    static String shortTitle(String title) {
        int dot = title.indexOf('.');
        String rest = dot >= 0 ? title.substring(dot + 1).trim() : title;
        int colon = rest.indexOf('：');
        return colon >= 0 ? rest.substring(colon + 1).trim() : rest;
    }
}
