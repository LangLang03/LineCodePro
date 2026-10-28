package cn.lineai.data.api;

import java.io.InputStream;
import java.io.OutputStream;

/** Exports and imports the user data archive. */
public interface ArchiveService {

    final class Summary {
        public final int conversations;
        public final int models;
        public final int settings;

        public Summary(int conversations, int models, int settings) {
            this.conversations = conversations;
            this.models = models;
            this.settings = settings;
        }
    }

    Summary export(OutputStream output);

    Summary importFrom(InputStream input);
}
