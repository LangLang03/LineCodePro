package cn.lineai.core.model;

import org.junit.Test;

import static org.junit.Assert.assertEquals;

public class ContextSizesTest {

    @Test public void parsesUnitSuffixesCaseInsensitively() {
        assertEquals(1000000, ContextSizes.parse("1m"));
        assertEquals(1000000, ContextSizes.parse("1M"));
        assertEquals(100000, ContextSizes.parse("100k"));
        assertEquals(100000, ContextSizes.parse("100K"));
        assertEquals(1500, ContextSizes.parse("1.5k"));
        assertEquals(1500000, ContextSizes.parse("1.5M"));
        assertEquals(128000, ContextSizes.parse("128000"));
    }

    @Test public void trimsSurroundingAndEmbeddedSpaces() {
        assertEquals(128000, ContextSizes.parse(" 128K "));
        assertEquals(128000, ContextSizes.parse("128 k"));
        assertEquals(1000000, ContextSizes.parse("1 M"));
        assertEquals(128000, ContextSizes.parse("128 000"));
    }

    @Test public void rejectsJunkInsteadOfGuessing() {
        assertEquals(ContextSizes.UNSET, ContextSizes.parse(null));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse(""));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("   "));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("abc"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("k"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("1g"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("1,000"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("0k"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("-5k"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("1.2.3k"));
        assertEquals(ContextSizes.UNSET, ContextSizes.parse("99999999999"));
    }

    @Test public void formatsWholeUnitsFriendly() {
        assertEquals("128K", ContextSizes.format(128000));
        assertEquals("1M", ContextSizes.format(1000000));
        assertEquals("1.5K", ContextSizes.format(1500));
        assertEquals("3M", ContextSizes.format(3000000));
        assertEquals("512", ContextSizes.format(512));
        assertEquals("", ContextSizes.format(ContextSizes.UNSET));
    }

    @Test public void formatRoundTripsLosslessly() {
        int[] sizes = {512, 1000, 1001, 1500, 4096, 32768, 100000, 128000, 131072, 131100,
                200000, 999999, 1000001, 1234567, 1000000, 1500000, 8000000, 2147483647};
        for (int size : sizes) {
            String text = ContextSizes.format(size);
            assertEquals("round trip failed for " + size + " -> " + text,
                    size, ContextSizes.parse(text));
        }
    }

    @Test public void keepsPrecisionForBinarySizedWindows() {
        assertEquals("131.072K", ContextSizes.format(131072));
        assertEquals(131072, ContextSizes.parse(ContextSizes.format(131072)));
    }
}
