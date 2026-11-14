package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.ModelConfig;

/**
 * Model configurations plus the single selected default.
 *
 * <p>The selected row is the chat default; save and delete keep the invariant that a non-empty
 * table always has exactly one default. Implementations are synchronous; callers own the
 * scheduler.</p>
 */
public interface ModelStore {

    /**
     * Lists the stored models, default first and then alphabetical by name.
     *
     * @return every stored model configuration
     */
    List<ModelConfig> list();

    /**
     * Looks up one model by identifier.
     *
     * @param id model identifier
     * @return the model or null when the identifier is unknown
     */
    ModelConfig find(String id);

    /**
     * Returns the chat default model.
     *
     * @return the model marked default, or the first model when none is marked, or null when the
     *         table is empty
     */
    ModelConfig selected();

    /**
     * Inserts or updates one model. The first inserted model and every model saved with the
     * default flag become the default, clearing the flag on the previous default.
     *
     * @param model configuration to persist; must not be null
     */
    void save(ModelConfig model);

    /**
     * Makes the given model the single default.
     *
     * @param id model identifier; unknown identifiers leave the default unchanged
     */
    void select(String id);

    /**
     * Deletes the given models in one transaction and reassigns the default to the first
     * remaining model when the default itself was removed.
     *
     * @param ids model identifiers to delete; null or empty leaves the store untouched
     */
    void delete(List<String> ids);

    /** Seeds the first model from the legacy single-model settings keys, if any. */
    void seedFromSettings(SettingsStore settings);
}
