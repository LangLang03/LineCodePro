package cn.lineai.model;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

public final class ChatUiState {
    private final String projectLabel;
    private final String projectPath;
    private final String modelLabel;
    private final String selectedModelId;
    private final String contextLabel;
    private final int contextPercent;
    private final boolean streaming;
    private final boolean hasConfiguredModel;
    private final boolean thinkingScrollEnabled;
    private final boolean thinkingAutoExpandEnabled;
    private final boolean codeWrapEnabled;
    private final String browserMode;
    private final String enterKeyBehavior;
    private final String chatMode;
    private final String conversationId;
    private final List<ChatMessage> messages;
    private final List<ModelConfig> availableModels;

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, true, false, false, OutputSettings.BROWSER_BUILTIN,
                InputSettings.ENTER_SEND, ChatMode.DEFAULT, messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, false,
                OutputSettings.BROWSER_BUILTIN, InputSettings.ENTER_SEND, ChatMode.DEFAULT, messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, ChatMode.DEFAULT, messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String chatMode, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, chatMode, "", messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String chatMode,
            String conversationId, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, InputSettings.ENTER_SEND, chatMode, conversationId, messages);
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages
    ) {
        this(projectLabel, projectPath, modelLabel, contextLabel, contextPercent, streaming,
                hasConfiguredModel, thinkingScrollEnabled, thinkingAutoExpandEnabled, codeWrapEnabled,
                browserMode, enterKeyBehavior, chatMode, conversationId, messages, "", Collections.emptyList());
    }

    public ChatUiState(
            String projectLabel, String projectPath, String modelLabel, String contextLabel,
            int contextPercent, boolean streaming, boolean hasConfiguredModel,
            boolean thinkingScrollEnabled, boolean thinkingAutoExpandEnabled,
            boolean codeWrapEnabled, String browserMode, String enterKeyBehavior,
            String chatMode, String conversationId, List<ChatMessage> messages,
            String selectedModelId, List<ModelConfig> availableModels
    ) {
        this.projectLabel = projectLabel;
        this.projectPath = projectPath == null ? "" : projectPath;
        this.modelLabel = modelLabel;
        this.selectedModelId = selectedModelId == null ? "" : selectedModelId;
        this.contextLabel = contextLabel;
        this.contextPercent = contextPercent;
        this.streaming = streaming;
        this.hasConfiguredModel = hasConfiguredModel;
        this.thinkingScrollEnabled = thinkingScrollEnabled;
        this.thinkingAutoExpandEnabled = thinkingAutoExpandEnabled;
        this.codeWrapEnabled = codeWrapEnabled;
        this.browserMode = OutputSettings.normalizeBrowserMode(browserMode);
        this.enterKeyBehavior = InputSettings.normalizeEnterKeyBehavior(enterKeyBehavior);
        this.chatMode = ChatMode.normalize(chatMode);
        this.conversationId = conversationId == null ? "" : conversationId;
        this.messages = messages == null ? Collections.emptyList() : Collections.unmodifiableList(messages);
        this.availableModels = availableModels == null
                ? Collections.emptyList()
                : Collections.unmodifiableList(new ArrayList<>(availableModels));
    }

    public String getProjectLabel() { return projectLabel; }
    public String getProjectPath() { return projectPath; }
    public String getModelLabel() { return modelLabel; }
    public String getSelectedModelId() { return selectedModelId; }
    public List<ModelConfig> getAvailableModels() { return availableModels; }
    public String getContextLabel() { return contextLabel; }
    public int getContextPercent() { return contextPercent; }
    public boolean isStreaming() { return streaming; }
    public boolean hasConfiguredModel() { return hasConfiguredModel; }
    public boolean isThinkingScrollEnabled() { return thinkingScrollEnabled; }
    public boolean isThinkingAutoExpandEnabled() { return thinkingAutoExpandEnabled; }
    public boolean isCodeWrapEnabled() { return codeWrapEnabled; }
    public String getBrowserMode() { return browserMode; }
    public String getEnterKeyBehavior() { return enterKeyBehavior; }
    public String getChatMode() { return chatMode; }
    public String getConversationId() { return conversationId; }
    public List<ChatMessage> getMessages() { return messages; }
}
