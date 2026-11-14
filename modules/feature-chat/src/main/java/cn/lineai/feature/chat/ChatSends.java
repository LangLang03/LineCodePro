package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

import cn.lineai.core.model.ModelConfig;

/**
 * Composer submission entry points: guards, then one IO preparation step that may compact the
 * context before the ordered event queue starts the loop.
 */
final class ChatSends {

    private ChatSends() {
    }

    /** Validates the submission and queues the turn preparation on the IO scheduler. */
    static void send(ChatController chat, String text, List<String> imagePaths,
                     List<String> filePaths) {
        String prompt = text == null ? "" : text.trim();
        final List<String> images = imagePaths == null
                ? java.util.Collections.<String>emptyList() : new ArrayList<>(imagePaths);
        final List<String> files = filePaths == null
                ? java.util.Collections.<String>emptyList() : new ArrayList<>(filePaths);
        if ((prompt.isEmpty() && images.isEmpty() && files.isEmpty())
                || chat.engine != null || chat.starting || chat.detached) {
            return;
        }
        chat.starting = true;
        ModelConfig model = chat.modelConfig();
        if (model == null) {
            chat.starting = false;
            if (chat.getModelMissingHandler() != null) {
                chat.getModelMissingHandler().run();
            }
            return;
        }
        final int generation = ++chat.generationSeq;
        chat.scheduler.io(() -> {
            try {
                chat.startGeneration(prompt, images, files, model, generation);
            } catch (RuntimeException error) {
                chat.events.post(() -> chat.starting = false);
                throw error;
            }
        });
    }
}
