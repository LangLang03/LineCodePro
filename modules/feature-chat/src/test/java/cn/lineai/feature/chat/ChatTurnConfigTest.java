package cn.lineai.feature.chat;

import java.util.Arrays;
import java.util.Collections;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.TodoItem;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ChatTurnConfigTest {

    private AgentExtension agent(boolean enabled) {
        return AgentExtension.builder()
                .name("Test Fixer")
                .slug("test-fixer")
                .trigger("when tests fail")
                .prompt("Fix failing tests.")
                .toolNames(Arrays.asList("read_file", "edit_file"))
                .enabled(enabled)
                .build();
    }

    @Test public void agentSectionIncludesOnlyEnabledAgents() {
        String section = ChatTurnConfig.agentSection(Arrays.asList(
                agent(true), AgentExtension.builder().name("Hidden").slug("hidden")
                        .prompt("secret").enabled(false).build()));
        assertTrue(section.contains("Test Fixer"));
        assertTrue(section.contains("when tests fail"));
        assertTrue(section.contains("read_file"));
        assertFalse(section.contains("Hidden"));
    }

    @Test public void emptyOrMissingAgentsProduceNoSection() {
        assertEquals("", ChatTurnConfig.agentSection(null));
        assertEquals("", ChatTurnConfig.agentSection(Collections.<AgentExtension>emptyList()));
        assertEquals("", ChatTurnConfig.agentSection(
                Collections.singletonList(agent(false))));
    }

    @Test public void skillSectionIncludesOnlyEnabledSkills() {
        Skill enabled = Skill.builder().name("Review").description("Android review")
                .content("# Review\nCheck native views.").enabled(true).build();
        Skill disabled = Skill.builder().name("Hidden").content("secret").enabled(false).build();
        String section = ChatTurnConfig.skillSection(Arrays.asList(enabled, disabled));
        assertTrue(section.contains("Review"));
        assertTrue(section.contains("Check native views."));
        assertFalse(section.contains("Hidden"));
    }

    @Test public void todoSectionMarksStatuses() {
        String section = ChatTurnConfig.todoSection(Arrays.asList(
                new TodoItem("done", TodoItem.Status.COMPLETED),
                new TodoItem("now", TodoItem.Status.IN_PROGRESS),
                new TodoItem("later", TodoItem.Status.PENDING)));
        assertTrue(section.contains("[x] done"));
        assertTrue(section.contains("[~] now"));
        assertTrue(section.contains("[ ] later"));
    }

    @Test public void systemPromptFallsBackToAgentSectionWithoutAssembler() {
        String section = ChatTurnConfig.agentSection(
                Collections.singletonList(agent(true)));
        assertEquals(section, ChatTurnConfig.systemPrompt(null, null, null, section, "", ""));
    }
}
