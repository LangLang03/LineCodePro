package cn.lineai.feature.terminal;

import cn.lineai.core.model.SshConfig;
import org.junit.Test;
import static org.junit.Assert.*;

public class TermuxClientParseTest {

    private static final String OUTPUT =
            "LINEAI_TERMUX_USERNAME=u0_a123\n"
                    + "LINEAI_TERMUX_HOST=127.0.0.1\n"
                    + "LINEAI_TERMUX_PORT=8022\n"
                    + "LINEAI_TERMUX_SHELL=/data/data/com.termux/files/usr/bin/bash\n"
                    + "LINEAI_TERMUX_RC=/data/data/com.termux/files/home/.bashrc\n"
                    + "LINEAI_PRIVATE_KEY_BEGIN\n"
                    + "-----BEGIN OPENSSH PRIVATE KEY-----\n"
                    + "AAAA\n"
                    + "-----END OPENSSH PRIVATE KEY-----\n"
                    + "LINEAI_PRIVATE_KEY_END\n";

    @Test public void parsesEveryFieldFromTheSetupOutput() {
        TermuxClient.SetupResult result = TermuxClient.parse(OUTPUT);
        assertEquals("u0_a123", result.config.username);
        assertEquals("127.0.0.1", result.config.host);
        assertEquals(8022, result.config.port);
        assertTrue(result.config.privateKey.contains("BEGIN OPENSSH PRIVATE KEY"));
        assertEquals("/data/data/com.termux/files/usr/bin/bash", result.shell);
        assertEquals("/data/data/com.termux/files/home/.bashrc", result.rcPath);
        assertTrue(result.config.isConfigured());
    }

    @Test public void fallsBackToLoopbackAndDefaultPort() {
        TermuxClient.SetupResult result = TermuxClient.parse(
                "LINEAI_TERMUX_USERNAME=u\nLINEAI_TERMUX_PORT=abc\n"
                        + "LINEAI_PRIVATE_KEY_BEGIN\nKEY\nLINEAI_PRIVATE_KEY_END\n");
        assertEquals(SshConfig.DEFAULT_HOST, result.config.host);
        assertEquals(SshConfig.DEFAULT_PORT, result.config.port);
    }

    @Test public void missingKeyFailsWithTheKeyRedacted() {
        try {
            TermuxClient.parse("LINEAI_TERMUX_USERNAME=u\nno key here");
            fail("expected failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Unable to parse"));
        }
    }

    @Test public void redactRemovesPrivateKeyMaterial() {
        String redacted = TermuxClient.redact(OUTPUT);
        assertFalse(redacted.contains("AAAA"));
        assertTrue(redacted.contains("[redacted]"));
        assertTrue(redacted.contains("LINEAI_TERMUX_USERNAME=u0_a123"));
    }

    @Test public void allowExternalAppsCommandMentionsTheProperty() {
        assertTrue(cn.lineai.core.model.TermuxCommands.ALLOW_EXTERNAL_APPS
                .contains("allow-external-apps=true"));
    }
}
