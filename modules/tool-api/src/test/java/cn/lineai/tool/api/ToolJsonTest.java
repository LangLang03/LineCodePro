package cn.lineai.tool.api;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

/** Lenient argument parsing: strict JSON must stay unchanged, model noise must still parse. */
public class ToolJsonTest {

    @Test
    public void keepsWellFormedArgumentsUntouched() {
        ToolJson json = ToolJson.tryParse("{\"path\":\"a.txt\",\"limit\":3}");
        assertNotNull(json);
        assertEquals("a.txt", json.text("path"));
        assertEquals(3, json.integer("limit", 0));
    }

    @Test
    public void readsFencedAndPrefixedArguments() {
        ToolJson fenced = ToolJson.tryParse("```json\n{\"path\":\"a.txt\"}\n```");
        assertNotNull(fenced);
        assertEquals("a.txt", fenced.text("path"));
        ToolJson prefixed = ToolJson.tryParse("Sure, I will read it.\n{\"path\":\"b.txt\"}");
        assertNotNull(prefixed);
        assertEquals("b.txt", prefixed.text("path"));
    }

    @Test
    public void keepsCodeFencesInsideArgumentStrings() {
        ToolJson json = ToolJson.tryParse(
                "{\"path\":\"a.md\",\"content\":\"```js\\nfoo\\n```\"}");
        assertNotNull(json);
        assertEquals("a.md", json.text("path"));
        assertEquals("```js\nfoo\n```", json.text("content"));
    }

    @Test
    public void repairsSingleQuotesUnquotedKeysAndPythonLiterals() {
        ToolJson json = ToolJson.tryParse("{path: 'a.txt', flag: True, note: None}");
        assertNotNull(json);
        assertEquals("a.txt", json.text("path"));
        assertTrue(json.pretty(), json.flag("flag"));
        assertTrue(json.pretty(), json.text("note").isEmpty());
    }

    @Test
    public void repairsSmartQuotesCommentsAndTrailingCommas() {
        ToolJson json = ToolJson.tryParse(
                "{\u201cpath\u201d: \u201ca.txt\u201d, // target\n /* limit */ \u201climit\u201d: 2,}");
        assertNotNull(json);
        assertEquals("a.txt", json.text("path"));
        assertEquals(2, json.integer("limit", 0));
    }

    @Test
    public void repairsRawNewlinesInsideStrings() {
        ToolJson json = ToolJson.tryParse("{\"content\":\"line1\nline2\",\"path\":\"a.txt\"}");
        assertNotNull(json);
        assertEquals("line1\nline2", json.text("content"));
    }

    @Test
    public void repairsTruncatedArguments() {
        ToolJson openString = ToolJson.tryParse("{\"path\":\"a.txt\",\"content\":\"unfinished");
        assertNotNull(openString);
        assertEquals("a.txt", openString.text("path"));

        ToolJson danglingValue = ToolJson.tryParse("{\"path\":\"a.txt\",\"content\":");
        assertNotNull(danglingValue);
        assertEquals("a.txt", danglingValue.text("path"));
    }

    @Test
    public void unwrapsDoubleEncodedArguments() {
        ToolJson json = ToolJson.tryParse("{\"arguments\":\"{\\\"path\\\":\\\"a.txt\\\"}\"}");
        assertNotNull(json);
        assertEquals("a.txt", json.text("path"));
    }

    @Test
    public void unwrapsSingleObjectArray() {
        ToolJson json = ToolJson.tryParse("[{\"path\":\"a.txt\"}]");
        assertNotNull(json);
        assertEquals("a.txt", json.text("path"));
    }

    @Test
    public void reportsNothingForUnusableText() {
        assertNull(ToolJson.tryParse(""));
        assertNull(ToolJson.tryParse("I cannot do that."));
        assertEquals(0, ToolJson.object("I cannot do that.").length());
    }

    @Test
    public void reportsWhetherArgumentsWereTruncated() {
        assertTrue(ToolJson.complete("{\"path\":\"a.txt\"}"));
        assertTrue(ToolJson.complete("```json\n{\"path\":\"a.txt\"}\n```"));
        assertTrue(ToolJson.complete(""));
        assertFalse(ToolJson.complete("{\"path\":\"a.txt\",\"content\":\"half"));
        assertFalse(ToolJson.complete("{\"path\":"));
    }

    @Test
    public void presentationUsesRepairedArguments() {
        ToolPresentation presentation = ToolPresentation.generic("read_file",
                "{'path': 'src/Main.java',}");
        assertEquals("src/Main.java", presentation.action);
    }
}
