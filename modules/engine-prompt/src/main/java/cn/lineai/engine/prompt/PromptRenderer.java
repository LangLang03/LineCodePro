package cn.lineai.engine.prompt;

import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Strict placeholder substitution: every declared placeholder is required, malformed templates
 * and undeclared placeholders fail, while substituted values may safely contain braces.
 */
public final class PromptRenderer {

    private static final Pattern PLACEHOLDER = Pattern.compile("\\{\\{\\s*([A-Z0-9_]+)\\s*\\}\\}");

    private PromptRenderer() {
    }

    public static String render(PromptId prompt, String template, Map<String, String> values) {
        for (String variable : prompt.variables()) {
            if (!values.containsKey(variable)) {
                throw new IllegalArgumentException("Missing prompt argument " + variable
                        + " for " + prompt.id);
            }
        }
        validate(prompt, template);
        Matcher matcher = PLACEHOLDER.matcher(template);
        StringBuffer output = new StringBuffer();
        while (matcher.find()) {
            String value = values.get(matcher.group(1));
            matcher.appendReplacement(output, Matcher.quoteReplacement(value));
        }
        matcher.appendTail(output);
        return output.toString();
    }

    /** Rejects malformed templates and placeholders that the prompt does not declare. */
    public static void validate(PromptId prompt, String template) {
        if (PLACEHOLDER.matcher(template).replaceAll("").indexOf("{{") >= 0) {
            throw new IllegalArgumentException("Malformed placeholder in " + prompt.id);
        }
        Matcher matcher = PLACEHOLDER.matcher(template);
        while (matcher.find()) {
            if (!prompt.variables().contains(matcher.group(1))) {
                throw new IllegalArgumentException("Unexpected prompt placeholder "
                        + matcher.group(1) + " in " + prompt.id);
            }
        }
    }
}
