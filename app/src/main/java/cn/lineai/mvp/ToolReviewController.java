package cn.lineai.mvp;

import cn.lineai.data.repository.DiffRecord;
import cn.lineai.data.repository.DiffRepository;
import cn.lineai.data.repository.DiffStore;
import cn.lineai.data.service.FileRestorer;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.json.JSONArray;
import org.json.JSONObject;

final class ToolReviewController {
    interface Host {
        void refreshFileTreeAfterRevert(String filePath);

        void render();
    }

    private final DiffStore diffRepository;
    private final ToolMessageController toolMessageController;
    private final BackgroundTaskRunner backgroundTasks;
    private final MainThreadDispatcher mainThread;
    private final Host host;
    private final Map<String, DiffRecord> localReviewCache = new HashMap<>();
    /**
     * 逐条消息的展示列表 memo：渲染热路径（流式时每 ~80ms 一次）会反复调用
     * {@link #applyLocalReviews(List)}，而对同一不可变 {@link ChatMessage} 实例结果完全相同，
     * 因此只在实例变化或本地评审发生变更时重算。
     */
    private final ArrayList<ChatMessage> displaySource = new ArrayList<>();
    private final ArrayList<ChatMessage> displayMemo = new ArrayList<>();
    private final ArrayList<Integer> displayReviewGeneration = new ArrayList<>();
    private int reviewGeneration;

    ToolReviewController(
            DiffStore diffRepository,
            ToolMessageController toolMessageController,
            BackgroundTaskRunner backgroundTasks,
            MainThreadDispatcher mainThread,
            Host host
    ) {
        this.diffRepository = diffRepository;
        this.toolMessageController = toolMessageController;
        this.backgroundTasks = backgroundTasks;
        this.mainThread = mainThread;
        this.host = host;
    }

    void review(String toolCallId, String state, String diffId) {
        if (toolCallId == null || toolCallId.length() == 0) {
            return;
        }
        String normalizedState = "rejected".equals(state) ? "rejected" : "accepted";
        String resolvedDiffId = diffId == null ? "" : diffId;
        if ("rejected".equals(normalizedState)) {
            if (resolvedDiffId.length() == 0) {
                resolvedDiffId = toolMessageController.findToolMessageDiffId(toolCallId);
            }
            if (resolvedDiffId.length() > 0) {
                rejectWithRevert(toolCallId, resolvedDiffId);
                return;
            }
        }
        if (resolvedDiffId.length() == 0) {
            resolvedDiffId = toolMessageController.findToolMessageDiffId(toolCallId);
        }
        setLocalReview(resolvedDiffId, normalizedState, "");
        host.render();
    }

    private void rejectWithRevert(String toolCallId, String diffId) {
        backgroundTasks.execute("linecode-diff-revert", () -> {
            DiffRecord diffRecord = diffRepository.getDiff(diffId);
            String filePath = diffRecord == null ? "" : diffRecord.getFilePath();
            DiffRepository.RevertResult result = diffRepository.revertDiff(diffId);
            if (!result.isSuccess()) {
                mainThread.post(() -> {
                    setLocalReview(diffId, "", result.getMessage());
                    host.render();
                });
                return;
            }
            if (result.getDiffRecord() != null) {
                try {
                    FileRestorer.restoreOldContent(result.getDiffRecord());
                } catch (Exception e) {
                    mainThread.post(() -> {
                        setLocalReview(diffId, "", "File restore failed: " + e.getMessage());
                        host.render();
                    });
                    return;
                }
                diffRepository.markReverted(diffId);
            }
            mainThread.post(() -> {
                setLocalReview(diffId, "rejected", "Reverted change to " + filePath);
                host.refreshFileTreeAfterRevert(filePath);
                host.render();
            });
        });
    }

    List<ChatMessage> applyLocalReviews(List<ChatMessage> source) {
        ArrayList<ChatMessage> display = new ArrayList<>();
        if (source == null) {
            return display;
        }
        int size = source.size();
        for (int i = 0; i < size; i++) {
            ChatMessage message = source.get(i);
            if (i < displayMemo.size()
                    && displaySource.get(i) == message
                    && displayReviewGeneration.get(i) == reviewGeneration) {
                display.add(displayMemo.get(i));
                continue;
            }
            ChatMessage next = withLocalReview(message);
            setMemo(i, message, next);
            display.add(next);
        }
        trimMemo(size);
        return display;
    }

    private void setMemo(int index, ChatMessage source, ChatMessage result) {
        if (index < displaySource.size()) {
            displaySource.set(index, source);
            displayMemo.set(index, result);
            displayReviewGeneration.set(index, reviewGeneration);
            return;
        }
        displaySource.add(source);
        displayMemo.add(result);
        displayReviewGeneration.add(reviewGeneration);
    }

    private void trimMemo(int size) {
        while (displaySource.size() > size) {
            int last = displaySource.size() - 1;
            displaySource.remove(last);
            displayMemo.remove(last);
            displayReviewGeneration.remove(last);
        }
    }

    private ChatMessage withLocalReview(ChatMessage message) {
        ChatMessage next = message;
        if (message != null && message.getRole() == ChatMessage.Role.TOOL) {
            DiffRecord direct = localReview(message.getDiffId());
            if (direct != null) {
                next = next.withToolReview(
                        message.getDiffId(),
                        reviewState(direct),
                        direct.getReviewMessage()
                );
            }
            String content = next.getContent();
            // 嵌套评审只可能出现在带 diff_id 的 JSON 封装里，先做廉价筛除，避免逐条解析工具输出。
            if (content != null && content.indexOf("diff_id") >= 0) {
                String reviewed = applyNestedLocalReviews(content);
                if (!reviewed.equals(next.getContent())) {
                    next = next.withContent(reviewed, next.getReasoningContent(), next.isStreaming());
                }
            }
        }
        return next;
    }

    private String applyNestedLocalReviews(String content) {
        if (content == null || content.trim().length() == 0) {
            return content == null ? "" : content;
        }
        try {
            JSONObject root = new JSONObject(content);
            return applyNestedLocalReviews(root) ? root.toString() : content;
        } catch (Exception ignored) {
            return content;
        }
    }

    private boolean applyNestedLocalReviews(JSONObject object) throws Exception {
        if (object == null) {
            return false;
        }
        boolean changed = applyLocalReviews(object.optJSONArray("tool_calls"));
        JSONArray agents = object.optJSONArray("agents");
        if (agents != null) {
            for (int i = 0; i < agents.length(); i++) {
                JSONObject agent = agents.optJSONObject(i);
                changed = applyNestedLocalReviews(agent) || changed;
            }
        }
        return changed;
    }

    private boolean applyLocalReviews(JSONArray calls) throws Exception {
        if (calls == null) {
            return false;
        }
        boolean changed = false;
        for (int i = 0; i < calls.length(); i++) {
            JSONObject item = calls.optJSONObject(i);
            if (item == null) {
                continue;
            }
            JSONObject result = item.optJSONObject("result");
            DiffRecord local = localReview(result == null ? "" : result.optString("diff_id"));
            if (local != null) {
                result.put("review_state", reviewState(local));
                result.put("review_message", local.getReviewMessage());
                changed = true;
            }
            changed = applyNestedLocalReviews(item) || changed;
        }
        return changed;
    }

    private DiffRecord localReview(String diffId) {
        if (diffId == null || diffId.length() == 0) {
            return null;
        }
        DiffRecord record;
        synchronized (localReviewCache) {
            if (localReviewCache.containsKey(diffId)) {
                record = localReviewCache.get(diffId);
            } else {
                record = diffRepository.getDiff(diffId);
                localReviewCache.put(diffId, record);
            }
        }
        if (record == null || reviewState(record).length() == 0 && record.getReviewMessage().length() == 0) {
            return null;
        }
        return record;
    }

    private void setLocalReview(String diffId, String state, String message) {
        if (diffId == null || diffId.length() == 0) {
            return;
        }
        diffRepository.setReview(diffId, state, message);
        synchronized (localReviewCache) {
            localReviewCache.remove(diffId);
        }
        // 评审结果变化后，之前 memo 的展示消息全部作废。
        reviewGeneration++;
    }

    private String reviewState(DiffRecord record) {
        if (record == null) {
            return "";
        }
        return record.getReviewState().length() > 0
                ? record.getReviewState()
                : record.isReverted() ? "rejected" : "";
    }
}
