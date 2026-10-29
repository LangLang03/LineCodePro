package cn.lineai.feature.settings;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenRegistry;

public final class SettingsScreens {
    public static final ScreenId<SettingsState, Params.Empty> HOME =
            new ScreenId<>("settings", null, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> INPUT =
            new ScreenId<>("settings.input", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> OUTPUT =
            new ScreenId<>("settings.output", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> BEHAVIOR =
            new ScreenId<>("settings.behavior", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> THEME =
            new ScreenId<>("settings.theme", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> PROMPTS =
            new ScreenId<>("settings.prompts", BEHAVIOR, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> SECURITY =
            new ScreenId<>("settings.security", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> ADVANCED =
            new ScreenId<>("settings.advanced", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> KEEP_ALIVE =
            new ScreenId<>("settings.keepAlive", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> STORAGE =
            new ScreenId<>("settings.storage", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> ERROR_LOGS =
            new ScreenId<>("settings.errorLogs", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> DATA =
            new ScreenId<>("settings.data", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> MCP =
            new ScreenId<>("settings.mcp", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> TOOL_SETTINGS =
            new ScreenId<>("settings.tools", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> SSH =
            new ScreenId<>("settings.ssh", MCP, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> TERMUX =
            new ScreenId<>("settings.termux", SSH, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> TUTORIAL =
            new ScreenId<>("settings.tutorial", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> MEMORY =
            new ScreenId<>("settings.memory", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> PHONE_CONTROL =
            new ScreenId<>("settings.phoneControl", ADVANCED, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> EXTENSIONS =
            new ScreenId<>("settings.extensions", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> ABOUT =
            new ScreenId<>("settings.about", HOME, true, Params.Empty::fromArgs);
    public static final ScreenId<SettingsState, Params.Empty> LICENSES =
            new ScreenId<>("settings.licenses", ABOUT, true, Params.Empty::fromArgs);
    /** Tool-call card gallery opened from the output settings screen. */
    public static final ScreenId<SettingsState, Params.Empty> TOOL_PREVIEW =
            new ScreenId<>("settings.toolPreview", OUTPUT, true, Params.Empty::fromArgs);

    private SettingsScreens() { }

    public static void register(ScreenRegistry registry) {
        registry.register(HOME).register(INPUT).register(OUTPUT)
                .register(BEHAVIOR).register(THEME).register(ABOUT).register(LICENSES)
                .register(PROMPTS).register(SECURITY).register(ADVANCED).register(KEEP_ALIVE).register(STORAGE).register(ERROR_LOGS).register(DATA).register(MCP).register(TOOL_SETTINGS).register(SSH).register(TERMUX).register(TUTORIAL).register(MEMORY).register(PHONE_CONTROL).register(EXTENSIONS).register(TOOL_PREVIEW);
    }
}
