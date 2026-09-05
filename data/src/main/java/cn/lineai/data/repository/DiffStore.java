package cn.lineai.data.repository;

import java.util.List;

/**
 * 差异记录仓储接口，定义 DiffRepository 的公开契约。
 */
public interface DiffStore {
    DiffRecord recordDiff(String filePath, String oldContent, String newContent, boolean oldExists);

    DiffRecord getDiff(String diffId);

    List<DiffRecord> getDiffChain(String filePath);

    DiffRepository.RevertResult revertDiff(String diffId);

    void markReverted(String diffId);

    void setReview(String diffId, String state, String message);
}
