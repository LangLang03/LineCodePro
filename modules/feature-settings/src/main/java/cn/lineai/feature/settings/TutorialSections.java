package cn.lineai.feature.settings;

import java.util.ArrayList;
import java.util.List;

/** Extracts `## ` headings for the tutorial table of contents. */
final class TutorialSections {

    private TutorialSections() {
    }

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

    static String shortTitle(String title) {
        int dot = title.indexOf('.');
        String rest = dot >= 0 ? title.substring(dot + 1).trim() : title;
        int colon = rest.indexOf('：');
        return colon >= 0 ? rest.substring(colon + 1).trim() : rest;
    }
}
