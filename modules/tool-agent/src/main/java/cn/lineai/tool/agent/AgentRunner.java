package cn.lineai.tool.agent;

import cn.lineai.core.model.AgentExtension;

/** Runs a custom agent task; implemented by the app with a nested tool loop. */
public interface AgentRunner {

    String run(AgentExtension agent, String task) throws Exception;
}
