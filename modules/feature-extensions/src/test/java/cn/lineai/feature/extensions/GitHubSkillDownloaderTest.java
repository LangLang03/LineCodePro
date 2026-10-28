package cn.lineai.feature.extensions;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class GitHubSkillDownloaderTest {

    @Test public void buildsArchiveUrls() throws Exception {
        assertEquals("https://github.com/owner/repo/archive/refs/heads/main.zip",
                GitHubSkillDownloader.archiveUrl("https://github.com/owner/repo", "main"));
        assertEquals("https://github.com/owner/repo/archive/refs/heads/feature/x.zip",
                GitHubSkillDownloader.archiveUrl("https://github.com/owner/repo/tree/feature/x", "main"));
        assertEquals("https://github.com/owner/repo/archive/refs/heads/main.zip",
                GitHubSkillDownloader.archiveUrl("https://github.com/owner/repo.git", "main"));
    }

    @Test public void acceptsDirectZipAndRejectsOtherHosts() throws Exception {
        assertEquals("https://github.com/owner/repo/archive/main.zip",
                GitHubSkillDownloader.archiveUrl("https://github.com/owner/repo/archive/main.zip", "main"));
        try {
            GitHubSkillDownloader.archiveUrl("https://example.com/repo", "main");
            throw new AssertionError("expected rejection");
        } catch (java.io.IOException expected) {
            // only GitHub is allowed
        }
    }
}
