package cn.lineai.data.repository;

import org.junit.Assert;
import org.junit.Test;

public final class PromptTemplateRepositoryTest {
    @Test
    public void templateIdsIncludeUserEditablePromptTemplates() {
        java.util.List<String> ids = PromptTemplateRepository.templateIds();

        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_SYSTEM_PROMPT));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_WORK_DIRECTORY));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_TONE_CODING));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_TONE_CHAT));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_LEARNING_CONTEXT));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_MEMORY_EXTRACTION));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_SKILL_EXTRACTION));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_CONTEXT_COMPACTION));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_CHAT_MODE_CHAT));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_CHAT_MODE_PLAN));
        Assert.assertTrue(ids.contains(PromptTemplateRepository.ID_CHAT_MODE_AGENT));
    }
}
