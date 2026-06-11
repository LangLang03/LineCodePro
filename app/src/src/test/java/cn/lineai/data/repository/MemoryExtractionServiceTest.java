package cn.lineai.data.repository;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

import cn.lineai.model.MemoryOverviewState;
import java.util.List;
import org.junit.Test;

public final class MemoryExtractionServiceTest {
    @Test
    public void projectAndroidXConstraintUsesProjectScope() {
        List<MemoryExtractionService.ExtractedMemory> candidates = MemoryExtractionService.ruleBasedCandidates(
                "做自动提取，另外区分作用域，比如这个项目不能用AndroidX，就保存到项目，而不是全局",
                ""
        );

        assertTrue(candidates.size() > 0);
        assertEquals(MemoryOverviewState.Memory.SCOPE_PROJECT, candidates.get(0).scope);
        assertEquals("当前项目不能使用 AndroidX。", candidates.get(0).content);
    }

    @Test
    public void userPreferenceUsesUserScope() {
        List<MemoryExtractionService.ExtractedMemory> candidates = MemoryExtractionService.ruleBasedCandidates(
                "我偏好默认用中文，回答要简洁直接。",
                ""
        );

        assertTrue(candidates.size() > 0);
        assertEquals(MemoryOverviewState.Memory.SCOPE_USER, candidates.get(0).scope);
    }
}
