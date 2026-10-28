package cn.lineai.feature.workspace;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

public final class WorkspaceScreens {

    public static final ScreenId<UiState, Params.Empty> PROJECTS =
            new ScreenId<>("workspace.projects", null, true, Params.Empty::fromArgs);

    private WorkspaceScreens() {
    }

    public static final ScreenId<UiState, Params.Empty> CHANGES =
            new ScreenId<>("workspace.changes", PROJECTS, true, Params.Empty::fromArgs);

    public static void register(ScreenRegistry registry) {
        registry.register(PROJECTS).register(CHANGES);
    }
}
