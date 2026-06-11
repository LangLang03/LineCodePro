package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionOverviewState {
    private final List<ExtensionAgentConfig> agents;
    private final List<ExtensionMcpConfig> mcps;
    private final List<SkillRecord> skills;

    public ExtensionOverviewState(
            List<ExtensionAgentConfig> agents,
            List<ExtensionMcpConfig> mcps,
            List<SkillRecord> skills
    ) {
        this.agents = agents == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(agents));
        this.mcps = mcps == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(mcps));
        this.skills = skills == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(skills));
    }

    public List<ExtensionAgentConfig> getAgents() {
        return agents;
    }

    public List<ExtensionMcpConfig> getMcps() {
        return mcps;
    }

    public List<SkillRecord> getSkills() {
        return skills;
    }
}
