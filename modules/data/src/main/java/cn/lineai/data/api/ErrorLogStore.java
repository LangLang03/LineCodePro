package cn.lineai.data.api;

import java.util.List;

/**
 * Append-only failure log with a bounded size.
 *
 * <p>Entries are captured as display text rather than structured exceptions; the log exists so the
 * error-log screen can show what failed and why. Implementations retain at most
 * {@link #MAX_ENTRIES} entries and drop the oldest ones beyond that bound.</p>
 */
public interface ErrorLogStore {

    /** Maximum number of entries retained before the oldest ones are discarded. */
    int MAX_ENTRIES = 200;

    /**
     * Lists the recorded failures, newest first.
     *
     * @return the retained entries in reverse creation order
     */
    List<ErrorLog> list();

    /**
     * Appends one failure and trims the log back to {@link #MAX_ENTRIES}.
     *
     * @param title short headline shown in the log list
     * @param subtitle optional secondary line shown beneath the title
     * @param detail full technical description shown when the entry is expanded
     */
    void record(String title, String subtitle, String detail);

    /** Deletes every recorded entry. */
    void clear();
}
