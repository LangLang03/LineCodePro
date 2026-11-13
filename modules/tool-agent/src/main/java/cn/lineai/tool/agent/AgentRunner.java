package cn.lineai.tool.agent;

import cn.lineai.core.model.AgentExtension;

/**
 * Runs a custom agent task; implemented by the app with a nested tool loop.
 *
 * <p>The abstraction keeps the recursive agent execution in the application layer, where the
 * nested loop can share sessions and models with the outer loop.</p>
 */
public interface AgentRunner {

    /**
     * Executes one task with the given agent and returns its final answer.
     *
     * @param agent agent extension providing name, prompt and tool permissions
     * @param task task text handed to the agent
     * @return the agent's final answer text
     * @throws Exception when the nested loop fails to complete the task
     */
    String run(AgentExtension agent, String task) throws Exception;
}
