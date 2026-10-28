package cn.lineai.feature.chat;

import java.util.List;
import java.util.function.Supplier;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.TodoItem;

/** Reads enabled Agent and Skill extensions and renders them as prompt sections. */
public final class DefaultPromptExtensions implements PromptExtensions {

    private final Supplier<List<AgentExtension>> agents;
    private final Supplier<List<Skill>> skills;
    private final Supplier<List<TodoItem>> todos;
    private final Supplier<String> learning;

    public DefaultPromptExtensions(Supplier<List<AgentExtension>> agents,
                                   Supplier<List<Skill>> skills,
                                   Supplier<List<TodoItem>> todos,
                                   Supplier<String> learning) {
        this.agents = agents;
        this.skills = skills;
        this.todos = todos;
        this.learning = learning;
    }

    @Override public String learningSection() {
        return learning == null ? "" : learning.get();
    }

    @Override public String section() {
        String agentSection = ChatTurnConfig.agentSection(
                agents == null ? null : agents.get());
        String skillSection = ChatTurnConfig.skillSection(
                skills == null ? null : skills.get());
        return agentSection + skillSection
                + ChatTurnConfig.todoSection(todos == null ? null : todos.get());
    }
}
