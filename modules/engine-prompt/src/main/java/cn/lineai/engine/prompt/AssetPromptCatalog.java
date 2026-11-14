package cn.lineai.engine.prompt;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Reads every template from {@code assets/prompts} once and fails fast when an asset is gone. */
public final class AssetPromptCatalog implements PromptCatalog {

    private final Map<PromptId, String> texts = new EnumMap<>(PromptId.class);

    /**
     * Loads every packaged template eagerly.
     *
     * @param assets source for opening prompt assets
     * @throws IllegalStateException when any template asset is missing or unreadable, so startup
     *         fails fast instead of surfacing later at render time
     */
    public AssetPromptCatalog(PromptAssetSource assets) {
        for (PromptId prompt : PromptId.values()) {
            texts.put(prompt, read(assets, "prompts/" + prompt.file));
        }
    }

    /**
     * Returns the packaged template text for a prompt.
     *
     * @param prompt identifier of the requested template
     * @return packaged template text
     * @throws IllegalStateException when the prompt was not loaded
     */
    @Override public String text(PromptId prompt) {
        String value = texts.get(prompt);
        if (value == null) throw new IllegalStateException("Prompt missing: " + prompt.id);
        return value;
    }

    /** Reads a UTF-8 asset fully into memory. */
    private static String read(PromptAssetSource assets, String path) {
        try (InputStream input = assets.open(path)) {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
            return output.toString(StandardCharsets.UTF_8.name());
        } catch (IOException error) {
            throw new IllegalStateException("Cannot read prompt asset: " + path, error);
        }
    }
}
