package cn.lineai.feature.extensions;

import java.io.ByteArrayInputStream;

import cn.lineai.core.kernel.time.Scheduler;
import cn.lineai.data.api.SkillStore;

/** Shared install pipeline for downloaded skill archives (SkillHub, GitHub, ...). */
final class RemoteSkillInstall {

    interface Download {
        byte[] fetch() throws Exception;
    }

    interface Callback {
        void onSuccess();

        void onError(String message);
    }

    private RemoteSkillInstall() {
    }

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
