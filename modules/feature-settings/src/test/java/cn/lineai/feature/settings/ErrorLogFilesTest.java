package cn.lineai.feature.settings;

import android.app.Application;
import android.net.Uri;

import java.io.File;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;

import cn.lineai.data.api.ErrorLog;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.RuntimeEnvironment;
import org.robolectric.annotation.Config;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class ErrorLogFilesTest {

    private Application context() {
        return RuntimeEnvironment.getApplication();
    }

    @Test public void writesTheEntryUnderTheErrorLogDirectory() throws Exception {
        ErrorLog entry = new ErrorLog("log-1", "request failed",
                "HTTP 500 from example.com", "stack line one\nstack line two", 1700000000000L);
        File file = ErrorLogFiles.write(context(), entry);
        assertTrue(file.isFile());
        assertEquals(ErrorLogFiles.directory(context()).getCanonicalPath(),
                file.getParentFile().getCanonicalPath());
        assertTrue(file.getName().endsWith(".txt"));
        assertTrue(file.getName().startsWith("request_failed-1700000000000"));
        String body = new String(Files.readAllBytes(file.toPath()), StandardCharsets.UTF_8);
        assertTrue(body.contains("request failed"));
        assertTrue(body.contains("HTTP 500 from example.com"));
        assertTrue(body.contains("stack line two"));
        assertTrue(body.contains(ErrorLogFiles.dateLine(context(), entry)));
    }

    @Test public void sanitizesUnsafeTitlesIntoAPlainFileName() {
        ErrorLog entry = new ErrorLog("log-2", "../../escape/me", "", "detail", 42L);
        String name = ErrorLogFiles.fileName(entry);
        assertFalse(name.contains("/"));
        assertFalse(name.contains("\\"));
        assertFalse(name.startsWith("."));
        assertTrue(name.endsWith(".txt"));
    }

    @Test public void resolveOnlyAcceptsPlainTextFilesInTheLogDirectory() {
        File directory = ErrorLogFiles.directory(context());
        if (!directory.isDirectory()) assertTrue(directory.mkdirs());
        File file = new File(directory, "visible.txt");
        try {
            Files.write(file.toPath(), "body".getBytes(StandardCharsets.UTF_8));
        } catch (Exception error) {
            throw new AssertionError(error);
        }
        assertNotNull(ErrorLogFiles.resolve(context(), "visible.txt"));
        assertNull(ErrorLogFiles.resolve(context(), "../visible.txt"));
        assertNull(ErrorLogFiles.resolve(context(), "visible.md"));
        assertNull(ErrorLogFiles.resolve(context(), "missing.txt"));
        assertNull(ErrorLogFiles.resolve(context(), null));
    }

    @Test public void buildsAContentUriForTheSharedProviderAuthority() throws Exception {
        File file = new File(ErrorLogFiles.directory(context()), "entry.txt");
        Uri uri = ErrorLogFiles.uriFor(context(), file);
        assertEquals("content", uri.getScheme());
        assertEquals(context().getPackageName() + ErrorLogFiles.AUTHORITY_SUFFIX,
                uri.getAuthority());
        assertEquals("entry.txt", uri.getLastPathSegment());
    }

    @Test public void fallsBackToTheStoredSubtitleWithoutATimestamp() {
        ErrorLog entry = new ErrorLog("log-3", "title", "code-42", "detail", 0L);
        assertEquals("code-42", ErrorLogFiles.dateLine(context(), entry));
    }
}
