package cn.lineai.feature.settings;

import cn.lineai.data.api.SettingsStore;

/** Per-action switches for accessibility phone control. */
public final class PhoneControlPreferences {

    /** Action key for taking screenshots. */
    public static final String SCREENSHOT = "screenshot";
    /** Action key for tapping the screen. */
    public static final String CLICK = "click";
    /** Action key for swipe gestures. */
    public static final String SWIPE = "swipe";
    /** Action key for long-press gestures. */
    public static final String LONG_PRESS = "longPress";
    /** Action key for reading the view hierarchy. */
    public static final String VIEW_HIERARCHY = "viewHierarchy";
    /** Action key for acting on individual views. */
    public static final String VIEW_ACTION = "viewAction";
    /** Action key for issuing global system actions. */
    public static final String GLOBAL_ACTION = "globalAction";

    private static final String PREFIX = "phoneControl.";
    private static final String KEY_DISCLAIMER = "phoneControl.disclaimerAccepted";

    private final SettingsStore settings;

    /**
     * Creates a preference facade over the given settings store.
     *
     * @param settings backing key-value store
     */
    public PhoneControlPreferences(SettingsStore settings) {
        this.settings = settings;
    }

    /**
     * Returns whether the user accepted the accessibility disclaimer.
     *
     * @return true once the disclaimer has been accepted
     */
    public boolean disclaimerAccepted() {
        return Boolean.parseBoolean(settings.get(KEY_DISCLAIMER, "false"));
    }

    /** Records the acceptance of the accessibility disclaimer. */
    public void acceptDisclaimer() {
        settings.put(KEY_DISCLAIMER, "true");
    }

    /**
     * Returns whether the given action kind is permitted.
     *
     * @param action one of the action key constants defined by this class
     * @return persisted switch value, or the default when never persisted
     */
    public boolean enabled(String action) {
        return Boolean.parseBoolean(settings.get(PREFIX + action, String.valueOf(defaultEnabled(action))));
    }

    /**
     * Persists the switch value for an action kind.
     *
     * @param action one of the action key constants defined by this class
     * @param value true to permit the action
     */
    public void setEnabled(String action, boolean value) {
        settings.put(PREFIX + action, String.valueOf(value));
    }

    /**
     * Returns the factory default for an action kind; privileged actions default to disabled.
     *
     * @param action one of the action key constants defined by this class
     * @return true unless the action is {@link #VIEW_ACTION} or {@link #GLOBAL_ACTION}
     */
    public static boolean defaultEnabled(String action) {
        return !VIEW_ACTION.equals(action) && !GLOBAL_ACTION.equals(action);
    }
}
