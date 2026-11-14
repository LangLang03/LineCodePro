package cn.lineai.tool.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/**
 * Adds every enabled agent as a callable tool.
 *
 * <p>Names are derived from the agent slug and disambiguated against the existing registry so
 * a renamed or duplicated agent can never shadow a built-in tool.</p>
 */
public final class AgentTools {

    /** Utility class; not instantiable. */
    private AgentTools() {
    }

    /**
     * Copies the base registry, registers the sub-agent management tools and one tool per
     * enabled agent with a non-empty prompt.
     *
     * @param base registry to extend; may be {@code null} to start empty
     * @param agents supplier of the current agent extension list; may be {@code null}
     * @param runtime runtime launching and tracking sub agent sessions
     * @return a new registry containing the base tools plus the agent tool family
     */
    public static ToolRegistry augment(ToolRegistry base,
                                       Supplier<List<AgentExtension>> agents,
                                       AgentRuntime runtime) {
        ToolRegistry registry = new ToolRegistry();
        Set<String> used = new HashSet<>();
        if (base != null) {
            for (Tool tool : base.all()) {
                registry.register(tool);
                used.add(tool.name());
            }
        }
        if (runtime != null) {
            registry.register(new AgentListTool(runtime));
            registry.register(new AgentSendTool(runtime));
            registry.register(new AgentWaitTool(runtime));
        }
        List<AgentExtension> current = agents == null ? null : agents.get();
        if (current == null) return registry;
        for (AgentExtension agent : current) {
            if (!agent.enabled || agent.prompt.isEmpty()) continue;
            String name = unique("agent_" + slug(agent.slug.isEmpty() ? agent.name : agent.slug), used);
            used.add(name);
            registry.register(new AgentTool(agent, name, runtime));
        }
        return registry;
    }

    /**
     * Appends the lowest numeric suffix that makes the candidate name unique.
     *
     * @param base preferred name
     * @param used names already claimed in this registry build
     * @return a name not present in {@code used}
     */
    private static String unique(String base, Set<String> used) {
        if (!used.contains(base)) return base;
        int index = 2;
        while (used.contains(base + "_" + index)) index++;
        return base + "_" + index;
    }

    /**
     * Reduces an agent slug or name to lowercase letters, digits and underscore separators.
     *
     * @param value raw agent slug or name
     * @return the slug, or {@code "custom"} when nothing usable remains
     */
    private static String slug(String value) {
        StringBuilder out = new StringBuilder();
        String lower = value.toLowerCase(Locale.ROOT);
        for (int i = 0; i < lower.length(); i++) {
            char ch = lower.charAt(i);
            if ((ch >= 'a' && ch <= 'z') || (ch >= '0' && ch <= '9')) {
                out.append(ch);
            } else if ((ch == '_' || ch == '-' || Character.isWhitespace(ch))
                    && out.length() > 0 && out.charAt(out.length() - 1) != '_') {
                out.append('_');
            }
        }
        while (out.length() > 0 && out.charAt(out.length() - 1) == '_') {
            out.setLength(out.length() - 1);
        }
        return out.length() == 0 ? "custom" : out.toString();
    }
}
