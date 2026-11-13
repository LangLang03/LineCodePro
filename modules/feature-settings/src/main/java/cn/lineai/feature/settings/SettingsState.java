package cn.lineai.feature.settings;

import cn.lineai.core.model.UiPreferences;
import cn.lineai.core.model.UiPreferences.Field;
import cn.lineai.core.mvi.present.UiState;
import cn.lineai.core.kernel.error.ErrorCode;

/**
 * Immutable UI state for the generic settings screens rendered by {@link SettingsPresenter}.
 *
 * <p>The state keeps two snapshots of the preferences: {@link #values} holds the edited (working)
 * values shown to the user, while {@link #saved} holds the last values known to be persisted.
 * Comparing the two drives the presenter's write queue.</p>
 */
public final class SettingsState implements UiState {
    /** Working preference values currently rendered on screen. */
    public final UiPreferences values;
    /** Preference values known to be persisted; divergence from {@link #values} means pending writes. */
    public final UiPreferences saved;
    /** Field currently being written to storage, or null when no write is in flight. */
    public final Field writing;
    /** True once the persisted preferences have been loaded from storage at least once. */
    public final boolean loaded;
    /** Last storage error reported to the UI, or null when the last operation succeeded. */
    public final ErrorCode error;

    /**
     * Creates a settings state snapshot.
     *
     * @param values working preference values
     * @param saved persisted preference values
     * @param writing field being written, or null
     * @param loaded whether persisted values have been loaded
     * @param error last storage error, or null
     */
    public SettingsState(UiPreferences values, UiPreferences saved, Field writing,
                         boolean loaded, ErrorCode error) {
        this.values = values;
        this.saved = saved;
        this.writing = writing;
        this.loaded = loaded;
        this.error = error;
    }
}
