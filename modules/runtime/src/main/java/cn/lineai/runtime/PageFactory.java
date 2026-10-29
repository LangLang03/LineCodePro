package cn.lineai.runtime;

import android.view.View;

import cn.lineai.core.mvi.nav.Params;
import cn.lineai.core.mvi.nav.ScreenId;
import cn.lineai.core.mvi.nav.ScreenKey;
import cn.lineai.core.mvi.present.UiState;

/**
 * Creates every pushed screen; chat and the drawer stay in the activity.
 *
 * <p>Dispatch is by route family, not by a per-screen branch list, so a feature that adds a page
 * (or the settings family that adds a utility page) never has to remember this class. The route
 * prefixes are the ownership boundary declared by the feature modules themselves.</p>
 */
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
        String route = key.id().route();
        if (featureRoute(route)) {
            return features.create(key);
        }
        if (settingsRoute(route)) {
            return settings.create(key);
        }
        throw new IllegalArgumentException("Unregistered view factory: " + route);
    }

    private static boolean featureRoute(String route) {
        return route.equals("models") || route.startsWith("models.")
                || route.startsWith("extensions.") || route.startsWith("workspace.");
    }

    private static boolean settingsRoute(String route) {
        return route.equals("browser") || route.equals("settings") || route.startsWith("settings.");
    }

    public SettingsPageFactory settings() {
        return settings;
    }

    public FeaturePageFactory features() {
        return features;
    }
}
