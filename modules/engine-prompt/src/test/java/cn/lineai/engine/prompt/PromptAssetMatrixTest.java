package cn.lineai.engine.prompt;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.junit.runners.Parameterized;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.Collection;

import static org.junit.Assert.assertTrue;

@RunWith(Parameterized.class)
public class PromptAssetMatrixTest {

    @Parameterized.Parameters(name = "{0}")
    public static Collection<Object[]> prompts() {
        Object[][] values = new Object[PromptId.values().length][];
        for (int i = 0; i < PromptId.values().length; i++) {
            values[i] = new Object[] {PromptId.values()[i]};
        }
        return Arrays.asList(values);
    }

    private final PromptId prompt;

    public PromptAssetMatrixTest(PromptId prompt) {
        this.prompt = prompt;
    }

    @Test public void assetIsPresentAndNonEmpty() throws Exception {
        File file = new File("src/main/assets/prompts", prompt.file);
        assertTrue(prompt.file, file.isFile());
        String text = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(prompt.id, text.trim().length() > 0);
    }
}
