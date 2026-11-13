package cn.lineai.engine.prompt;

import java.io.IOException;
import java.io.InputStream;

/**
 * Opens a prompt asset by path; keeps Android asset access out of this module.
 * The composition root (runtime) binds it to the app's AssetManager.
 */
public interface PromptAssetSource {

    InputStream open(String path) throws IOException;
}
