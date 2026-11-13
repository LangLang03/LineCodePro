package cn.lineai.data.api;

import java.io.InputStream;
import java.io.OutputStream;

/**
 * Exports and imports the user data archive.
 *
 * <p>The archive is a ZIP of JSON table dumps; export strips secrets, and import replaces the
 * current data wholesale inside one transaction. Implementations are synchronous; callers own the
 * scheduler.</p>
 */
public interface ArchiveService {

    /** Row counts of the tables users care about, as reported by the last export or import. */
    final class Summary {
        public final int conversations;
        public final int models;
        public final int settings;

        /**
         * Creates a summary.
         *
         * @param conversations number of conversation rows covered
         * @param models number of model rows covered
         * @param settings number of settings rows covered
         */
        public Summary(int conversations, int models, int settings) {
            this.conversations = conversations;
            this.models = models;
            this.settings = settings;
        }
    }

    /**
     * Writes the full archive to the given stream. Exported data contains no secrets: model API
     * keys are dropped and secret settings rows are omitted.
     *
     * @param output stream receiving the ZIP content; not closed by the implementation
     * @return row counts of the exported tables
     */
    Summary export(OutputStream output);

    /**
     * Reads an archive and replaces the current database contents and workspace files. The
     * archive is validated and staged before any live data is touched, so a malformed archive
     * cannot leave the installation half-restored.
     *
     * @param input stream holding the ZIP content
     * @return row counts of the imported tables
     */
    Summary importFrom(InputStream input);
}
