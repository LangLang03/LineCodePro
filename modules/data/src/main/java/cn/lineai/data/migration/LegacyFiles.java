package cn.lineai.data.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/**
 * Copies the legacy on-disk Skill tree without overwriting newer files.
 *
 * <p>Migration must never destroy data the new app already produced, so only files that do not
 * exist in the target are written and failures on single files are logged instead of propagated.</p>
 */
public final class LegacyFiles {

    /** Not instantiable; the migration runs through static entry points. */
    private LegacyFiles() {
    }

    /**
     * Copies the legacy skill tree into the new root, skipping missing or unreadable sources.
     *
     * @param legacyRoot legacy skills directory; a null or absent root is a no-op
     * @param targetRoot new skills directory, created on demand
     */
    public static void copySkills(File legacyRoot, File targetRoot) {
        if (legacyRoot == null || !legacyRoot.isDirectory()) return;
        copyTree(legacyRoot, targetRoot);
    }

    /** Recursively copies files that do not yet exist in the target. */
    private static void copyTree(File source, File target) {
        File[] children = source.listFiles();
        if (children == null) return;
        if (!target.exists() && !target.mkdirs()) return;
        for (File child : children) {
            File next = new File(target, child.getName());
            if (child.isDirectory()) {
                copyTree(child, next);
            } else if (!next.exists()) {
                try (FileInputStream input = new FileInputStream(child);
                     FileOutputStream output = new FileOutputStream(next)) {
                    byte[] buffer = new byte[8192];
                    int count;
                    while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
                } catch (IOException error) {
                    System.err.println("Cannot migrate skill " + child + ": " + error);
                }
            }
        }
    }

}
