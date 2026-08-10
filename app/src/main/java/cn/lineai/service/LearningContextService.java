package cn.lineai.service;

import cn.lineai.ai.prompt.MemoryPromptBuilder;
import cn.lineai.data.repository.LearningContextRepository;
import cn.lineai.data.repository.MemoryRanker;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.model.Strings;
import java.util.Collections;
import java.util.List;

/**
 * 学习上下文业务编排服务。
 * 负责记忆检索、BM25 排序、历史匹配、格式化等业务逻辑，
 * 数据存取委托给 {@link LearningContextRepository}。
 */
public final class LearningContextService {

    private static final double WORKING_MEMORY_BOOST = 0.30;

    private final LearningContextRepository repository;
    private final MemoryPromptBuilder promptBuilder;

    public LearningContextService(LearningContextRepository repository, MemoryPromptBuilder promptBuilder) {
        this.repository = repository;
        this.promptBuilder = promptBuilder;
    }

    public synchronized String buildLearningContext(String projectId, String userInput, String excludeConversationId) {
        List<MemoryRanker.Candidate> workingMemory = MemoryRanker.rank(repository.readWorkingMemory(projectId), userInput, 5, true, WORKING_MEMORY_BOOST);
        List<MemoryRanker.Candidate> memories = MemoryRanker.rank(repository.readMemories(projectId), userInput, 6, true);
        List<MemoryRanker.Candidate> history = MemoryRanker.rank(repository.readConversationIndex(projectId, excludeConversationId), userInput, 6, true);
        if (history.isEmpty()) {
            history = MemoryRanker.rank(repository.readConversationMessages(projectId, excludeConversationId), userInput, 6, true);
        }
        List<MemoryRanker.Candidate> skills = MemoryRanker.rank(repository.readSkills(), userInput, 8, true);
        repository.markMemoriesUsed(memories);

        return promptBuilder.build(projectId, workingMemory, memories, history, skills);
    }

    /**
     * 学习模式关闭时仍注入用户手动保存的记忆（memory_update 工具 / 记忆管理界面），
     * 保证手动记忆不依赖自动学习开关即可被模型看到。
     */
    public synchronized String buildManualMemoriesContext(String projectId) {
        List<MemoryRanker.Candidate> memories = repository.readManualMemories(projectId);
        if (memories.isEmpty()) {
            return "";
        }
        return promptBuilder.build(projectId, Collections.emptyList(), memories, Collections.emptyList(), Collections.emptyList());
    }

    public synchronized MemoryOverviewState getOverview(String projectId) {
        String safeProjectId = Strings.nullToEmpty(projectId);
        return new MemoryOverviewState(
                safeProjectId,
                repository.readOverviewMemories(MemoryOverviewState.Memory.SCOPE_USER, safeProjectId),
                repository.readOverviewMemories(MemoryOverviewState.Memory.SCOPE_PROJECT, safeProjectId),
                repository.readOverviewMemories(MemoryOverviewState.Memory.SCOPE_ENVIRONMENT, safeProjectId),
                repository.readOverviewWorkingMemory(safeProjectId),
                repository.readOverviewHistory(safeProjectId)
        );
    }
}
