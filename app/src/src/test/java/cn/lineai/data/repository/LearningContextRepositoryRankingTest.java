package cn.lineai.data.repository;

import static org.junit.Assert.assertTrue;

import java.util.List;
import org.junit.Test;

public final class LearningContextRepositoryRankingTest {
    @Test
    public void extractsMultilingualTechnicalKeywords() {
        List<String> keywords = LearningContextRepository.extractKeywords("这个项目不能用 AndroidX，要 Java 原生 View。");

        assertTrue(keywords.contains("项目"));
        assertTrue(keywords.contains("不能"));
        assertTrue(keywords.contains("androidx"));
        assertTrue(keywords.contains("java"));
    }

    @Test
    public void relevantDocumentScoresHigherThanUnrelatedDocument() {
        double relevant = LearningContextRepository.relevanceScore(
                "这个项目不能用 AndroidX",
                "当前项目不能使用 AndroidX，必须保持 Java 原生 View。"
        );
        double unrelated = LearningContextRepository.relevanceScore(
                "这个项目不能用 AndroidX",
                "天气预报和网页搜索配置。"
        );

        assertTrue(relevant > unrelated);
        assertTrue(relevant > 0.0);
    }

    @Test
    public void recencyDecaysWithAge() {
        long now = 1_800_000_000_000L;
        long recent = now - 86_400_000L;
        long old = now - 120L * 86_400_000L;

        assertTrue(LearningContextRepository.recencyBoost(recent, now)
                > LearningContextRepository.recencyBoost(old, now));
    }

    @Test
    public void rankingScoreAddsSmallRecencyAndBoost() {
        long now = 1_800_000_000_000L;
        String query = "项目 AndroidX";
        String text = "当前项目不能使用 AndroidX。";
        double normal = LearningContextRepository.rankingScore(query, text, now, now, 0.0);
        double boosted = LearningContextRepository.rankingScore(query, text, now, now, 0.3);

        assertTrue(boosted > normal);
    }
}
