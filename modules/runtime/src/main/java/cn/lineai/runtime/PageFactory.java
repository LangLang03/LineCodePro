package cn.lineai.runtime;

import android.view.View;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.core.mvi.present.UiState;
import cn.lineai.feature.extensions.ExtensionScreens;
import cn.lineai.feature.models.ModelScreens;
import cn.lineai.feature.settings.SettingsScreens;
import cn.lineai.feature.workspace.WorkspaceScreens;

/** Creates every pushed screen; chat and the drawer stay in the activity. */
public final class PageFactory {

    public static final ScreenId<UiState, Params.Args> BROWSER =
            new ScreenId<>("browser", args -> args);

    private final SettingsPageFactory settings;
    private final FeaturePageFactory features;

    public PageFactory(PageHost host) {
        this.settings = new SettingsPageFactory(host);
        this.features = new FeaturePageFactory(host);
    }

    public View create(ScreenKey<?, ?> key) {
        if (key.id() == PageFactory.BROWSER) return settings.create(key);
        if (key.id() == ModelScreens.LIST || key.id() == ModelScreens.OPTIONS
                || key.id() == ModelScreens.EDIT || key.id() == ModelScreens.LOCAL) {
            return features.create(key);
        }
        if (key.id() == ExtensionScreens.AGENTS || key.id() == ExtensionScreens.AGENT_EDIT
                || key.id() == ExtensionScreens.MCPS || key.id() == ExtensionScreens.MCP_EDIT
                || key.id() == ExtensionScreens.SKILLS || key.id() == ExtensionScreens.SKILL_EDIT
                || key.id() == ExtensionScreens.SKILL_HUB
                || key.id() == ExtensionScreens.SKILL_GITHUB
                || key.id() == ExtensionScreens.LINECODE
                || key.id() == ExtensionScreens.TERMINAL_PROVIDERS) {
            return features.create(key);
        }
        if (key.id() == WorkspaceScreens.PROJECTS || key.id() == WorkspaceScreens.CHANGES) {
            return features.create(key);
        }
        if (key.id() == SettingsScreens.HOME || key.id() == SettingsScreens.INPUT
                || key.id() == SettingsScreens.OUTPUT || key.id() == SettingsScreens.BEHAVIOR
                || key.id() == SettingsScreens.SECURITY || key.id() == SettingsScreens.MCP
                || key.id() == SettingsScreens.EXTENSIONS || key.id() == SettingsScreens.MEMORY
                || key.id() == SettingsScreens.TUTORIAL || key.id() == SettingsScreens.SSH
                || key.id() == SettingsScreens.TERMUX || key.id() == SettingsScreens.TOOL_SETTINGS
                || key.id() == SettingsScreens.DATA || key.id() == SettingsScreens.ERROR_LOGS
                || key.id() == SettingsScreens.STORAGE || key.id() == SettingsScreens.KEEP_ALIVE
                || key.id() == SettingsScreens.ADVANCED || key.id() == SettingsScreens.PHONE_CONTROL
                || key.id() == SettingsScreens.PROMPTS || key.id() == SettingsScreens.ABOUT
                || key.id() == SettingsScreens.LICENSES || key.id() == SettingsScreens.THEME) {
            return settings.create(key);
        }
        throw new IllegalArgumentException("Unregistered view factory: " + key.id().route());
    }

    public SettingsPageFactory settings() {
        return settings;
    }

    public FeaturePageFactory features() {
        return features;
    }
}
