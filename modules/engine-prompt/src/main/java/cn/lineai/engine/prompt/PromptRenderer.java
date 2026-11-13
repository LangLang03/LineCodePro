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

    /**
     * Substitutes {@code {{NAME}}} placeholders in a template.
     *
     * <p>Every declared variable must be present in {@code values}; extra entries are ignored.
     * Substituted values are inserted literally, so braces inside them are never re-interpreted
     * as placeholders.</p>
     *
     * @param prompt prompt the template belongs to; declares the allowed placeholders
     * @param template template text with {@code {{NAME}}} placeholders
     * @param values substitution values keyed by placeholder name
     * @return rendered text
     * @throws IllegalArgumentException when a declared variable is missing, the template is
     *         malformed, or the template uses an undeclared placeholder
     */
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

    /**
     * Rejects malformed templates and placeholders that the prompt does not declare.
     *
     * @param prompt prompt whose declared placeholders define the allowed set
     * @param template template text to check
     * @throws IllegalArgumentException when a leftover {@code {{}} sequence or an undeclared
     *         placeholder is found
     */
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
