package cn.lineai.tool;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.tool.builtin.FileIo;
import cn.lineai.tool.builtin.FileToolPathPolicy;
import java.io.File;
import org.json.JSONObject;

public final class DiffRecorder {
    private final DiffStore diffRepository;

    public DiffRecorder(DiffStore diffRepository) {
        this.diffRepository = diffRepository;
    }

    boolean shouldRecordDiff(BaseTool tool) {
        return diffRepository != null && tool.shouldRecordDiff();
    }

    ToolResult executeWithDiff(BaseTool tool, JSONObject input, ToolContext context) {
        String path = input.optString("file_path");
        File file;
        boolean existed;
        String oldContent = "";
        try {
            file = FileToolPathPolicy.resolve(context, path);
            existed = file.exists();
            if (existed && file.isDirectory()) {
                return ToolResult.of("", tool.getName(), "路径是一个目录，无法写入文件: " + path, true);
            }
            if (existed) {
                oldContent = FileIo.readUtf8(file);
            }
        } catch (Exception e) {
            ExceptionUtils.restoreInterrupt(e);
            return ToolResult.of("", tool.getName(), "无法读取原文件: " + path + "\n" + ExceptionUtils.describeException(e), true);
        }

        ToolResult result = tool.execute(input, context);
        if (result.isError()) {
            return result;
        }

        String newContent;
        try {
            newContent = file.exists() ? FileIo.readUtf8(file) : "";
        } catch (Exception ignored) {
            newContent = input.optString("content");
        }
        if (!oldContent.equals(newContent)) {
            DiffRecord diff = diffRepository.recordDiff(file.getPath(), oldContent, newContent, existed);
            return result.withDiffId(diff.getId());
        }
        return result;
    }
}
