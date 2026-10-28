package cn.lineai.runtime;

import java.io.File;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.core.model.Conversation;
import cn.lineai.core.model.MemoryItem;
import cn.lineai.core.model.Project;
import cn.lineai.core.model.Skill;
import cn.lineai.core.model.UiPreferences;
import cn.lineai.data.api.ConversationStore;
import cn.lineai.data.api.MemoryStore;
import cn.lineai.data.api.ProjectStore;
import cn.lineai.data.api.SettingsStore;
import cn.lineai.data.api.SkillStore;
import cn.lineai.engine.prompt.PromptCatalog;
import cn.lineai.engine.prompt.PromptId;
import cn.lineai.engine.prompt.PromptOverrides;
import cn.lineai.engine.prompt.PromptStore;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class LearningContextProviderTest {

    private final Map<String, String> settings = new HashMap<>();
    private final File skillsRoot = new File("/data/app/skills");

    private final SettingsStore settingsStore = new SettingsStore() {
        public String get(String key, String fallback) {
            return settings.containsKey(key) ? settings.get(key) : fallback;
        }
        public int getInt(String key, int fallback) { return fallback; }
        public void put(String key, String value) { settings.put(key, value); }
        public void remove(String key) { settings.remove(key); }
        public void invalidate() { }
        public Map<String, String> all() { return settings; }
    };

    private final MemoryStore memoryStore = new MemoryStore() {
        public List<MemoryItem> list(String projectId) {
            return Collections.singletonList(new MemoryItem("m1", MemoryItem.SCOPE_PROJECT,
                    "/workspace/project", "Build", "Remember the build command", "manual",
                    1.0, 1, 1, 0, 0));
        }
        public void save(MemoryItem memory) { }
        public void delete(String id) { }
        public void deleteAll(List<String> ids) { }
    };

    private final ConversationStore conversationStore = new ConversationStore() {
        public List<Conversation> list() {
            List<Conversation> items = new ArrayList<>();
            items.add(new Conversation("c1", "Refactor chat", "", 1, 1, 2));
            return items;
        }
        public Conversation create(String title) { return null; }
        public void rename(String id, String title) { }
        public void delete(String id) { }
        public void touch(String id, long nowMs, int messageCount) { }
    };

    private final SkillStore skillStore = new SkillStore() {
        public List<Skill> list() { return enabled(); }
        public List<Skill> enabled() {
            return Collections.singletonList(Skill.builder().id("review").name("Review")
                    .description("Checks diffs").content("Review the change").build());
        }
        public Skill save(Skill input) { return input; }
        public Skill importSkill(InputStream input, String displayName) { return null; }
        public void setEnabled(String id, boolean enabled) { }
        public void delete(String id) { }
    };

    private final ProjectStore projectStore = new ProjectStore() {
        public List<Project> list() { return Collections.emptyList(); }
        public Project selected() {
            return new Project("p1", "Project", "/workspace/project", true, 1, 1);
        }
        public Project ensureDefault(File directory) { return null; }
        public Project create(String name, String path) { return null; }
        public void select(String id) { }
        public void delete(String id) { }
    };

    private LearningContextProvider provider() {
        Map<PromptId, String> templates = new HashMap<>();
        templates.put(PromptId.LEARNING_CONTEXT, "{{WORKING_MEMORY_SECTION}}"
                + "{{MEMORY_SECTION}}{{HISTORY_SECTION}}{{SKILL_PATHS_SECTION}}"
                + "{{SKILLS_SECTION}}{{PRIVATE_BOUNDARY_SECTION}}");
        PromptStore prompts = new PromptStore(PromptCatalog.of(templates), PromptOverrides.none());
        return new LearningContextProvider(settingsStore, memoryStore, conversationStore,
                skillStore, projectStore, prompts, skillsRoot);
    }

    @Test public void disabledLearningModeAddsNoContext() {
        assertTrue(provider().build().isEmpty());
    }

    @Test public void enabledLearningModeIncludesMemoryHistorySkillsAndPaths() {
        settings.put(UiPreferences.Field.LEARNING_MODE.key, "true");
        String context = provider().build();
        assertTrue(context.contains("Remember the build command"));
        assertTrue(context.contains("Refactor chat"));
        assertTrue(context.contains("Review: Checks diffs"));
        assertTrue(context.contains(skillsRoot.getAbsolutePath()));
        assertTrue(context.contains("/workspace/project/.linecode/skills"));
        assertTrue(context.contains("Private directory boundary"));
    }
}
