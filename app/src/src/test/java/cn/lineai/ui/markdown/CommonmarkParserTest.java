package cn.lineai.ui.markdown;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import java.util.Collections;
import org.commonmark.Extension;
import org.commonmark.ext.gfm.tables.TableBlock;
import org.commonmark.ext.gfm.tables.TableBody;
import org.commonmark.ext.gfm.tables.TableCell;
import org.commonmark.ext.gfm.tables.TableHead;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.node.BulletList;
import org.commonmark.node.FencedCodeBlock;
import org.commonmark.node.Node;
import org.commonmark.node.OrderedList;
import org.commonmark.parser.Parser;
import org.junit.Test;

public final class CommonmarkParserTest {
    @Test
    public void parsesNestedListsAndCodeBlocks() {
        Node document = Parser.builder().build().parse("- one\n  1. nested\n\n```java\nclass A {}\n```");

        assertTrue(document.getFirstChild() instanceof BulletList);
        Node nested = document.getFirstChild().getFirstChild().getFirstChild().getNext();
        assertTrue(nested instanceof OrderedList);

        Node code = document.getFirstChild().getNext();
        assertTrue(code instanceof FencedCodeBlock);
        assertEquals("java", ((FencedCodeBlock) code).getInfo());
    }

    @Test
    public void parsesGfmTablesWithAlignment() {
        Iterable<Extension> extensions = Collections.singletonList(TablesExtension.create());
        Node document = Parser.builder().extensions(extensions).build().parse(
                "| Name | Count |\n"
                        + "| --- | ---: |\n"
                        + "| alpha | 12 |\n"
        );

        assertTrue(document.getFirstChild() instanceof TableBlock);
        Node head = document.getFirstChild().getFirstChild();
        assertTrue(head instanceof TableHead);
        Node body = head.getNext();
        assertTrue(body instanceof TableBody);

        Node secondHeaderCell = head.getFirstChild().getFirstChild().getNext();
        assertTrue(secondHeaderCell instanceof TableCell);
        assertEquals(TableCell.Alignment.RIGHT, ((TableCell) secondHeaderCell).getAlignment());
    }
}
