package cn.lineai.ai.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class StringTemplate {
    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{([^{}]+)\\}\\}");
    private final String template;

    public StringTemplate(String template) {
        this.template = template == null ? "" : template;
    }

    public String render(Map<String, String> values) {
        // Substitute the template once; placeholders inside inserted text are literal data.
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer rendered = new StringBuffer();
        while (matcher.find()) {
            String key = matcher.group(1);
            String replacement = values.containsKey(key) ? values.get(key) : matcher.group();
            matcher.appendReplacement(rendered, Matcher.quoteReplacement(replacement == null ? "" : replacement));
        }
        matcher.appendTail(rendered);
        return rendered.toString().trim();
    }
}
