package cn.lineai.feature.workspace;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

/**
 * Screen identifiers for the workspace flow.
 *
 * <p>CHANGES descends from PROJECTS so closing the changes view unwinds back to the project list.
 * Registration with the {@link ScreenRegistry} is the module's only wiring entry point.</p>
 */
public final class WorkspaceScreens {

    /** Root project list screen and back-stack root of the workspace flow. */
    public static final ScreenId<UiState, Params.Empty> PROJECTS =
            new ScreenId<>("workspace.projects", null, true, Params.Empty::fromArgs);

    /** Not instantiable; the registry wiring is static. */
    private WorkspaceScreens() {
    }

    /** Git changes screen stacked on the project list. */
    public static final ScreenId<UiState, Params.Empty> CHANGES =
            new ScreenId<>("workspace.changes", PROJECTS, true, Params.Empty::fromArgs);

    /**
     * Registers every workspace screen with the navigation registry.
     *
     * @param registry registry owned by the navigation host
     */
    public static void register(ScreenRegistry registry) {
        registry.register(PROJECTS).register(CHANGES);
    }
}
