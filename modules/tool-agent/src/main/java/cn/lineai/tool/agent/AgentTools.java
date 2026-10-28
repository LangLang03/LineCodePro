package cn.lineai.tool.agent;

import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.function.Supplier;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolRegistry;

/** Adds every enabled agent as a callable tool. */
public final class AgentTools {

    private AgentTools() {
    }

    public static ToolRegistry augment(ToolRegistry base,
                                       Supplier<List<AgentExtension>> agents,
                                       AgentRunner runner) {
        ToolRegistry registry = new ToolRegistry();
        Set<String> used = new HashSet<>();
        if (base != null) {
            for (Tool tool : base.all()) {
                registry.register(tool);
                used.add(tool.name());
            }
        }
        List<AgentExtension> current = agents == null ? null : agents.get();
        if (current == null) return registry;
        for (AgentExtension agent : current) {
            if (!agent.enabled || agent.prompt.isEmpty()) continue;
            String name = unique("agent_" + slug(agent.slug.isEmpty() ? agent.name : agent.slug), used);
            used.add(name);
            registry.register(new AgentTool(agent, name, runner));
        }
        return registry;
    }

    private static String unique(String base, Set<String> used) {
        if (!used.contains(base)) return base;
        int index = 2;
        while (used.contains(base + "_" + index)) index++;
        return base + "_" + index;
    }

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
