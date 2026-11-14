package cn.lineai.engine.prompt;

import java.io.IOException;
import java.io.InputStream;

/**
 * Opens a prompt asset by path; keeps Android asset access out of this module.
 * The composition root (runtime) binds it to the app's AssetManager.
 */
public interface PromptAssetSource {

    /**
     * Opens the prompt asset at the given module-relative path.
     *
     * @param path asset path relative to the module root ({@code prompts/...} in practice)
     * @return stream over the asset contents; the caller closes it
     * @throws IOException when the asset cannot be opened
     */
    InputStream open(String path) throws IOException;
}
