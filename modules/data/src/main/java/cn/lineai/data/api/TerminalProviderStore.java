package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.TerminalProviderConfig;

/**
 * Installed external terminal providers.
 *
 * <p>A provider is an external application the shell can hand a session to; the store only records
 * its coordinates and enabled state. Implementations are synchronous; callers own the scheduler.</p>
 */
public interface TerminalProviderStore {

    /**
     * Lists the installed providers, newest update first.
     *
     * @return every stored provider configuration
     */
    List<TerminalProviderConfig> list();

    /**
     * Installs one provider or returns the existing entry for the same package and service class,
     * so repeated discovery passes do not create duplicates.
     *
     * @param input provider configuration to install
     * @return the stored configuration, either the newly inserted one or the pre-existing match
     */
    TerminalProviderConfig add(TerminalProviderConfig input);

    /**
     * Toggles the enabled flag without rewriting the configuration.
     *
     * @param id provider identifier; unknown identifiers are ignored
     * @param enabled whether the provider may be offered to the user
     */
    void setEnabled(String id, boolean enabled);

    /**
     * Removes the provider with the given identifier.
     *
     * @param id provider identifier; unknown identifiers are ignored
     */
    void delete(String id);
}
