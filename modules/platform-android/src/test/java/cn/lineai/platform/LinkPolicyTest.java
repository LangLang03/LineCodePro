package cn.lineai.platform;

import org.junit.Test;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LinkPolicyTest {

    @Test public void httpsIsAlwaysAllowed() {
        assertTrue(LinkPolicy.allows("https://example.com/a?b=1", false));
    }

    @Test public void plainHttpNeedsTheOptIn() {
        assertFalse(LinkPolicy.allows("http://example.com", false));
        assertTrue(LinkPolicy.allows("http://example.com", true));
    }

    @Test public void loopbackHttpIsAlwaysAllowed() {
        assertTrue(LinkPolicy.allows("http://127.0.0.1:8080/x", false));
        assertTrue(LinkPolicy.allows("http://localhost/x", false));
        assertTrue(LinkPolicy.allows("http://10.0.2.2:8765/v1", false));
    }

    @Test public void rejectsOtherSchemesCredentialsAndWhitespace() {
        assertFalse(LinkPolicy.allows("javascript:alert(1)", true));
        assertFalse(LinkPolicy.allows("file:///etc/passwd", true));
        assertFalse(LinkPolicy.allows("https://user:pass@example.com", true));
        assertFalse(LinkPolicy.allows("https://example.com/ bad", true));
        assertFalse(LinkPolicy.allows(null, true));
    }
}
