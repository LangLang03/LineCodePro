package cn.lineai.ui.util;

import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMode;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public final class SlashCommandCatalogTest {

    @Test
    public void filterMainReturnsAllForEmptyQuery() {
        List<SlashCommandCatalog.Definition> all = SlashCommandCatalog.filterMain("");
        Assert.assertEquals(5, all.size());
        Assert.assertEquals("/chat", all.get(0).token);
        Assert.assertEquals("/model", all.get(4).token);
    }

    @Test
    public void filterMainMatchesByPrefixCaseInsensitively() {
        List<SlashCommandCatalog.Definition> matches = SlashCommandCatalog.filterMain("PL");
        Assert.assertEquals(1, matches.size());
        Assert.assertEquals("/plan", matches.get(0).token);
    }

    @Test
    public void filterMainReturnsEmptyForUnknownPrefix() {
        List<SlashCommandCatalog.Definition> matches = SlashCommandCatalog.filterMain("zz");
        Assert.assertTrue(matches.isEmpty());
    }

    @Test
    public void parseReturnsModeForKnownModeTokens() {
        SlashCommandCatalog.Parsed chat = SlashCommandCatalog.parse("/chat");
        Assert.assertNotNull(chat);
        Assert.assertEquals(SlashCommandCatalog.Kind.MODE, chat.kind);
        Assert.assertEquals(ChatMode.CHAT, chat.mode);

        SlashCommandCatalog.Parsed plan = SlashCommandCatalog.parse("/plan");
        Assert.assertEquals(ChatMode.PLAN, plan.mode);

        SlashCommandCatalog.Parsed agent = SlashCommandCatalog.parse("/agent");
        Assert.assertEquals(ChatMode.AGENT, agent.mode);

        SlashCommandCatalog.Parsed control = SlashCommandCatalog.parse("/control");
        Assert.assertEquals(ChatMode.CONTROL, control.mode);
    }

    @Test
    public void parseReturnsModelWithOptionalReasoningLevel() {
        SlashCommandCatalog.Parsed model = SlashCommandCatalog.parse("/model gpt-4 high");
        Assert.assertNotNull(model);
        Assert.assertEquals(SlashCommandCatalog.Kind.MODEL, model.kind);
        Assert.assertEquals("gpt-4", model.modelId);
        Assert.assertEquals(AiBehaviorSettings.REASONING_HIGH, model.reasoningEffort);

        SlashCommandCatalog.Parsed modelNoLevel = SlashCommandCatalog.parse("/model gpt-4");
        Assert.assertNotNull(modelNoLevel);
        Assert.assertEquals("gpt-4", modelNoLevel.modelId);
        Assert.assertNull(modelNoLevel.reasoningEffort);
    }

    @Test
    public void parseIgnoresInvalidReasoningLevelButKeepsModelId() {
        SlashCommandCatalog.Parsed model = SlashCommandCatalog.parse("/model gpt-4 bogus");
        Assert.assertNotNull(model);
        Assert.assertEquals("gpt-4", model.modelId);
        Assert.assertNull(model.reasoningEffort);
    }

    @Test
    public void parseReturnsNullForUnknownCommand() {
        Assert.assertNull(SlashCommandCatalog.parse("/foo bar"));
    }

    @Test
    public void parseReturnsNullForModelWithoutId() {
        Assert.assertNull(SlashCommandCatalog.parse("/model"));
        Assert.assertNull(SlashCommandCatalog.parse("/model "));
    }

    @Test
    public void parseReturnsNullForNonSlashInput() {
        Assert.assertNull(SlashCommandCatalog.parse("hello"));
        Assert.assertNull(SlashCommandCatalog.parse(""));
        Assert.assertNull(SlashCommandCatalog.parse(null));
    }

    @Test
    public void filterModelIdsMatchesByPrefix() {
        List<String> ids = java.util.Arrays.asList("gpt-4", "gpt-3.5", "claude-3");
        List<String> matches = SlashCommandCatalog.filterModelIds(ids, "gp");
        Assert.assertEquals(2, matches.size());
        Assert.assertTrue(matches.contains("gpt-4"));
        Assert.assertTrue(matches.contains("gpt-3.5"));
    }

    @Test
    public void filterModelIdsReturnsAllForEmptyQuery() {
        List<String> ids = java.util.Arrays.asList("a", "b", "c");
        List<String> matches = SlashCommandCatalog.filterModelIds(ids, "");
        Assert.assertEquals(3, matches.size());
    }

    @Test
    public void filterModelIdsReturnsEmptyForNullInput() {
        Assert.assertTrue(SlashCommandCatalog.filterModelIds(null, "x").isEmpty());
    }

    @Test
    public void filterReasoningLevelsMatchesPrefix() {
        List<String> matches = SlashCommandCatalog.filterReasoningLevels("h");
        Assert.assertEquals(1, matches.size());
        Assert.assertEquals(AiBehaviorSettings.REASONING_HIGH, matches.get(0));

        List<String> lowMatches = SlashCommandCatalog.filterReasoningLevels("lo");
        Assert.assertEquals(1, lowMatches.size());
        Assert.assertEquals(AiBehaviorSettings.REASONING_LOW, lowMatches.get(0));
    }

    @Test
    public void filterReasoningLevelsReturnsAllForEmptyQuery() {
        Assert.assertEquals(6, SlashCommandCatalog.filterReasoningLevels("").size());
    }

    @Test
    public void reasoningLevelsExposeAllStandardValues() {
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_OFF));
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_AUTO));
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_LOW));
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_MEDIUM));
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_HIGH));
        Assert.assertTrue(SlashCommandCatalog.REASONING_LEVELS.contains(AiBehaviorSettings.REASONING_MAX));
    }
}
