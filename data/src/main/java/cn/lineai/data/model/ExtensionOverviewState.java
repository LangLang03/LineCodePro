package cn.lineai.data.model;

import cn.lineai.ipc.IpcProviderConfig;
import cn.lineai.model.ExtensionAgentConfig;
import cn.lineai.model.ExtensionMcpConfig;
import cn.lineai.model.SkillRecord;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ExtensionOverviewState {
    private final List<ExtensionAgentConfig> agents;
    private final List<ExtensionMcpConfig> mcps;
    private final List<SkillRecord> skills;
    private final List<IpcProviderConfig> ipcProviders;

    public ExtensionOverviewState(
            List<ExtensionAgentConfig> agents,
            List<ExtensionMcpConfig> mcps,
            List<SkillRecord> skills
    ) {
        this(agents, mcps, skills, null);
    }

    public ExtensionOverviewState(
            List<ExtensionAgentConfig> agents,
            List<ExtensionMcpConfig> mcps,
            List<SkillRecord> skills,
            List<IpcProviderConfig> ipcProviders
    ) {
        this.agents = agents == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(agents));
        this.mcps = mcps == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(mcps));
        this.skills = skills == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(skills));
        this.ipcProviders = ipcProviders == null ? Collections.emptyList() : Collections.unmodifiableList(new ArrayList<>(ipcProviders));
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

    public List<IpcProviderConfig> getIpcProviders() {
        return ipcProviders;
    }
}
