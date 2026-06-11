package cn.lineai.context;

import android.content.Context;
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
import cn.lineai.ai.protocol.CodexResponsesProtocol;
import cn.lineai.ai.protocol.OpenAiResponsesCompactionProtocol;
import cn.lineai.data.repository.PromptTemplateRepository;
import cn.lineai.model.AiBehaviorSettings;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.MessageContentSanitizer;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import cn.lineai.tool.BaseTool;
import cn.lineai.tool.ToolCall;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public final class ContextCompactionService {
    public static final double COMPACT_TRIGGER_RATIO = 0.8d;

    private static final Pattern ANALYSIS_PATTERN = Pattern.compile("<analysis>[\\s\\S]*?</analysis>", Pattern.CASE_INSENSITIVE);
    private static final Pattern SUMMARY_PATTERN = Pattern.compile("<summary>([\\s\\S]*?)</summary>", Pattern.CASE_INSENSITIVE);

    private final ModelClient modelClient;
    private final OpenAiResponsesCompactionProtocol responsesCompactionProtocol;
    private final CodexResponsesProtocol responsesSummaryProtocol;
    private final PromptTemplateRepository promptTemplateRepository;

    public ContextCompactionService(Context context) {
        this(context, new ModelClient(), new OpenAiResponsesCompactionProtocol(), new CodexResponsesProtocol(),
                new PromptTemplateRepository(context));
    }

    ContextCompactionService(
            Context context,
            ModelClient modelClient,
            OpenAiResponsesCompactionProtocol responsesCompactionProtocol,
            CodexResponsesProtocol responsesSummaryProtocol,
            PromptTemplateRepository promptTemplateRepository
    ) {
        Context appContext = context.getApplicationContext();
        this.modelClient = modelClient;
        this.responsesCompactionProtocol = responsesCompactionProtocol;
        this.responsesSummaryProtocol = responsesSummaryProtocol;
        this.promptTemplateRepository = promptTemplateRepository == null
                ? new PromptTemplateRepository(appContext)
                : promptTemplateRepository;
    }

    public ContextCompactionResult compact(
            ModelConfig selectedModel,
            List<ChatMessage> messages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        if (selectedModel == null) {
            throw new ModelCompletionException("没有可用模型，无法压缩上下文");
        }
        ArrayList<ChatMessage> contextMessages = compactableMessages(messages);
        if (contextMessages.isEmpty()) {
            throw new ModelCompletionException("当前上下文不足，无需压缩");
        }
        if (shouldUseResponsesCompaction(selectedModel)) {
            return compactWithResponsesApi(selectedModel, contextMessages, cancellationToken);
        }
        if (shouldUseOpenAiResponsesSummary(selectedModel)) {
            return compactWithOpenAiResponsesSummary(selectedModel, contextMessages, cancellationToken);
        }
        return compactWithSummary(selectedModel, contextMessages, cancellationToken);
    }

    public boolean shouldCompact(List<ChatMessage> messages, int contextTokens, ContextManager contextManager, boolean includeReasoning) {
        if (messages == null || messages.isEmpty() || contextManager == null) {
            return false;
        }
        return contextManager.estimateTokens(messages, includeReasoning) >= Math.max(1, contextTokens) * COMPACT_TRIGGER_RATIO;
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
        String compactItem = responsesCompactionProtocol.compact(selectedModel, input, cancellationToken);
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ContextCompactionResult("", "");
        }
        return new ContextCompactionResult(createResponsesCompactFallbackContent(), compactItem);
    }

    private ContextCompactionResult compactWithSummary(
            ModelConfig selectedModel,
            List<ChatMessage> contextMessages,
            ModelCancellationToken cancellationToken
    ) throws ModelCompletionException {
        String transcript = toCompactTranscript(contextMessages);
        ArrayList<ModelMessage> request = new ArrayList<>();
        request.add(new UserModelMessage(prompt() + "\n\nConversation transcript:\n" + transcript));
        ModelRequestOptions options = new ModelRequestOptions(
                AiBehaviorSettings.REASONING_OFF,
                false,
                new ArrayList<BaseTool>()
        );
        ModelCompletionResponse response = modelClient.stream(selectedModel, request, null, cancellationToken, options);
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
        String transcript = toCompactTranscript(contextMessages);
        ArrayList<ModelMessage> request = new ArrayList<>();
        request.add(new UserModelMessage(prompt() + "\n\nConversation transcript:\n" + transcript));
        ModelCompletionResponse response = responsesSummaryProtocol.stream(
                selectedModel.withModelId(selectedModel.getEffectiveCompressionModelId()),
                request,
                null,
                cancellationToken,
                new ModelRequestOptions(AiBehaviorSettings.REASONING_OFF, false, new ArrayList<BaseTool>())
        );
        if (cancellationToken != null && cancellationToken.isCancelled()) {
            return new ContextCompactionResult("", "");
        }
        return new ContextCompactionResult(createCompactSummaryContent(response.getText()), "");
    }

    private boolean shouldUseResponsesCompaction(ModelConfig selectedModel) {
        return selectedModel.isCompressionModelEnabled()
                && selectedModel.getProtocolType() == ModelProtocolType.CODEX_RESPONSES;
    }

    private boolean shouldUseOpenAiResponsesSummary(ModelConfig selectedModel) {
        return selectedModel.isCompressionModelEnabled()
                && selectedModel.getProtocolType() == ModelProtocolType.OPENAI_COMPATIBLE;
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

    private String toCompactTranscript(List<ChatMessage> messages) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (builder.length() > 0) {
                builder.append("\n\n---\n\n");
            }
            builder.append("## ").append(i + 1).append(". ").append(message.getRole().getProtocolName());
            if (message.getContent().trim().length() > 0) {
                builder.append("\n\n").append(MessageContentSanitizer.forModel(message));
            }
            if (message.hasToolCalls()) {
                builder.append("\n\nTool calls:\n");
                for (ToolCall call : message.getToolCalls()) {
                    builder.append("- ").append(call.getName()).append(": ").append(call.getArguments()).append('\n');
                }
            }
            if (message.getToolCallId().length() > 0) {
                builder.append("\n\nTool result for: ").append(message.getToolCallId());
            }
            if (message.getReasoningContent().trim().length() > 0) {
                builder.append("\n\nReasoning:\n").append(message.getReasoningContent());
            }
        }
        return builder.toString();
    }

    public String createCompactSummaryContent(String summary) {
        String formatted = formatCompactSummary(summary);
        return "This session is being continued from a previous conversation that ran out of context. "
                + "The summary below covers the earlier portion of the conversation.\n\n"
                + formatted
                + "\n\nContinue the conversation from where it left off without asking the user any further questions. Resume directly.";
    }

    private String createResponsesCompactFallbackContent() {
        return "This session is being continued from a previous conversation that ran out of context. "
                + "The earlier portion of the conversation has been compacted by the OpenAI Responses compact API. "
                + "Continue the conversation from where it left off without asking the user any further questions. Resume directly.";
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
