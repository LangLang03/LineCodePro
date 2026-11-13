package cn.lineai.feature.models;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

/**
 * Screen identifiers for the model management flow and their argument helpers.
 *
 * <p>The identifiers form a back-stack tree: OPTIONS and EDIT descend from LIST and OPTIONS so
 * leaving an editor unwinds to the option sheet rather than the raw list. Registration with the
 * {@link ScreenRegistry} is the module's only wiring entry point; screens themselves must load
 * their data on the io scheduler and render on the main thread.</p>
 */
public final class ModelScreens {
    /** Root list screen: back-stack root of the model flow, empty parameters. */
    public static final ScreenId<UiState, Params.Empty> LIST =
            new ScreenId<>("models", null, true, Params.Empty::fromArgs);
    /** Provider options sheet stacked directly on the list screen. */
    public static final ScreenId<UiState, Params.Empty> OPTIONS =
            new ScreenId<>("models.options", LIST, true, Params.Empty::fromArgs);
    /** Editor screen carrying the edited model id; child of the options sheet. */
    public static final ScreenId<UiState, Params.Args> EDIT =
            new ScreenId<>("models.edit", OPTIONS, true, args -> args);
    /** Local-model screen attached to the list; carries raw args, no dedicated builder. */
    public static final ScreenId<UiState, Params.Args> LOCAL =
            new ScreenId<>("models.local", LIST, true, args -> args);

    /** Not instantiable; the registry wiring is static. */
    private ModelScreens() { }

    /**
     * Registers every model screen with the navigation registry.
     *
     * @param registry registry owned by the navigation host
     */
    public static void register(ScreenRegistry registry) {
        registry.register(LIST).register(OPTIONS).register(EDIT).register(LOCAL);
    }

    /**
     * Builds editor arguments carrying a model id.
     *
     * @param model model to edit; null yields empty arguments
     * @return arguments with the {@code id} key, or empty when null
     */
    public static Params.Args editArgs(ModelConfig model) {
        return model == null ? Params.Args.empty() : Params.Args.of("id", model.id);
    }

    /**
     * Builds option-sheet arguments carrying a preset id.
     *
     * @param presetId preset identifier; null yields empty arguments
     * @return arguments with the {@code preset} key, or empty when null
     */
    public static Params.Args presetArgs(String presetId) {
        return presetId == null ? Params.Args.empty() : Params.Args.of("preset", presetId);
    }
}
