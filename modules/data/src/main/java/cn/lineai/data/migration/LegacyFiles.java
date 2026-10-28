package cn.lineai.data.migration;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

/** Copies the legacy on-disk Skill tree without overwriting newer files. */
public final class LegacyFiles {

    private LegacyFiles() {
    }

    public static void copySkills(File legacyRoot, File targetRoot) {
        if (legacyRoot == null || !legacyRoot.isDirectory()) return;
        copyTree(legacyRoot, targetRoot);
    }

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
