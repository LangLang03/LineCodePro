package cn.lineai.feature.extensions;

import java.io.ByteArrayInputStream;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.data.api.SkillStore;

/**
 * Shared install pipeline for downloaded skill archives (SkillHub, GitHub, and other sources).
 *
 * <p>This is the single install path for remote skills: it always funnels archive bytes through
 * {@link SkillStore#importSkill}, so all sources get identical validation and deduplication.</p>
 */
final class RemoteSkillInstall {

    /** Source of the archive bytes; executed on the I/O scheduler. */
    interface Download {
        /**
         * Fetches the archive.
         *
         * @return raw archive bytes
         * @throws Exception on any download failure; the message is surfaced to the callback
         */
        byte[] fetch() throws Exception;
    }

    /** Completion notification invoked on the main thread. */
    interface Callback {
        /** Invoked after the archive was imported successfully. */
        void onSuccess();

        /**
         * Invoked when the download or import failed.
         *
         * @param message failure description suitable for display
         */
        void onError(String message);
    }

    /** Utility class; not instantiable. */
    private RemoteSkillInstall() {
    }

    /**
     * Downloads the archive on I/O, imports it through the skill store, and reports the outcome
     * on the main thread.
     *
     * @param scheduler scheduler enforcing the I/O-to-main threading contract
     * @param store skill store performing the actual import
     * @param download archive source
     * @param displayName display name assigned to the imported skill
     * @param callback completion notification
     */
    static void run(Scheduler scheduler, SkillStore store, Download download,
                    String displayName, Callback callback) {
        scheduler.io(() -> {
            try {
                byte[] bytes = download.fetch();
                store.importSkill(new ByteArrayInputStream(bytes), displayName);
                scheduler.main(callback::onSuccess);
            } catch (Exception error) {
                String message = error.getMessage() == null
                        ? error.getClass().getSimpleName() : error.getMessage();
                scheduler.main(() -> callback.onError(message));
            }
        });
    }
}
