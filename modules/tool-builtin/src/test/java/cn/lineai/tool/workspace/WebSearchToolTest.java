package cn.lineai.tool.workspace;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

public class WebSearchToolTest {

    @Test public void formatsRssItems() {
        String xml = "<?xml version=\"1.0\"?><rss><channel>"
                + "<item><title>One &amp; Two</title><link>https://example.com/1</link>"
                + "<description><![CDATA[<b>First</b> result]]></description></item>"
                + "<item><title>Second</title><link>https://example.com/2</link></item>"
                + "</channel></rss>";
        String formatted = WebSearchTool.formatRss(xml);
        assertTrue(formatted.contains("1. One & Two"));
        assertTrue(formatted.contains("https://example.com/1"));
        assertTrue(formatted.contains("First result"));
        assertTrue(formatted.contains("2. Second"));
    }

    @Test public void formatsJsonResultsFromNestedArrays() throws Exception {
        String json = "{\"results\":[{\"title\":\"A\",\"url\":\"https://a\","
                + "\"content\":\"alpha\"}]}";
        String formatted = WebSearchTool.formatJsonResults(json,
                new String[] {"results"}, "title", "url", "content");
        assertTrue(formatted.contains("1. A"));
        assertTrue(formatted.contains("https://a"));
        assertTrue(formatted.contains("alpha"));
    }

    @Test public void noResultsIsExplicit() throws Exception {
        assertTrue(WebSearchTool.formatJsonResults("{}", new String[] {"results"},
                "title", "url", "snippet").contains("No results"));
    }
}
