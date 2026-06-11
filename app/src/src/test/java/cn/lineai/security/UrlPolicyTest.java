package cn.lineai.security;

import org.junit.Assert;
import org.junit.Test;

public final class UrlPolicyTest {
    @Test
    public void acceptsHttpsUrls() {
        Assert.assertEquals(
                "https://example.com/path",
                UrlPolicy.normalizeHttpOrLocalCleartextUrl(" https://example.com/path ")
        );
    }

    @Test
    public void acceptsLocalCleartextHttpUrls() {
        Assert.assertEquals(
                "http://127.0.0.1:8080",
                UrlPolicy.normalizeHttpOrLocalCleartextUrl("http://127.0.0.1:8080")
        );
        Assert.assertEquals(
                "http://10.0.2.2:3000",
                UrlPolicy.normalizeHttpOrLocalCleartextUrl("http://10.0.2.2:3000")
        );
    }

    @Test
    public void rejectsRemoteCleartextHttpUrls() {
        Assert.assertEquals("", UrlPolicy.normalizeHttpOrLocalCleartextUrl("http://example.com"));
        try {
            UrlPolicy.requireHttpOrLocalCleartextUrl("http://example.com", "URL");
            Assert.fail("Expected remote HTTP URL to be rejected.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("HTTP 明文"));
        }
    }

    @Test
    public void rejectsNonHttpSchemes() {
        Assert.assertEquals("", UrlPolicy.normalizeHttpOrHttpsUrl("javascript:alert(1)"));
        Assert.assertEquals("", UrlPolicy.normalizeHttpOrHttpsUrl("file:///tmp/index.html"));
    }
}
