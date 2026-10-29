package cn.lineai.feature.chat;

import java.util.LinkedHashSet;
import java.util.Set;

/**
 * Keyed disclosure memory owned by {@link TimelineView}.
 *
 * <p>Only explicit user overrides are stored; the default comes from the preference that created
 * the item. That is the legacy {@code ToggleState} contract (a toggled key inverts its default),
 * and it survives every rebind and theme change because the set outlives the row views.</p>
 */
final class DisclosureState {

    private final Set<String> toggled = new LinkedHashSet<>();

    boolean isOpen(String key, boolean byDefault) {
        return toggled.contains(key) != byDefault;
    }

    void toggle(String key) {
        if (!toggled.remove(key)) {
            toggled.add(key);
        }
    }

    void setOpen(String key, boolean byDefault, boolean open) {
        if (isOpen(key, byDefault) != open) {
            toggle(key);
        }
    }

    void clear() {
        toggled.clear();
    }
}
