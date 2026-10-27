package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ThemePaletteTest {

    @Test
    public void darkPaletteKeepsLegacyValues() {
        ThemePalette dark = ThemePalette.forMode(ThemePalette.MODE_DARK);
        assertEquals(0xFF171819, dark.bg);
        assertEquals(0xFF242629, dark.surface);
        assertEquals(0xFFE5E9EE, dark.accent);
        assertEquals(0xFF242629, dark.userBubble);
        assertTrue(dark.isDark());
    }

    @Test
    public void lightPaletteIsNotDark() {
        ThemePalette light = ThemePalette.forMode(ThemePalette.MODE_LIGHT);
        assertFalse(light.isDark());
        assertEquals(0xFFFCFCFD, light.bg);
    }

    @Test
    public void customWithReplacesOnlyRequestedKey() {
        ThemePalette base = ThemePalette.dark();
        ThemePalette changed = base.with(ThemePalette.KEY_ACCENT, 0xFFFF0000);
        assertEquals(0xFFFF0000, changed.accent);
        assertEquals(base.bg, changed.bg);
        assertEquals(base.text, changed.text);
    }

    @Test
    public void unknownKeyReturnsSameInstance() {
        ThemePalette base = ThemePalette.dark();
        assertTrue(base == base.with("nope", 0));
    }

    @Test
    public void normalizeModeFallsBackToSystem() {
        assertEquals(ThemePalette.MODE_SYSTEM, ThemePalette.normalizeMode("bogus"));
        assertEquals(ThemePalette.MODE_DARK, ThemePalette.normalizeMode("dark"));
    }

    @Test
    public void allModesProduceDistinctBackgrounds() {
        String[] modes = {ThemePalette.MODE_DARK, ThemePalette.MODE_LIGHT, ThemePalette.MODE_COFFEE,
                ThemePalette.MODE_VSCODE, ThemePalette.MODE_GITHUB_DARK, ThemePalette.MODE_GRUVBOX,
                ThemePalette.MODE_HIGH_CONTRAST};
        for (String mode : modes) {
            assertEquals(mode, ThemePalette.forMode(mode).mode);
        }
    }
}
