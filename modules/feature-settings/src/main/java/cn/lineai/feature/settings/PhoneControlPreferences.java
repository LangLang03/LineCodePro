package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/** Per-action switches for accessibility phone control. */
public final class PhoneControlPreferences {

    public static final String SCREENSHOT = "screenshot";
    public static final String CLICK = "click";
    public static final String SWIPE = "swipe";
    public static final String LONG_PRESS = "longPress";
    public static final String VIEW_HIERARCHY = "viewHierarchy";
    public static final String VIEW_ACTION = "viewAction";
    public static final String GLOBAL_ACTION = "globalAction";

    private static final String PREFIX = "phoneControl.";
    private static final String KEY_DISCLAIMER = "phoneControl.disclaimerAccepted";

    private final SettingsStore settings;

    public PhoneControlPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    public boolean disclaimerAccepted() {
        return Boolean.parseBoolean(settings.get(KEY_DISCLAIMER, "false"));
    }

    public void acceptDisclaimer() {
        settings.put(KEY_DISCLAIMER, "true");
    }

    public boolean enabled(String action) {
        return Boolean.parseBoolean(settings.get(PREFIX + action, String.valueOf(defaultEnabled(action))));
    }

    public void setEnabled(String action, boolean value) {
        settings.put(PREFIX + action, String.valueOf(value));
    }

    public static boolean defaultEnabled(String action) {
        return !VIEW_ACTION.equals(action) && !GLOBAL_ACTION.equals(action);
    }
}
