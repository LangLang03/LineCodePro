package cn.lineai.engine.prompt;

import android.app.Application;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

/**
 * Native tool models must not see tool names in the prompt (the schemas travel in the request's
 * tools field); prompt-based fallback models keep the name list.
 */
@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SystemPromptAssemblerTest {

    private SystemPromptAssembler assembler() {
        PromptCatalog catalog = new AssetPromptCatalog(
                path -> RuntimeEnvironment.getApplication().getAssets().open(path));
        return new SystemPromptAssembler(new PromptStore(catalog, null));
    }

    @Test public void nativeToolsPromptCarriesNoToolNames() {
        String prompt = assembler().assemble("id", "Model", "provider", "OPENAI_COMPATIBLE",
                "coding", "agent", "", "", "");
        assertFalse(prompt, prompt.contains("Available tools"));
        assertFalse(prompt, prompt.contains("Tools currently denied"));
        assertFalse(prompt, prompt.contains("actually allowed"));
    }

    @Test public void fallbackPromptStillListsAllowedAndBlockedTools() {
        String prompt = assembler().assemble("id", "Model", "provider", "OPENAI_COMPATIBLE",
                "coding", "agent", "", "read_file, shell", "write_file");
        assertTrue(prompt, prompt.contains("## Available tools"));
        assertTrue(prompt, prompt.contains("read_file, shell"));
        assertTrue(prompt, prompt.contains("Tools denied by the current permission mode: write_file"));
    }

    @Test public void minimalPromptWithoutNamesStaysClean() {
        assertFalse(assembler().minimal("").contains("Tools:"));
        String withNames = assembler().minimal("shell, read_file");
        assertTrue(withNames, withNames.contains("Tools: shell, read_file"));
    }
}
