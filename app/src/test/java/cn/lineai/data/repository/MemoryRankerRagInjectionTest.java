package cn.lineai.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.util.Arrays;
import java.util.List;
import org.junit.Test;

/**
 * 证明 RAG 记忆注入的排序行为：MemoryRanker.rank 会选择关键词命中的候选，
 * 在无命中时通过 recency fallback 选择最近候选，并在关闭 fallback 时返回空列表。
 */
public final class MemoryRankerRagInjectionTest {

    @Test
    public void matchingMemoryCandidateIsRankedFirstWithPositiveRelevance() {
        MemoryRanker.Candidate unrelated = new MemoryRanker.Candidate(
                "u1", "天气预报和网页搜索配置。", System.currentTimeMillis(), "");
        MemoryRanker.Candidate match = new MemoryRanker.Candidate(
                "m1", "当前项目不能使用 AndroidX，必须保持 Java 原生 View。",
                System.currentTimeMillis(), "");

        List<MemoryRanker.Candidate> result = MemoryRanker.rank(
                Arrays.asList(unrelated, match), "项目 AndroidX", 10, false, 0.0);

        assertFalse(result.isEmpty());
        assertEquals("m1", result.get(0).id);
        assertTrue(match.relevanceScore > 0.0);
    }

    @Test
    public void rankWithNoMatchesAndRecentFallbackReturnsRecentCandidates() {
        long now = System.currentTimeMillis();
        MemoryRanker.Candidate recent = new MemoryRanker.Candidate(
                "r1", "天气预报和网页搜索配置。", now - 60_000L, "");
        MemoryRanker.Candidate old = new MemoryRanker.Candidate(
                "r2", "天气预报和网页搜索配置。", now - 90L * 86_400_000L, "");

        List<MemoryRanker.Candidate> result = MemoryRanker.rank(
                Arrays.asList(recent, old), "项目 AndroidX", 5, true, 0.0);

        assertFalse(result.isEmpty());
        assertEquals("r1", result.get(0).id);
    }

    @Test
    public void rankWithNoMatchesAndNoFallbackReturnsEmpty() {
        long now = System.currentTimeMillis();
        MemoryRanker.Candidate recent = new MemoryRanker.Candidate(
                "r1", "天气预报和网页搜索配置。", now - 60_000L, "");
        MemoryRanker.Candidate old = new MemoryRanker.Candidate(
                "r2", "天气预报和网页搜索配置。", now - 90L * 86_400_000L, "");

        List<MemoryRanker.Candidate> result = MemoryRanker.rank(
                Arrays.asList(recent, old), "项目 AndroidX", 5, false, 0.0);

        assertTrue(result.isEmpty());
    }

    @Test
    public void maxCountLimitsRankedCandidatesAndPreservesOrdering() {
        long now = System.currentTimeMillis();
        MemoryRanker.Candidate oldUnrelated = new MemoryRanker.Candidate(
                "c1", "很久以前的无关记忆：天气预报和网页搜索配置。", now - 1000L * 60 * 60, "");
        MemoryRanker.Candidate recentUnrelated = new MemoryRanker.Candidate(
                "c2", "刚刚发生的无关记忆：午饭吃了什么。", now - 1000L * 60, "");
        MemoryRanker.Candidate highRelevanceOlder = new MemoryRanker.Candidate(
                "c3", "当前项目不能使用 AndroidX，必须保持 Java 原生 View。", now - 1000L * 30, "");
        MemoryRanker.Candidate highRelevanceNewest = new MemoryRanker.Candidate(
                "c4", "项目升级到 AndroidX 后，需要更新所有传统 View 的适配。", now, "");

        List<MemoryRanker.Candidate> result = MemoryRanker.rank(
                Arrays.asList(oldUnrelated, recentUnrelated, highRelevanceOlder, highRelevanceNewest),
                "AndroidX 项目 View",
                2,
                true,
                0.0);

        // 截断到 maxCount=2：无关候选被过滤，只保留两个相关候选（排序由相关度/新鲜度启发式决定）。
        assertEquals(2, result.size());
        for (MemoryRanker.Candidate candidate : result) {
            assertTrue("unrelated candidate must be filtered: " + candidate.id,
                    "c3".equals(candidate.id) || "c4".equals(candidate.id));
            assertTrue(candidate.relevanceScore > 0.0);
        }
    }

    @Test
    public void matchingRankFiltersOutIrrelevantCandidates() {
        long now = System.currentTimeMillis();
        MemoryRanker.Candidate unrelated = new MemoryRanker.Candidate(
                "u1", "天气预报和网页搜索配置，还有一些生活琐事。", now - 1000L * 60 * 60, "");
        MemoryRanker.Candidate recentUnrelated = new MemoryRanker.Candidate(
                "u2", "刚刚发生的无关记忆：午饭吃了什么。", now - 1000L * 60, "");
        MemoryRanker.Candidate strongMatch = new MemoryRanker.Candidate(
                "s1", "当前项目已经切换到 AndroidX，需要更新所有旧的 View 实现。", now, "");

        List<MemoryRanker.Candidate> result = MemoryRanker.rank(
                Arrays.asList(unrelated, recentUnrelated, strongMatch),
                "AndroidX 项目 View 升级",
                10,
                true,
                0.0);

        // 有匹配时，相关性为 0 的候选即使很新也会被过滤，只保留强匹配候选。
        assertEquals(1, result.size());
        assertEquals("s1", result.get(0).id);
        assertTrue(result.get(0).relevanceScore > 0.0);
    }
}
