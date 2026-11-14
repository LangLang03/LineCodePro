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

    /**
     * @param agents   supplies enabled custom agents
     * @param skills   supplies enabled skills
     * @param todos    supplies the current TODO list
     * @param learning supplies the learning-mode section; may be null
     */
    public DefaultPromptExtensions(Supplier<List<AgentExtension>> agents,
                                   Supplier<List<Skill>> skills,
                                   Supplier<List<TodoItem>> todos,
                                   Supplier<String> learning) {
        this.agents = agents;
        this.skills = skills;
        this.todos = todos;
        this.learning = learning;
    }

    /** Learning-mode context from the supplied source; empty when absent. */
    @Override public String learningSection() {
        return learning == null ? "" : learning.get();
    }

    /** Agents, skills and TODO sections concatenated in that order. */
    @Override public String section() {
        String agentSection = ChatTurnConfig.agentSection(
                agents == null ? null : agents.get());
        String skillSection = ChatTurnConfig.skillSection(
                skills == null ? null : skills.get());
        return agentSection + skillSection
                + ChatTurnConfig.todoSection(todos == null ? null : todos.get());
    }
}
