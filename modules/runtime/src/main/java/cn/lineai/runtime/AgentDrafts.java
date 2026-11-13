package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.engine.ai.AgentDraftGenerator;
import cn.lineai.engine.ai.ModelProbe;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.engine.prompt.PromptId;
import cn.lineai.engine.prompt.PromptRenderer;
import cn.lineai.tool.api.Tool;
import cn.lineai.tool.api.ToolKind;

/**
 * Extension-agent authoring use case: renders the AGENT_DRAFT prompt over the tool catalog
 * and calls the selected model. Extracted from the page factory so the factory only wires.
 */
public final class AgentDrafts {

    private AgentDrafts() {
    }

    /** Renders the draft prompt over {@code tools} (phone tools excluded) and calls the model. */
    public static AgentExtension generate(ModelConfig model, String template,
                                          java.util.Collection<Tool> tools, String description,
                                          ModelProbe probe) throws Exception {
        List<String> names = new ArrayList<>();
        StringBuilder catalog = new StringBuilder();
        for (Tool tool : tools) {
            if (tool.kind() == ToolKind.PHONE) continue;
            if (catalog.length() > 0) catalog.append('\n');
            catalog.append(tool.name()).append(": ").append(tool.description());
            names.add(tool.name());
        }
        Map<String, String> values = new HashMap<>();
        values.put("USER_NEED", description);
        values.put("AVAILABLE_TOOLS", catalog.toString());
        String prompt = PromptRenderer.render(PromptId.AGENT_DRAFT, template, values);
        return new AgentDraftGenerator(probe::complete).generate(model, prompt, names);
    }
}
