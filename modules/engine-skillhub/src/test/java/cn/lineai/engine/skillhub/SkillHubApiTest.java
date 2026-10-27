package cn.lineai.engine.skillhub;

import java.util.List;

import cn.lineai.core.model.SkillHubDetail;
import cn.lineai.core.model.SkillHubEntry;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

public class SkillHubApiTest {

    @Test public void parsesSkillList() {
        List<SkillHubEntry> entries = SkillHubApi.parseList(
                "{\"code\":0,\"data\":{\"skills\":["
                        + "{\"slug\":\"repo-review\",\"displayName\":\"Repo Review\","
                        + "\"summary\":\"Reviews repositories\","
                        + "\"owner\":{\"displayName\":\"Alice\"},"
                        + "\"latestVersion\":{\"version\":\"1.2.0\"},"
                        + "\"stats\":{\"downloads\":42}},"
                        + "{\"slug\":\"\"}]}}");
        assertEquals(1, entries.size());
        assertEquals("repo-review", entries.get(0).slug);
        assertEquals("Repo Review", entries.get(0).name);
        assertEquals("Reviews repositories", entries.get(0).description);
        assertEquals("Alice", entries.get(0).author);
        assertEquals("1.2.0", entries.get(0).version);
        assertEquals(42, entries.get(0).downloads);
    }

    @Test public void parsesDetail() {
        SkillHubDetail detail = SkillHubApi.parseDetail("repo-review",
                "{\"skill\":{\"displayName\":\"Repo Review\",\"summary\":\"Reviews\","
                        + "\"tags\":[\"android\",\"\"],\"stats\":{\"downloads\":9}},"
                        + "\"owner\":{\"displayName\":\"Alice\"},"
                        + "\"latestVersion\":{\"version\":\"2.0\"}}");
        assertEquals("Repo Review", detail.name);
        assertEquals("Alice", detail.author);
        assertEquals("2.0", detail.version);
        assertEquals(1, detail.tags.size());
        assertEquals("android", detail.tags.get(0));
    }

    @Test public void parsesComments() {
        java.util.List<cn.lineai.core.model.SkillHubComment> comments = SkillHubApi.parseComments(
                "{\"items\":[{\"id\":7,\"content\":\"nice\","
                        + "\"user\":{\"displayName\":\"Bob\"}},{\"id\":8,\"content\":\"\"}]}");
        assertEquals(1, comments.size());
        assertEquals("Bob", comments.get(0).author);
        assertEquals("nice", comments.get(0).content);
        assertEquals(7, comments.get(0).id);
    }

    @Test public void errorResponsesYieldNull() {
        assertNull(SkillHubApi.parseList("{\"code\":1,\"message\":\"nope\"}"));
        assertNull(SkillHubApi.parseList("not json"));
    }

    @Test public void emptyListIsValid() {
        assertTrue(SkillHubApi.parseList("{\"code\":0,\"data\":{}}").isEmpty());
    }
}
