package cn.lineai.storage;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import java.io.File;
import java.nio.file.Files;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public final class LocalDirectoryPathTest {
    @Rule public TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void resolvesExistingWritableDirectoryUnderVolume() throws Exception {
        File volume = temporaryFolder.newFolder("volume");
        File directory = new File(volume, "download/Browser");
        assertTrue(directory.mkdirs());

        assertEquals(directory.getCanonicalPath(),
                LocalDirectoryPath.resolve(volume, "download/Browser"));
    }

    @Test
    public void rejectsTraversalAndMalformedSegments() throws Exception {
        File volume = temporaryFolder.newFolder("volume");
        temporaryFolder.newFolder("outside");

        assertNull(LocalDirectoryPath.resolve(volume, "../outside"));
        assertNull(LocalDirectoryPath.resolve(volume, "a/../outside"));
        assertNull(LocalDirectoryPath.resolve(volume, "a//outside"));
        assertNull(LocalDirectoryPath.resolve(volume, "a\\outside"));
    }

    @Test
    public void rejectsSymlinkOutsideVolume() throws Exception {
        File volume = temporaryFolder.newFolder("volume");
        File outside = temporaryFolder.newFolder("outside");
        Files.createSymbolicLink(new File(volume, "linked").toPath(), outside.toPath());

        assertNull(LocalDirectoryPath.resolve(volume, "linked"));
    }
}
