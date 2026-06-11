package cn.lineai.workspace;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class SafPathResolverTest {
    private final SafPathResolver resolver = new SafPathResolver();

    @Test
    public void mapsPrimaryExternalStorageTree() {
        assertEquals(
                "/storage/emulated/0/Download/Code",
                resolver.treeUriToFileSystemPath("content://com.android.externalstorage.documents/tree/primary%3ADownload%2FCode")
        );
    }

    @Test
    public void mapsDownloadsProviderTree() {
        assertEquals(
                "/storage/emulated/0/Download/LineCode",
                resolver.treeUriToFileSystemPath("content://com.android.providers.downloads.documents/tree/downloads%2FLineCode")
        );
    }

    @Test
    public void mapsRawPath() {
        assertEquals(
                "/storage/1234-ABCD/Project",
                resolver.treeUriToFileSystemPath("content://com.android.externalstorage.documents/tree/raw%3A%2Fstorage%2F1234-ABCD%2FProject")
        );
    }
}
