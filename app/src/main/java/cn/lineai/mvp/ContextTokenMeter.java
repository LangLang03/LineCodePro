package cn.lineai.mvp;

import cn.lineai.context.ContextManager;
import cn.lineai.model.ChatMessage;
import java.util.ArrayList;
import java.util.List;

/**
 * 长对话上下文 token 估算的记忆化包装。
 *
 * <p>渲染热路径（流式输出时每 ~80ms 一次）都会重算整条对话的 token 占用，而单条消息的估算需要
 * 扫描其正文与工具调用。{@code ChatMessage} 不可变，因此同一实例的估算值恒定：按位置比对实例
 * 引用，只重算真正发生变化的消息，长对话下把每次 render 的成本从 O(全部文本) 降到 O(变化尾部)。
 */
public final class ContextTokenMeter {
    private final ContextManager contextManager;
    private final ArrayList<ChatMessage> memoSource = new ArrayList<>();
    private final ArrayList<Integer> memoCost = new ArrayList<>();
    private boolean memoIncludeReasoning;
    private boolean memoValid;
    private int computedCount;

    public ContextTokenMeter(ContextManager contextManager) {
        this.contextManager = contextManager;
    }

    public int total(List<ChatMessage> messages, boolean includeReasoning) {
        if (messages == null || messages.isEmpty()) {
            reset();
            memoValid = true;
            memoIncludeReasoning = includeReasoning;
            return 0;
        }
        if (!memoValid || memoIncludeReasoning != includeReasoning) {
            reset();
            memoIncludeReasoning = includeReasoning;
            memoValid = true;
        }
        int total = 0;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (i < memoCost.size() && memoSource.get(i) == message) {
                total += memoCost.get(i);
                continue;
            }
            int cost = contextManager.estimateTokens(message, includeReasoning);
            computedCount++;
            if (i < memoSource.size()) {
                memoSource.set(i, message);
                memoCost.set(i, cost);
            } else {
                memoSource.add(message);
                memoCost.add(cost);
            }
            total += cost;
        }
        while (memoSource.size() > messages.size()) {
            int last = memoSource.size() - 1;
            memoSource.remove(last);
            memoCost.remove(last);
        }
        return total;
    }

    public void reset() {
        memoSource.clear();
        memoCost.clear();
        memoValid = false;
    }

    /** 实际执行过多少次单条消息估算，用于测试与诊断。 */
    public int computedCount() {
        return computedCount;
    }
}
