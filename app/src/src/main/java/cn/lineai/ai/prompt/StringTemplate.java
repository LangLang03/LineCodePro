package cn.lineai.ai.prompt;

import java.util.Map;

public final class StringTemplate {
    private final String template;

    public StringTemplate(String template) {
        this.template = template == null ? "" : template;
    }

    public String render(Map<String, String> values) {
        String rendered = template;
        for (Map.Entry<String, String> entry : values.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered.trim();
    }
}
