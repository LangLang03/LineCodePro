package cn.lineai.arch;

import java.io.File;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.junit.Test;

import static org.junit.Assert.assertTrue;

/**
 * LegacyMigration remaps old settings keys onto the live ones with raw literals: the data
 * layer cannot see feature-chat, so nothing else ties the two together. A rename on either
 * side would silently orphan migrated user settings — this test is the only alarm.
 */
public class SettingsKeyParityTest {

    private static String read(String path) {
        return Modules.read(new File(Modules.ROOT, path));
    }

    @Test
    public void migrationKeysMatchFeatureChatConstants() {
        String toolModes = read(
                "modules/feature-chat/src/main/java/cn/lineai/feature/chat/ToolModes.java");
        String permissionMode = constant(toolModes, "KEY");
        String chatMode = constant(toolModes, "CHAT_MODE_KEY");
        assertTrue("ToolModes.KEY not found", permissionMode != null);
        assertTrue("ToolModes.CHAT_MODE_KEY not found", chatMode != null);

        String legacy = read(
                "modules/data/src/main/java/cn/lineai/data/migration/LegacyMigration.java");
        assertTrue("LegacyMigration must keep remapping " + permissionMode,
                legacy.contains('"' + permissionMode + '"'));
        assertTrue("LegacyMigration must keep remapping " + chatMode,
                legacy.contains('"' + chatMode + '"'));
    }

    private static String constant(String source, String field) {
        Matcher matcher = Pattern.compile(
                "static final String " + field + " = \"([^\"]+)\";").matcher(source);
        return matcher.find() ? matcher.group(1) : null;
    }
}
