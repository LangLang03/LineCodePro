package cn.lineai.feature.models;

import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;
import cn.lineai.core.mvi.present.UiState;

public final class ModelScreens {
    public static final ScreenId<UiState, Params.Empty> LIST =
            new ScreenId<>("models", null, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Empty> OPTIONS =
            new ScreenId<>("models.options", LIST, true, Params.Empty::fromArgs);
    public static final ScreenId<UiState, Params.Args> EDIT =
            new ScreenId<>("models.edit", OPTIONS, true, args -> args);
    public static final ScreenId<UiState, Params.Args> LOCAL =
            new ScreenId<>("models.local", LIST, true, args -> args);

    private ModelScreens() { }

    public static void register(ScreenRegistry registry) {
        registry.register(LIST).register(OPTIONS).register(EDIT).register(LOCAL);
    }

    public static Params.Args editArgs(ModelConfig model) {
        return model == null ? Params.Args.empty() : Params.Args.of("id", model.id);
    }

    public static Params.Args presetArgs(String presetId) {
        return presetId == null ? Params.Args.empty() : Params.Args.of("preset", presetId);
    }
}
