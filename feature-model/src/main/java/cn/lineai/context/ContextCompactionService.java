package cn.lineai.context;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.ai.ModelClient;
import cn.lineai.ai.ModelCompletionException;
import cn.lineai.ai.ModelCompletionResponse;
import cn.lineai.ai.ModelRequestOptions;
import cn.lineai.ai.message.AssistantModelMessage;
import cn.lineai.ai.message.ModelMessage;
import cn.lineai.ai.message.SystemModelMessage;
import cn.lineai.ai.message.ToolModelMessage;
import cn.lineai.ai.message.UserModelMessage;
import cn.lineai.ai.prompt.StringTemplate;
import cn.lineai.ai.protocol.CodexResponsesProtocol;
import cn.lineai.ai.protocol.OpenAiResponsesCompactionProtocol;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.ToolInfo;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContextCompactionService {
    /**
     * 硬触发压缩比例：上下文占用达到 80% 时进行全量压缩。
     */
    public static final double COMPACT_TRIGGER_RATIO = 0.8d;

    /**
     * 软触发压缩比例：上下文占用达到 50% 时进行增量压缩（仅压缩最早的部分消息）。
     * <p>
     * 参考 codex 的 auto_compact_token_limit（context_window * 0.9）与 Claude-Code 的
     * AUTOCOMPACT_BUFFER_TOKENS 思路，但更激进一步：在 50% 时就开始把最早的历史
     * 增量压缩成摘要，避免等到 80% 时一次性压缩大量消息导致 transcript 字符串过大。
     */
    public static final double SOFT_COMPACT_TRIGGER_RATIO = 0.5d;

    /**
     * 软触发压缩时，保留最近消息不动所占的比例。例如 0.3 表示保留最近 30% 的可压缩消息，
     * 只对更早的 70% 做增量压缩。这样可以在保持近期上下文完整的同时，逐步减少总占用。
     */
    public static final double SOFT_COMPACT_TAIL_KEEP_RATIO = 0.3d;

    /**
     * transcript 分段时单段的最大字符数。仅用于内部 StringBuilder 缓冲控制，
     * 不会丢弃任何内容：超出后开启下一段，最后通过 String.join 拼接。
     * <p>
     * 这避免了单个 StringBuilder 不断扩容时产生大量临时 char[]，也让中间段可被
     * GC 及时回收。最终拼接后的大字符串由 largeHeap + OOM catch 兜底。
     */
    private static final int TRANSCRIPT_SEGMENT_MAX_CHARS = 256 * 1024;

    /**
     * 硬压缩后保留最近真实用户消息（原文保留，不做摘要）的 token 预算，
     * 与 codex 本地压缩的 COMPACT_USER_MESSAGE_MAX_TOKENS 对齐。
     */
    public static final int COMPACT_USER_MESSAGE_MAX_TOKENS = 20_000;

    /**
     * 压缩调用（生成摘要的模型请求）失败时的重试次数，与 codex 的压缩重试语义对齐。
     */
    private static final int MAX_COMPACT_RETRIES = 2;

    private static final long COMPACT_RETRY_DELAY_MS = 1000L;

    private static final Pattern ANALYSIS_PATTERN = Pattern.compile("<analysis>[\\s\\S]*?</analysis>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("<summary>([\\s\\S]*?)</summary>", Pattern.CASE_INSENSITIVE);

    private final ModelClient modelClient;
    private final OpenAiResponsesCompactionProtocol responsesCompactionProtocol;
    private final CodexResponsesProtocol responsesSummaryProtocol;
    private final PromptTemplateRepository promptTemplateRepository;
    private final TokenUsageTracker tokenUsageTracker;

    public ContextCompactionService(
            ModelClient modelClient,
            OpenAiResponsesCompactionProtocol responsesCompactionProtocol,
            CodexResponsesProtocol responsesSummaryProtocol,
            PromptTemplateRepository promptTemplateRepository,
            TokenUsageTracker tokenUsageTracker
    ) {
        this.modelClient = modelClient;
        this.responsesCompactionProtocol = responsesCompactionProtocol;
        this.responsesSummaryProtocol = responsesSummaryProtocol;
        this.promptTemplateRepository = promptTemplateRepository;
        this.tokenUsageTracker = tokenUsageTracker;
    }

    public ContextCompactionResult compact(
            ModelConfig selectedModel,
            List<ChatMessage> messages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        if (selectedModel == null) {
            throw new ModelCompletionException("No model available, cannot compact context");
        }
        ArrayList<ChatMessage> contextMessages = compactableMessages(messages);
        if (contextMessages.isEmpty()) {
            throw new ModelCompletionException("Context too small, no need to compact");
        }
        try {
            if (shouldUseResponsesCompaction(selectedModel)) {
                return compactWithResponsesApi(selectedModel, contextMessages, cancellationToken);
            }
            if (shouldUseOpenAiResponsesSummary(selectedModel)) {
                return compactWithOpenAiResponsesSummary(selectedModel, contextMessages, cancellationToken);
            }
            return compactWithSummary(selectedModel, contextMessages, cancellationToken);
        } catch (OutOfMemoryError oom) {
            // 上下文压缩在后台线程构造 transcript 时仍可能因模型响应累积而触发 OOM，
            // 这里释放本次调用中已经累积的大对象，再向上抛出可读的失败信息，
            // 让 ContextCompactionController 走降级路径而不是进程被杀。
            contextMessages.clear();
            throw new ModelCompletionException("Context too large, out of memory during compaction. Please manually clear early messages and retry", oom);
        }
    }

    /**
     * 判断是否达到硬触发压缩阈值（80%）。硬触发会做全量压缩：把所有可压缩消息
     * 一次性总结为摘要。这是原有逻辑的兜底阈值。
     */
    public boolean shouldCompact(List<ChatMessage> messages, int contextTokens, ContextManager contextManager, boolean includeReasoning) {
        if (messages == null || messages.isEmpty() || contextManager == null) {
            return false;
        }
        return contextManager.estimateTokens(messages, includeReasoning) >= Math.max(1, contextTokens) * COMPACT_TRIGGER_RATIO;
    }

    /**
     * codex 式硬触发判断：优先使用服务器观测到的 input tokens（真实 usage，
     * 已含 system prompt 与工具定义），观测不到时回退到本地估算。
     */
    public boolean shouldCompact(List<ChatMessage> messages, int contextTokens, ContextManager contextManager, boolean includeReasoning, int observedInputTokens) {
        if (observedInputTokens > 0) {
            return observedInputTokens >= Math.max(1, contextTokens) * COMPACT_TRIGGER_RATIO;
        }
        return shouldCompact(messages, contextTokens, contextManager, includeReasoning);
    }

    /**
     * 判断是否达到软触发压缩阈值（50%）。软触发会做增量压缩：只对最早的一部分
     * 消息生成摘要，保留近期消息不动，避免上下文继续增长到 80% 时才一次性处理
     * 大量历史。
     * <p>
     * 软触发仅当存在足够多的可压缩消息时才会触发，避免在对话刚起步时频繁压缩。
     */
    public boolean shouldSoftCompact(List<ChatMessage> messages, int contextTokens, ContextManager contextManager, boolean includeReasoning) {
        if (messages == null || messages.isEmpty() || contextManager == null) {
            return false;
        }
        int contextTokensSafe = Math.max(1, contextTokens);
        int usageTokens = contextManager.estimateTokens(messages, includeReasoning);
        if (usageTokens < contextTokensSafe * SOFT_COMPACT_TRIGGER_RATIO) {
            return false;
        }
        // 已经达到硬触发阈值时让 shouldCompact 处理，不重复软触发。
        if (usageTokens >= contextTokensSafe * COMPACT_TRIGGER_RATIO) {
            return false;
        }
        return compactableMessageCount(messages) >= 8;
    }

    /**
     * codex 式软触发判断：优先使用服务器观测到的 input tokens，观测不到时回退到本地估算。
     */
    public boolean shouldSoftCompact(List<ChatMessage> messages, int contextTokens, ContextManager contextManager, boolean includeReasoning, int observedInputTokens) {
        if (observedInputTokens > 0) {
            int contextTokensSafe = Math.max(1, contextTokens);
            if (observedInputTokens < contextTokensSafe * SOFT_COMPACT_TRIGGER_RATIO) {
                return false;
            }
            if (observedInputTokens >= contextTokensSafe * COMPACT_TRIGGER_RATIO) {
                return false;
            }
            return compactableMessageCount(messages) >= 8;
        }
        return shouldSoftCompact(messages, contextTokens, contextManager, includeReasoning);
    }

    private int compactableMessageCount(List<ChatMessage> messages) {
        int count = 0;
        if (messages == null) {
            return 0;
        }
        for (ChatMessage message : messages) {
            if (message == null || message.isExcludeFromContext() || !hasCompactableContent(message)) {
                continue;
            }
            if (message.isHidden() && message.getResponseInputItemJson().length() > 0) {
                continue;
            }
            if (message.isCompactBlock()) {
                continue;
            }
            count++;
        }
        return count;
    }

    /**
     * 将可压缩消息拆分为 head（待压缩）与 tail（保留）两部分。tail 保留最近
     * SOFT_COMPACT_TAIL_KEEP_RATIO 比例的消息不动，head 用于生成增量摘要。
     * <p>
     * 调用方负责把 head 传入 {@link #compact(ModelConfig, List, ModelCancellationToken)}，
     * 然后将 head 中的消息标记 excludeFromContext 并追加摘要，tail 保持原状。
     *
     * @return 长度为 2 的 List：{@code get(0)} 为 head，{@code get(1)} 为 tail。
     */
    public List<List<ChatMessage>> splitForSoftCompact(List<ChatMessage> messages) {
        ArrayList<ChatMessage> compactable = compactableMessages(messages);
        ArrayList<ChatMessage> head = new ArrayList<>();
        ArrayList<ChatMessage> tail = new ArrayList<>();
        if (compactable.isEmpty()) {
            ArrayList<List<ChatMessage>> result = new ArrayList<>(2);
            result.add(head);
            result.add(tail);
            return result;
        }
        int tailSize = Math.max(1, (int) Math.round(compactable.size() * SOFT_COMPACT_TAIL_KEEP_RATIO));
        int splitIndex = Math.max(0, compactable.size() - tailSize);
        for (int i = 0; i < splitIndex; i++) {
            head.add(compactable.get(i));
        }
        for (int i = splitIndex; i < compactable.size(); i++) {
            tail.add(compactable.get(i));
        }
        ArrayList<List<ChatMessage>> result = new ArrayList<>(2);
        result.add(head);
        result.add(tail);
        return result;
    }

    private ContextCompactionResult compactWithResponsesApi(
            ModelConfig selectedModel,
            List<ChatMessage> contextMessages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        ArrayList<ModelMessage> input = new ArrayList<>();
        for (ChatMessage message : contextMessages) {
            input.add(toResponsesModelMessage(message));
        }
        String compactItem = compactResponsesItemWithRetry(selectedModel, input, cancellationToken);
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ContextCompactionResult("", "");
        }
        // Codex/OpenAI-Responses 压缩调用返回的是字符串压缩项，不携带 token usage，
        // 因此不会像 streamSummaryWithRetry 那样把压缩后的 usage 记入 tracker。
        // 这里显式重置 tracker，避免残留压缩前的旧 usage 让后续 shouldCompact/
        // shouldSoftCompact 继续以旧的大数值触发过度压缩；重置后基线回退到本地估算。
        if (tokenUsageTracker != null) {
            tokenUsageTracker.reset();
        }
        return new ContextCompactionResult(createResponsesCompactFallbackContent(), compactItem);
    }

    private ContextCompactionResult compactWithSummary(
            ModelConfig selectedModel,
            List<ChatMessage> contextMessages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        String transcript = buildTranscript(contextMessages);
        ArrayList<ModelMessage> request = new ArrayList<>();
        request.add(new UserModelMessage(prompt() + "\n\nConversation transcript:\n" + transcript));
        ModelCompletionResponse response = streamSummaryWithRetry(
                () -> modelClient.stream(
                        selectedModel,
                        request,
                        null,
                        cancellationToken,
                        new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, new ArrayList<ToolInfo>())
                ),
                cancellationToken
        );
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ContextCompactionResult("", "");
        }
        return new ContextCompactionResult(createCompactSummaryContent(response.getText()), "");
    }

    private ContextCompactionResult compactWithOpenAiResponsesSummary(
            ModelConfig selectedModel,
            List<ChatMessage> contextMessages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        String transcript = buildTranscript(contextMessages);
        ArrayList<ModelMessage> request = new ArrayList<>();
        request.add(new UserModelMessage(prompt() + "\n\nConversation transcript:\n" + transcript));
        ModelCompletionResponse response = streamSummaryWithRetry(
                () -> responsesSummaryProtocol.stream(
                        selectedModel.withModelId(selectedModel.getEffectiveCompressionModelId()),
                        request,
                        null,
                        cancellationToken,
                        new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, new ArrayList<cn.lineai.tool.ToolInfo>())
                ),
                cancellationToken
        );
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ContextCompactionResult("", "");
        }
        return new ContextCompactionResult(createCompactSummaryContent(response.getText()), "");
    }

    /**
     * codex 式压缩调用：独立模型请求生成摘要，失败时带退避重试，成功后把
     * 服务器观测到的 usage 记入 {@link TokenUsageTracker}（压缩后上下文的新基线）。
     */
    private ModelCompletionResponse streamSummaryWithRetry(
            SummaryStreamCall call,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        int attempt = 0;
        while (true) {
            try {
                ModelCompletionResponse response = call.call();
                if (tokenUsageTracker != null) {
                    tokenUsageTracker.record(response);
                }
                return response;
            } catch (ModelCompletionException e) {
                if (attempt >= MAX_COMPACT_RETRIES || (cancellationToken != null && cancellationToken.isCancelled())) {
                    throw e;
                }
                attempt++;
                sleepQuietly(COMPACT_RETRY_DELAY_MS * attempt);
            }
        }
    }

    private String compactResponsesItemWithRetry(
            ModelConfig selectedModel,
            List<ModelMessage> input,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        int attempt = 0;
        while (true) {
            try {
                return responsesCompactionProtocol.compact(selectedModel, input, cancellationToken);
            } catch (ModelCompletionException e) {
                if (attempt >= MAX_COMPACT_RETRIES || (cancellationToken != null && cancellationToken.isCancelled())) {
                    throw e;
                }
                attempt++;
                sleepQuietly(COMPACT_RETRY_DELAY_MS * attempt);
            }
        }
    }

    private void sleepQuietly(long millis) {
        try {
            Thread.sleep(millis);
        } catch (InterruptedException ignored) {
            Thread.currentThread().interrupt();
        }
    }

    private interface SummaryStreamCall {
        ModelCompletionResponse call() throws ModelCompletionException;
    }

    private boolean shouldUseResponsesCompaction(ModelConfig selectedModel) {
        return selectedModel.isCompressionModelEnabled()
                && selectedModel.getProtocolType() == ModelProtocolType.CODEX_RESPONSES;
    }

    private boolean shouldUseOpenAiResponsesSummary(ModelConfig selectedModel) {
        return selectedModel.isCompressionModelEnabled()
                && selectedModel.getProtocolType() == ModelProtocolType.OPENAI_COMPATIBLE;
    }

    /**
     * codex 式保留尾部：从最新往回收集真实用户消息（跳过已排除消息、隐藏摘要、
     * 压缩进度块），直到 token 预算耗尽，返回按时间正序的列表。
     * <p>
     * 硬压缩后这些最近用户消息原样保留在上下文中（不转成摘要），摘要作为压缩后
     * 历史的一项。对应 codex 本地压缩的 COMPACT_USER_MESSAGE_MAX_TOKENS 语义。
     */
    public List<ChatMessage> selectRecentUserMessages(List<ChatMessage> messages, int maxTokens, ContextManager contextManager) {
        ArrayList<ChatMessage> selected = new ArrayList<>();
        if (messages == null || messages.isEmpty() || maxTokens <= 0 || contextManager == null) {
            return selected;
        }
        int remaining = maxTokens;
        for (int i = messages.size() - 1; i >= 0; i--) {
            ChatMessage message = messages.get(i);
            if (message == null || message.isExcludeFromContext() || message.isCompactBlock()) {
                continue;
            }
            // 已压缩产生的隐藏摘要不再保留为"最近用户消息"。
            if (message.isHidden() && message.getResponseInputItemJson().length() > 0) {
                continue;
            }
            if (message.getRole() != ChatMessage.Role.USER) {
                continue;
            }
            if (message.getContent().trim().length() == 0 && message.getReasoningContent().trim().length() == 0) {
                continue;
            }
            int tokens = contextManager.estimateTokens(message);
            if (tokens > remaining) {
                break;
            }
            selected.add(message);
            remaining -= tokens;
        }
        java.util.Collections.reverse(selected);
        return selected;
    }

    private ArrayList<ChatMessage> compactableMessages(List<ChatMessage> messages) {
        ArrayList<ChatMessage> result = new ArrayList<>();
        if (messages == null) {
            return result;
        }
        for (ChatMessage message : messages) {
            if (message == null || message.isExcludeFromContext() || !hasCompactableContent(message)) {
                continue;
            }
            // 已压缩产生的隐藏摘要（hidden 且带响应项 JSON）不再参与下一次压缩，
            // 否则旧摘要会被当成普通消息重新总结，导致历史摘要被覆盖/丢失。
            if (message.isHidden() && message.getResponseInputItemJson().length() > 0) {
                continue;
            }
            // 压缩进度块（compactStatus 非空）也不是可压缩内容。
            if (message.isCompactBlock()) {
                continue;
            }
            result.add(message);
        }
        return result;
    }

    private boolean hasCompactableContent(ChatMessage message) {
        return message.getContent().trim().length() > 0
                || message.getReasoningContent().trim().length() > 0
                || message.hasToolCalls()
                || !message.getToolResults().isEmpty()
                || message.getResponseInputItemJson().length() > 0;
    }

    private ModelMessage toResponsesModelMessage(ChatMessage message) {
        if (message.getResponseInputItemJson().length() > 0) {
            return new UserModelMessage(message.getContent(), message.getResponseInputItemJson());
        }
        if (message.getRole() == ChatMessage.Role.SYSTEM) {
            return new SystemModelMessage(MessageContentSanitizer.forModel(message));
        }
        if (message.getRole() == ChatMessage.Role.TOOL) {
            return new ToolModelMessage(MessageContentSanitizer.forModel(message), message.getToolCallId(), message.getToolName(), message.isError());
        }
        if (message.getRole() == ChatMessage.Role.ASSISTANT) {
            return new AssistantModelMessage(MessageContentSanitizer.forModel(message), message.getReasoningContent(), message.getToolCalls());
        }
        return new UserModelMessage(MessageContentSanitizer.forModel(message));
    }

    /**
     * 构造压缩用 transcript。采用分段存储策略：用 {@code List<StringBuilder>} 分段累积，
     * 每段不超过 {@link #TRANSCRIPT_SEGMENT_MAX_CHARS}，避免单个 StringBuilder 不断扩容
     * 时产生大量临时 char[]。最后通过 {@code String.join} 一次性拼接为完整 transcript。
     * <p>
     * 与之前的粗暴截断（MAX_TRANSCRIPT_CHARS=1MB / MAX_MESSAGE_CONTENT_CHARS=64KB）
     * 不同，这里不丢弃任何内容：超长内容由 largeHeap + OOM catch 兜底，而不是
     * 静默截断导致模型丢失早期历史。
     */
    private String buildTranscript(List<ChatMessage> messages) {
        ArrayList<String> segments = new ArrayList<>();
        StringBuilder current = new StringBuilder(TRANSCRIPT_SEGMENT_MAX_CHARS);
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (current.length() > 0) {
                current.append("\n\n---\n\n");
            }
            current.append("## ").append(i + 1).append(". ").append(message.getRole().getProtocolName());
            if (message.getContent().trim().length() > 0) {
                current.append("\n\n").append(ToolResult.truncateContent(MessageContentSanitizer.forModel(message)));
            }
            if (message.hasToolCalls()) {
                current.append("\n\nTool calls:\n");
                for (ToolCall call : message.getToolCalls()) {
                    current.append("- ").append(call.getName()).append(": ")
                            .append(ToolResult.truncateContent(call.getArguments())).append('\n');
                    flushSegmentIfNeeded(segments, current);
                }
            }
            if (message.getToolCallId().length() > 0) {
                current.append("\n\nTool result for: ").append(message.getToolCallId());
            }
            if (message.getReasoningContent().trim().length() > 0) {
                current.append("\n\nReasoning:\n").append(ToolResult.truncateContent(message.getReasoningContent()));
            }
            flushSegmentIfNeeded(segments, current);
        }
        if (current.length() > 0) {
            segments.add(current.toString());
        }
        return String.join("", segments);
    }

    /**
     * 当当前 StringBuilder 长度超过 {@link #TRANSCRIPT_SEGMENT_MAX_CHARS} 时，
     * 将其内容加入分段列表并重置长度为 0 继续复用。这里不调用 {@code trimToSize()}：
     * 缩减内部 char[] 后下次 append 又得扩容，得不偿失。复用同一个 StringBuilder
     * 让中间段可被 GC 及时回收，而不是等整个 transcript 构造完才释放。
     */
    private void flushSegmentIfNeeded(List<String> segments, StringBuilder current) {
        if (current.length() < TRANSCRIPT_SEGMENT_MAX_CHARS) {
            return;
        }
        segments.add(current.toString());
        current.setLength(0);
    }

    public String createCompactSummaryContent(String summary) {
        String formatted = formatCompactSummary(summary);
        String template = promptTemplateRepository.getTemplateText(
                PromptTemplateRepository.ID_CONTEXT_COMPACTION_SUMMARY_PREFIX);
        HashMap<String, String> values = new HashMap<>();
        values.put("SUMMARY", formatted);
        return new StringTemplate(template).render(values);
    }

    private String createResponsesCompactFallbackContent() {
        return promptTemplateRepository.getTemplateText(
                PromptTemplateRepository.ID_CONTEXT_COMPACTION_RESPONSES_FALLBACK);
    }

    private String formatCompactSummary(String summary) {
        String formatted = summary == null ? "" : summary;
        formatted = ANALYSIS_PATTERN.matcher(formatted).replaceAll("");
        Matcher matcher = SUMMARY_PATTERN.matcher(formatted);
        if (matcher.find()) {
            formatted = "Summary:\n" + matcher.group(1).trim();
        }
        return formatted.replaceAll("\\n\\n+", "\n\n").trim();
    }

    private String prompt() {
        return promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_CONTEXT_COMPACTION);
    }
}
