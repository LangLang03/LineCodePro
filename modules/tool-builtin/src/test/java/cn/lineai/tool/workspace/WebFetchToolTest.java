package cn.lineai.tool.workspace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WebFetchToolTest {

    @Test public void extractsReadableTextFromHtml() {
        String html = "<html><head><style>.x{}</style><script>alert(1)</script></head>"
                + "<body><h1>Title</h1><p>Hello &amp; welcome</p><div>Second<br>line</div></body></html>";
        String text = WebFetchTool.htmlToText(html);
        assertTrue(text.contains("Title"));
        assertTrue(text.contains("Hello & welcome"));
        assertTrue(text.contains("Second"));
        assertTrue(text.contains("line"));
        assertFalse(text.contains("alert"));
        assertFalse(text.contains(".x"));
    }

    @Test public void allowsHttpsAndLoopbackHttpOnly() {
        assertTrue(WebFetchTool.allowedUrl("https://example.com/page"));
        assertTrue(WebFetchTool.allowedUrl("http://localhost:8080/"));
        assertTrue(WebFetchTool.allowedUrl("http://127.0.0.1/"));
        assertFalse(WebFetchTool.allowedUrl("http://example.com/"));
        assertFalse(WebFetchTool.allowedUrl("file:///etc/passwd"));
        assertFalse(WebFetchTool.allowedUrl("not a url"));
    }
}
