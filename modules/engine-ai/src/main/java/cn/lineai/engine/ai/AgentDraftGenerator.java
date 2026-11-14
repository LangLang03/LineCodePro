package cn.lineai.engine.ai;

import org.json.JSONArray;
import org.json.JSONObject;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.ModelConfig;

/**
 * Turns one natural-language request into an Agent draft.
 *
 * <p>The model only proposes values; unknown tool names are dropped against the catalog the caller
 * passes in, the slug is normalised here, and incomplete drafts are rejected instead of filling the
 * form with empty fields.</p>
 */
public final class AgentDraftGenerator {

    /** Single-shot completion source, injected so tests can stub the model call. */
    public interface Completion {
        /**
         * Completes a prompt with the given model.
         *
         * @param model model configuration for the call
         * @param prompt full prompt text
         * @return raw completion text
         * @throws Exception when the underlying call fails
         */
        String complete(ModelConfig model, String prompt) throws Exception;
    }

    private final Completion completion;

    /**
     * Creates a generator over the given completion source.
     *
     * @param completion completion source used to draft agents
     */
    public AgentDraftGenerator(Completion completion) {
        this.completion = completion;
    }

    /**
     * Produces an agent draft from a natural-language request.
     *
     * <p>The answer must contain a JSON object with {@code name}, {@code slug} and {@code prompt},
     * optionally {@code trigger} and {@code toolNames}. Tool names outside the allowed catalog are
     * dropped, and an empty filtered set falls back to the read-only tool set.</p>
     *
     * @param model model configuration for the drafting call
     * @param prompt natural-language description of the agent to create
     * @param allowedTools tool names the draft may reference
     * @return complete draft, or {@code null} when the answer contains no usable draft
     * @throws Exception when the underlying completion call fails
     */
    public AgentExtension generate(ModelConfig model, String prompt, List<String> allowedTools)
            throws Exception {
        String answer = completion.complete(model, prompt);
        JSONObject draft = extractObject(answer);
        if (draft == null) {
            return null;
        }
        String name = draft.optString("name", "").trim();
        String slug = normalizeSlug(draft.optString("slug", name));
        String systemPrompt = draft.optString("prompt", "").trim();
        if (name.isEmpty() || slug.isEmpty() || systemPrompt.isEmpty()) {
            return null;
        }
        String trigger = draft.optString("trigger", "").trim();
        return AgentExtension.builder()
                .name(name)
                .slug(slug)
                .prompt(systemPrompt)
                .trigger(trigger)
                .toolNames(tools(draft.optJSONArray("toolNames"), allowedTools))
                .build();
    }

    /** Filters proposed tool names against the catalog, with the read-only set as fallback. */
    private static List<String> tools(JSONArray values, List<String> allowedTools) {
        Set<String> allowed = new HashSet<>();
        if (allowedTools != null) {
            allowed.addAll(allowedTools);
        }
        List<String> result = new ArrayList<>();
        if (values != null) {
            for (int index = 0; index < values.length(); index++) {
                String name = values.optString(index, "").trim();
                if (allowed.contains(name) && !result.contains(name)) {
                    result.add(name);
                }
            }
        }
        if (result.isEmpty()) {
            for (String fallback : new String[] {"read_file", "glob", "list_dir"}) {
                if (allowed.contains(fallback)) {
                    result.add(fallback);
                }
            }
        }
        return result;
    }

    /** Pulls the first JSON object out of a fenced or prose-wrapped answer. */
    static JSONObject extractObject(String answer) {
        String text = answer == null ? "" : answer.trim();
        int start = text.indexOf('{');
        int end = text.lastIndexOf('}');
        if (start < 0 || end <= start) {
            return null;
        }
        try {
            return new JSONObject(text.substring(start, end + 1));
        } catch (Exception invalid) {
            return null;
        }
    }

    /** Lowercase identifier; non-alphanumeric runs collapse to a dash (legacy normalisation). */
    static String normalizeSlug(String value) {
        String raw = value == null ? "" : value.trim().toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        boolean dash = false;
        for (int index = 0; index < raw.length() && builder.length() < 48; index++) {
            char ch = raw.charAt(index);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9') || ch == '_') {
                builder.append(ch);
                dash = false;
            } else if (!dash && builder.length() > 0) {
                builder.append('-');
                dash = true;
            }
        }
        int end = builder.length();
        while (end > 0 && (builder.charAt(end - 1) == '-' || builder.charAt(end - 1) == '_')) end--;
        String clean = builder.substring(0, end);
        if (clean.isEmpty()) {
            return "";
        }
        char first = clean.charAt(0);
        return first >= 'a' && first <= 'z' ? clean : "agent-" + clean;
    }
}
