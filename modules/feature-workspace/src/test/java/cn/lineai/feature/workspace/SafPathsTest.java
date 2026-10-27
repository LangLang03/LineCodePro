package cn.lineai.feature.workspace;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class SafPathsTest {

    @Test public void mapsPrimaryVolumeTrees() {
        assertEquals("/storage/emulated/0/Projects/Demo", SafPaths.toFileSystemPath(
                "content://com.android.externalstorage.documents/tree/primary%3AProjects%2FDemo"));
    }

    @Test public void mapsDocumentUrisAndSdCards() {
        assertEquals("/storage/emulated/0/Download/file.txt", SafPaths.toFileSystemPath(
                "content://com.android.externalstorage.documents/document/primary%3ADownload%2Ffile.txt"));
        assertEquals("/storage/1234-ABCD/repo", SafPaths.toFileSystemPath(
                "content://com.android.externalstorage.documents/tree/1234-ABCD%3Arepo"));
    }

    @Test public void mapsDownloadsAuthorityAndRawPaths() {
        assertEquals("/storage/emulated/0/Download",
                SafPaths.toFileSystemPath("content://com.android.providers.downloads.documents/tree/downloads"));
        assertEquals("/data/local/tmp/x", SafPaths.toFileSystemPath(
                "content://com.android.externalstorage.documents/document/raw%3A%2Fdata%2Flocal%2Ftmp%2Fx"));
    }

    @Test public void rejectsUnsupportedUris() {
        assertEquals("", SafPaths.toFileSystemPath(null));
        assertEquals("", SafPaths.toFileSystemPath("file:///sdcard/x"));
        assertEquals("", SafPaths.toFileSystemPath(
                "content://com.android.externalstorage.documents/tree/unknownvolume%3Ax"));
    }
}
