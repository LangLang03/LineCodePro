package cn.lineai.engine.ai;

import java.util.Arrays;
import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.ModelConfig;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class AgentDraftGeneratorTest {

    private static final ModelConfig MODEL = ModelConfig.builder()
            .identity("m", "Model")
            .endpoint("p", "model", "https://example.com/v1")
            .context(8000, 1024)
            .build();

    private AgentDraftGenerator generator(String answer) {
        return new AgentDraftGenerator((model, prompt) -> answer);
    }

    @Test public void parsesFencedDraftAndFiltersUnknownTools() throws Exception {
        AgentDraftGenerator generator = generator("```json\n"
                + "{\"name\":\"View 性能\",\"slug\":\"View Perf!\",\"prompt\":\"Analyse views.\","
                + "\"trigger\":\"slow UI\",\"toolNames\":[\"read_file\",\"nope\",\"glob\"]}\n```");
        AgentExtension draft = generator.generate(MODEL, "prompt",
                Arrays.asList("read_file", "glob", "list_dir"));
        assertEquals("View 性能", draft.name);
        assertEquals("view-perf", draft.slug);
        assertEquals("Analyse views.", draft.prompt);
        assertEquals("slow UI", draft.trigger);
        assertEquals(Arrays.asList("read_file", "glob"), draft.toolNames);
    }

    @Test public void fallsBackToReadOnlyToolsWhenTheModelPicksNone() throws Exception {
        AgentExtension draft = generator(
                "noise before {\"name\":\"A\",\"slug\":\"a\",\"prompt\":\"p\"} noise after")
                .generate(MODEL, "prompt", Arrays.asList("read_file", "shell_execute"));
        assertEquals(Arrays.asList("read_file"), draft.toolNames);
    }

    @Test public void rejectsDraftsWithoutRequiredFields() throws Exception {
        assertNull(generator("{\"name\":\"A\"}").generate(MODEL, "p", null));
        assertNull(generator("not json").generate(MODEL, "p", null));
    }

    @Test public void normalizesIdentifiers() {
        assertEquals("agent-123", AgentDraftGenerator.normalizeSlug("123"));
        assertEquals("my-agent", AgentDraftGenerator.normalizeSlug(" My Agent "));
        assertTrue(AgentDraftGenerator.normalizeSlug("").isEmpty());
    }
}
