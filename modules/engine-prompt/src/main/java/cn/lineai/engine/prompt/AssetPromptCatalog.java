package cn.lineai.engine.prompt;

import android.content.res.AssetManager;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.Map;

/** Reads every template from {@code assets/prompts} once and fails fast when an asset is gone. */
public final class AssetPromptCatalog implements PromptCatalog {

    private final Map<PromptId, String> texts = new EnumMap<>(PromptId.class);

    public AssetPromptCatalog(AssetManager assets) {
        for (PromptId prompt : PromptId.values()) {
            texts.put(prompt, read(assets, "prompts/" + prompt.file));
        }
    }

    @Override public String text(PromptId prompt) {
        String value = texts.get(prompt);
        if (value == null) throw new IllegalStateException("Prompt missing: " + prompt.id);
        return value;
    }

    private static String read(AssetManager assets, String path) {
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
