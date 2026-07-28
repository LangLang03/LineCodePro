package cn.lineai.tool;

import org.json.JSONException;
import org.json.JSONObject;
import org.junit.Assert;
import org.junit.Test;

public final class ToolArgsCleanerTest {

    @Test
    public void emptyAndNullReturnEmptyObject() {
        Assert.assertEquals("{}", ToolArgsCleaner.clean(null));
        Assert.assertEquals("{}", ToolArgsCleaner.clean(""));
        Assert.assertEquals("{}", ToolArgsCleaner.clean("   "));
    }

    @Test
    public void markdownFenceWithLanguageIdentifier() throws JSONException {
        String raw = "```json\n{\"a\": 1}\n```";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"a\": 1}", cleaned);
        Assert.assertEquals(1, new JSONObject(cleaned).optInt("a"));
    }

    @Test
    public void markdownFenceWithoutLanguageIdentifier() throws JSONException {
        String raw = "```\n{\"b\": 2}\n```";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"b\": 2}", cleaned);
        Assert.assertEquals(2, new JSONObject(cleaned).optInt("b"));
    }

    @Test
    public void inlineSingleLineComment() throws JSONException {
        String raw = "{\"a\": 1} // trailing comment";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"a\": 1} ", cleaned);
        Assert.assertEquals(1, new JSONObject(cleaned).optInt("a"));
    }

    @Test
    public void multiLineComment() throws JSONException {
        String raw = "/* outer */ {\"a\": /* inner */ 1}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals(" {\"a\":  1}", cleaned);
        Assert.assertEquals(1, new JSONObject(cleaned).optInt("a"));
    }

    @Test
    public void commentInsideStringIsPreserved() throws JSONException {
        String raw = "{\"url\": \"http://example.com/path\"}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"url\": \"http://example.com/path\"}", cleaned);
        Assert.assertEquals("http://example.com/path", new JSONObject(cleaned).optString("url"));
    }

    @Test
    public void blockCommentInsideStringIsPreserved() throws JSONException {
        String raw = "{\"pattern\": \"a /* not a comment */ b\"}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"pattern\": \"a /* not a comment */ b\"}", cleaned);
        Assert.assertEquals("a /* not a comment */ b", new JSONObject(cleaned).optString("pattern"));
    }

    @Test
    public void trailingCommaInObject() throws JSONException {
        String raw = "{\"a\":1,}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"a\":1}", cleaned);
        Assert.assertEquals(1, new JSONObject(cleaned).optInt("a"));
    }

    @Test
    public void trailingCommaInArray() throws JSONException {
        String raw = "[1, 2, 3,]";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("[1, 2, 3]", cleaned);
        Assert.assertEquals(3, new JSONObject("{\"arr\":" + cleaned + "}").optJSONArray("arr").length());
    }

    @Test
    public void simpleSingleQuotedObject() throws JSONException {
        String raw = "{'a': 1, 'b': 'hello'}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"a\": 1, \"b\": \"hello\"}", cleaned);
        JSONObject obj = new JSONObject(cleaned);
        Assert.assertEquals(1, obj.optInt("a"));
        Assert.assertEquals("hello", obj.optString("b"));
    }

    @Test
    public void unescapedWhitespaceControlCharactersAreEscaped() throws JSONException {
        String raw = "{\"a\":\"line1\nline2\ttab\"}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"a\":\"line1\\nline2\\ttab\"}", cleaned);
        Assert.assertEquals("line1\nline2\ttab", new JSONObject(cleaned).optString("a"));
    }

    @Test
    public void escapedControlSequencesArePreserved() throws JSONException {
        String raw = "{\"a\":\"line1\\nline2\\ttab\"}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals(raw, cleaned);
        Assert.assertEquals("line1\nline2\ttab", new JSONObject(cleaned).optString("a"));
    }

    @Test
    public void validJsonIsUnchanged() throws JSONException {
        String raw = "{\"name\":\"test\",\"count\":42,\"items\":[\"x\",\"y\"]}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals(raw, cleaned);
        JSONObject obj = new JSONObject(cleaned);
        Assert.assertEquals("test", obj.optString("name"));
        Assert.assertEquals(42, obj.optInt("count"));
        Assert.assertEquals(2, obj.optJSONArray("items").length());
    }

    @Test
    public void nestedTrailingCommasAreRemoved() {
        String raw = "[1, [2,],]";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("[1, [2]]", cleaned);
    }

    @Test
    public void truncatedObjectGetsClosingBrace() throws JSONException {
        String raw = "{\"agents\": [{\"id\": \"a\", \"type\": \"sub-coding\", \"description\": \"d\", \"prompt\": \"p\"}]";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"agents\": [{\"id\": \"a\", \"type\": \"sub-coding\", \"description\": \"d\", \"prompt\": \"p\"}]}", cleaned);
        JSONObject obj = new JSONObject(cleaned);
        Assert.assertEquals(1, obj.optJSONArray("agents").length());
    }

    @Test
    public void truncatedArrayGetsClosingBracket() throws JSONException {
        String raw = "{\"items\": [1, 2";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"items\": [1, 2]}", cleaned);
        Assert.assertEquals(2, new JSONObject(cleaned).optJSONArray("items").length());
    }

    @Test
    public void truncatedStringGetsClosingQuote() throws JSONException {
        String raw = "{\"name\": \"abc";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"name\": \"abc\"}", cleaned);
        Assert.assertEquals("abc", new JSONObject(cleaned).optString("name"));
    }

    @Test
    public void truncatedNestedStructuresAreBalanced() throws JSONException {
        String raw = "{\"outer\": {\"inner\": [1";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals("{\"outer\": {\"inner\": [1]}}", cleaned);
        Assert.assertEquals(1, new JSONObject(cleaned).optJSONObject("outer").optJSONArray("inner").getInt(0));
    }

    @Test
    public void truncatedAgentPipelineFromLog() throws JSONException {
        String raw = "{\"agents\": [{\"id\": \"prove_pythagoras\", \"type\": \"sub-coding\", \"description\": \"证明勾股定理\", \"prompt\": \"p\"}, {" +
                "\"id\": \"prove_primes_infinite\", \"type\": \"sub-coding\", \"description\": \"证明质数无穷\", \"prompt\": \"p\"}, {" +
                "\"id\": \"verify_proofs\", \"type\": \"sub-coding\", \"description\": \"验证两个证明\", \"prompt\": \"p\", \"read_scope\": [\"a.txt\"], \"write_scope\": [\"b.txt\"], \"depends_on\": [\"prove_pythagoras\", \"prove_primes_infinite\"]}";
        String cleaned = ToolArgsCleaner.clean(raw);
        JSONObject obj = new JSONObject(cleaned);
        Assert.assertEquals(3, obj.optJSONArray("agents").length());
        Assert.assertEquals("verify_proofs", obj.optJSONArray("agents").optJSONObject(2).optString("id"));
    }

    @Test
    public void validJsonUnchangedByBalancing() throws JSONException {
        String raw = "{\"name\":\"test\",\"count\":42,\"items\":[\"x\",\"y\"]}";
        String cleaned = ToolArgsCleaner.clean(raw);
        Assert.assertEquals(raw, cleaned);
    }
}
