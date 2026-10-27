package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.FileDiff;

/** File changes produced by tools, with rollback support. */
public interface DiffStore {

    void record(String rootPath, String path, String before, String after, boolean existedBefore);

    List<FileDiff> list();

    boolean revert(String id);

    void clear();
}
