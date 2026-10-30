package cn.lineai.feature.chat;

import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.Map;
import java.util.Set;

/**
 * Keyed disclosure memory owned by {@link TimelineView}.
 *
 * <p>Only explicit user overrides are stored; the default comes from the preference that created
 * the item. That is the legacy {@code ToggleState} contract (a toggled key inverts its default),
 * and it survives every rebind and theme change because the set outlives the row views.</p>
 *
 * <p>Disclosures whose default changes while a turn streams (the process panel opens itself while
 * running and folds when done) record an absolute choice instead, so a manual collapse is not
 * re-read as "inverted default" when the default flips.</p>
 */
final class DisclosureState {

    private final Set<String> toggled = new LinkedHashSet<>();
    private final Map<String, Boolean> choices = new LinkedHashMap<>();

    boolean isOpen(String key, boolean byDefault) {
        Boolean choice = choices.get(key);
        if (choice != null) {
            return choice;
        }
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

    /** Absolute open state recorded by a user toggle, or {@code null} when never touched. */
    Boolean explicit(String key) {
        return choices.get(key);
    }

    void setExplicit(String key, boolean open) {
        choices.put(key, open);
    }

    void clear() {
        toggled.clear();
        choices.clear();
    }
}
