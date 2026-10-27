package cn.lineai.feature.settings;

import java.util.List;
import org.junit.Test;
import static org.junit.Assert.*;

public class TutorialSectionsTest {

    @Test public void skipsHeadingsInsideFencesAndKeepsOrder() {
        String markdown = "# Intro\n## 1. Start\n```\n## not a section\n```\n## 2. 设置详解：模型管理\n";
        List<String> sections = TutorialSections.parse(markdown);
        assertEquals(2, sections.size());
        assertEquals("1. Start", sections.get(0));
        assertEquals("2. 设置详解：模型管理", sections.get(1));
    }

    @Test public void shortTitleDropsNumberAndPrefix() {
        assertEquals("模型管理", TutorialSections.shortTitle("2. 设置详解：模型管理"));
        assertEquals("Start", TutorialSections.shortTitle("1. Start"));
        assertEquals("Plain", TutorialSections.shortTitle("Plain"));
    }

    @Test public void emptyMarkdownYieldsNoSections() {
        assertTrue(TutorialSections.parse(null).isEmpty());
        assertTrue(TutorialSections.parse("").isEmpty());
    }
}
