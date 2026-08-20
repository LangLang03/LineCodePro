package cn.lineai.ui.theme;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class InlineEmphasisParserTest {
    @Test
    public void doubleAsterisksProduceBoldTextWithoutMarkers() {
        InlineEmphasisParser.Parsed parsed = InlineEmphasisParser.parse("**你好**，继续分析");

        assertEquals("你好，继续分析", parsed.getText());
        assertEquals(1, parsed.getSpans().size());
        assertEquals(InlineEmphasisParser.BOLD, parsed.getSpans().get(0).getStyle());
    }

    @Test
    public void missingSeparatorBetweenLegacySummaryBlocksIsRestored() {
        InlineEmphasisParser.Parsed parsed = InlineEmphasisParser.parse(
                "**First step****Second step**"
        );

        assertEquals("First step | Second step", parsed.getText());
        assertEquals(2, parsed.getSpans().size());
    }

    @Test
    public void codeSpansRemainLiteralWhileSurroundingEmphasisIsParsed() {
        InlineEmphasisParser.Parsed parsed = InlineEmphasisParser.parse(
                "Use `__init__` with **care** and ``a*b``"
        );

        assertEquals("Use `__init__` with care and ``a*b``", parsed.getText());
        assertEquals(1, parsed.getSpans().size());
        assertEquals(InlineEmphasisParser.BOLD, parsed.getSpans().get(0).getStyle());
    }
}
