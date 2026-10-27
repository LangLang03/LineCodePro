package cn.lineai.feature.extensions;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

/** Routes for the extension family pages. */
public final class ExtensionScreens {

    public static final ScreenId<UiState, Params.Empty> AGENTS =
            new ScreenId<>("extensions.agents", null, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Args> AGENT_EDIT =
            new ScreenId<>("extensions.agents.edit", AGENTS, true, args -> args);

    private ExtensionScreens() {
    }

    public static final ScreenId<UiState, Params.Empty> MCPS =
            new ScreenId<>("extensions.mcps", null, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Args> MCP_EDIT =
            new ScreenId<>("extensions.mcps.edit", MCPS, true, args -> args);

    public static final ScreenId<UiState, Params.Empty> SKILLS =
            new ScreenId<>("extensions.skills", null, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Args> SKILL_EDIT =
            new ScreenId<>("extensions.skills.edit", SKILLS, true, args -> args);

    public static final ScreenId<UiState, Params.Empty> SKILL_HUB =
            new ScreenId<>("extensions.skills.hub", SKILLS, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Args> SKILL_DETAIL =
            new ScreenId<>("extensions.skills.detail", SKILLS, true, args -> args);
    public static final ScreenId<UiState, Params.Empty> SKILL_LOGIN =
            new ScreenId<>("extensions.skills.login", SKILLS, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Empty> SKILL_PUBLISH =
            new ScreenId<>("extensions.skills.publish", SKILLS, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Empty> SKILL_GITHUB =
            new ScreenId<>("extensions.skills.github", SKILLS, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Empty> LINECODE =
            new ScreenId<>("extensions.linecode", null, true, Params.Empty::fromArgs);

    public static final ScreenId<UiState, Params.Empty> TERMINAL_PROVIDERS =
            new ScreenId<>("extensions.terminalProviders", null, true, Params.Empty::fromArgs);

    public static void register(ScreenRegistry registry) {
        registry.register(AGENTS).register(AGENT_EDIT).register(MCPS).register(MCP_EDIT)
                .register(SKILLS).register(SKILL_EDIT).register(SKILL_HUB).register(SKILL_DETAIL).register(SKILL_LOGIN).register(SKILL_PUBLISH).register(SKILL_GITHUB).register(LINECODE)
                .register(TERMINAL_PROVIDERS);
    }

    public static Params.Args agentArgs(String id) {
        return id == null || id.isEmpty() ? Params.Args.empty() : Params.Args.of("id", id);
    }

    public static Params.Args mcpArgs(String id) {
        return id == null || id.isEmpty() ? Params.Args.empty() : Params.Args.of("id", id);
    }

    public static Params.Args skillArgs(String id) {
        return id == null || id.isEmpty() ? Params.Args.empty() : Params.Args.of("id", id);
    }
}
