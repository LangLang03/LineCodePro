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
}
