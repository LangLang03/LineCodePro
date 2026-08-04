package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;
import cn.lineai.model.tool.ToolResult;

import cn.lineai.ai.ImageInputPayload;
import cn.lineai.ai.ModelCancellationToken;
import cn.lineai.data.repository.ChatModeRepository;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.ToolSettingsStore;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelStore;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

final class ChatInteractionController {
    interface Host {
        String nextId();

        String agentTerminatedMessage();

        String syncModePermission();

        void ensureCurrentConversation();

        void persistCurrentConversation();

        void loadConversation(String id);

        void cancelActiveGeneration();

        void startGenerationKeepAlive();

        void stopGenerationKeepAlive();

        void setCurrentCancellationToken(ModelCancellationToken cancellationToken);

        void markStreamingMessagesStopped();

        void resetTodoState();

        void hideOverlays();

        void showChatScreen();

        void setComposerDraft(String text, ArrayList<InputAttachment> attachments);

        void render();
    }

    private final ArrayList<ChatMessage> messages;
    private final ChatSessionStore chatSessionStore;
    private final ConversationStore conversationRepository;
    private final ModelStore modelRepository;
    private final ChatModeRepository chatModeRepository;
    private final ToolSettingsStore toolSettingsRepository;
    private final ContextCompactionController contextCompactionController;
    private final GenerationFlowController generationFlowController;
    private final Host host;
    private String lastMessageModelId = "";

    ChatInteractionController(
            ArrayList<ChatMessage> messages,
            ChatSessionStore chatSessionStore,
            ConversationStore conversationRepository,
            ModelStore modelRepository,
            ChatModeRepository chatModeRepository,
            ToolSettingsStore toolSettingsRepository,
            ContextCompactionController contextCompactionController,
            GenerationFlowController generationFlowController,
            Host host
    ) {
        this.messages = messages;
        this.chatSessionStore = chatSessionStore;
        this.conversationRepository = conversationRepository;
        this.modelRepository = modelRepository;
        this.chatModeRepository = chatModeRepository;
        this.toolSettingsRepository = toolSettingsRepository;
        this.contextCompactionController = contextCompactionController;
        this.generationFlowController = generationFlowController;
        this.host = host;
    }

    void newConversation() {
        host.cancelActiveGeneration();
        if (chatSessionStore.isStreaming()) {
            host.markStreamingMessagesStopped();
            generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        }
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        host.persistCurrentConversation();
        chatSessionStore.startNewConversation(System.currentTimeMillis());
        contextCompactionController.onConversationChanged();
        generationFlowController.clearSessionAutoToolConfirmations();
        lastMessageModelId = "";
        host.hideOverlays();
        host.showChatScreen();
        host.render();
    }

    void selectConversation(String id) {
        if (id == null || id.length() == 0) {
            host.hideOverlays();
            return;
        }
        host.cancelActiveGeneration();
        boolean wasStreaming = chatSessionStore.isStreaming();
        if (wasStreaming) {
            host.markStreamingMessagesStopped();
            generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        }
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        if (wasStreaming || !id.equals(chatSessionStore.getCurrentConversationId())) {
            host.persistCurrentConversation();
        }
        host.loadConversation(id);
        lastMessageModelId = "";
        generationFlowController.clearSessionAutoToolConfirmations();
        host.resetTodoState();
        host.hideOverlays();
        host.showChatScreen();
        host.render();
    }

    void deleteConversation(String id) {
        if (id == null || id.length() == 0) {
            return;
        }
        conversationRepository.deleteConversation(id);
        if (id.equals(chatSessionStore.getCurrentConversationId())) {
            host.cancelActiveGeneration();
            chatSessionStore.setStreaming(false);
            host.stopGenerationKeepAlive();
            chatSessionStore.clearCurrentConversation();
            contextCompactionController.onConversationChanged();
            generationFlowController.clearSessionAutoToolConfirmations();
            host.resetTodoState();
            lastMessageModelId = "";
        }
        host.render();
    }

    void sendMessage(String text) {
        sendMessage(text, Collections.emptyList());
    }

    void sendMessage(String text, List<InputAttachment> attachments) {
        dispatchMessage(text, attachments, "");
    }

    void sendMessageWithImage(String text, List<InputAttachment> attachments,
                              String imageBase64, String imageMimeType, String imageName) {
        String imageNameSafe = imageName == null ? "" : imageName.trim();
        String trimmed = text == null ? "" : text.trim();
        // 有图片但无文本时，给 UI 一个占位说明，避免显示空消息；同时确保 Codex 协议拿到非空 prompt
        if (trimmed.length() == 0) {
            trimmed = imageNameSafe.length() > 0 ? "已附加图片：" + imageNameSafe : "已附加图片";
        }
        String rawInputJson = "";
        if (imageBase64 != null && imageBase64.length() > 0) {
            String mimeType = imageMimeType == null ? "" : imageMimeType;
            try {
                rawInputJson = ImageInputPayload.rawInputJson(trimmed, mimeType, imageBase64);
            } catch (org.json.JSONException ignored) {
                rawInputJson = "";
            }
        }
        if (rawInputJson.length() == 0) {
            // 图片解析失败则降级为普通文本发送
            dispatchMessage(trimmed, attachments, "");
            return;
        }
        dispatchMessage(trimmed, attachments, rawInputJson);
    }

    private void dispatchMessage(String text, List<InputAttachment> attachments, String rawInputJson) {
        String trimmed = text == null ? "" : text.trim();
        ArrayList<InputAttachment> safeAttachments = sanitizeAttachments(attachments);
        if ((trimmed.isEmpty() && safeAttachments.isEmpty() && rawInputJson.length() == 0)
                || chatSessionStore.isStreaming()) {
            return;
        }
        host.ensureCurrentConversation();
        String userContent = composeUserContent(trimmed, safeAttachments);
        ChatMessage userMessage = new ChatMessage(
                host.nextId(), ChatMessage.Role.USER, userContent, "",
                false, false, false,
                Collections.<cn.lineai.model.tool.ToolCall>emptyList(),
                Collections.<cn.lineai.model.tool.ToolResult>emptyList(),
                "", "", false, "", "", "",
                "", rawInputJson, safeAttachments);
        messages.add(userMessage);
        host.persistCurrentConversation();
        ModelConfig selectedModel = modelRepository.getSelectedModel();
        if (selectedModel == null) {
            messages.add(new ChatMessage(host.nextId(), ChatMessage.Role.ASSISTANT,
                    "还没有可用模型。请进入 设置 → 模型管理 → 添加模型，保存后再发送消息。",
                    false));
            host.persistCurrentConversation();
            host.render();
            return;
        }

        String currentModelId = selectedModel.getModelId();
        if (lastMessageModelId.length() > 0 && !lastMessageModelId.equals(currentModelId)) {
            messages.add(ChatMessage.modelSwitchNotice(host.nextId(), lastMessageModelId, currentModelId));
            host.persistCurrentConversation();
        }
        lastMessageModelId = currentModelId;

        int generationId = chatSessionStore.nextGenerationId();
        ModelCancellationToken cancellationToken = new ModelCancellationToken();
        host.setCurrentCancellationToken(cancellationToken);
        chatSessionStore.setStreaming(true);
        host.startGenerationKeepAlive();
        host.render();

        String activeUserMessageId = messages.get(messages.size() - 1).getId();
        if (contextCompactionController.shouldAutoCompactBeforeRequest(selectedModel, activeUserMessageId)) {
            contextCompactionController.startContextCompaction(
                    generationId,
                    selectedModel,
                    cancellationToken,
                    true,
                    activeUserMessageId,
                    userContent
            );
            return;
        }
        // 软触发增量压缩：50% 占用时把最早的一部分消息压缩成摘要，避免上下文继续
        // 增长到 80% 时才一次性处理大量历史。这是动态压缩的核心环节。
        if (contextCompactionController.shouldAutoSoftCompactBeforeRequest(selectedModel, activeUserMessageId)) {
            contextCompactionController.startSoftContextCompaction(
                    generationId,
                    selectedModel,
                    cancellationToken,
                    true,
                    activeUserMessageId,
                    userContent
            );
            return;
        }
        generationFlowController.startInitialModelRequest(generationId, selectedModel, cancellationToken, userContent);
    }

    void recallMessage(String messageId) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        String targetId = messageId == null ? "" : messageId;
        if (targetId.length() == 0) {
            return;
        }
        int targetIndex = -1;
        for (int i = 0; i < messages.size(); i++) {
            ChatMessage message = messages.get(i);
            if (targetId.equals(message.getId()) && message.getRole() == ChatMessage.Role.USER) {
                targetIndex = i;
                break;
            }
        }
        if (targetIndex < 0) {
            return;
        }
        ChatMessage recalled = messages.get(targetIndex);
        String recalledText = recallText(recalled.getContent(), recalled.getAttachments());
        ArrayList<InputAttachment> recalledAttachments = new ArrayList<>(recalled.getAttachments());
        while (messages.size() > targetIndex) {
            messages.remove(messages.size() - 1);
        }
        host.persistCurrentConversation();
        host.render();
        host.setComposerDraft(recalledText, recalledAttachments);
    }

    void changeChatMode(String mode) {
        if (chatSessionStore.isStreaming()) {
            return;
        }
        chatModeRepository.applyMode(mode);
        chatModeRepository.applyPermissionForMode(mode, toolSettingsRepository);
        host.syncModePermission();
        host.render();
    }

    void stopGeneration() {
        generationFlowController.flushPendingAssistantDelta();
        host.cancelActiveGeneration();
        chatSessionStore.setStreaming(false);
        host.stopGenerationKeepAlive();
        chatSessionStore.invalidateActiveGeneration();
        host.markStreamingMessagesStopped();
        generationFlowController.markRunningAgentProgressStopped(host.agentTerminatedMessage());
        host.persistCurrentConversation();
        host.render();
    }

    void clearCurrentConversation() {
        String currentConversationId = chatSessionStore.getCurrentConversationId();
        messages.clear();
        if (currentConversationId.length() > 0) {
            conversationRepository.deleteConversation(currentConversationId);
        }
        chatSessionStore.clearCurrentConversation();
        contextCompactionController.onConversationChanged();
        generationFlowController.clearSessionAutoToolConfirmations();
    }

    void resetModelTracking() {
        lastMessageModelId = "";
    }

    private ArrayList<InputAttachment> sanitizeAttachments(List<InputAttachment> rawAttachments) {
        ArrayList<InputAttachment> result = new ArrayList<>();
        if (rawAttachments == null) {
            return result;
        }
        for (InputAttachment attachment : rawAttachments) {
            if (attachment == null || attachment.getPath().length() == 0) {
                continue;
            }
            boolean exists = false;
            for (InputAttachment current : result) {
                if (current.matches(attachment.getPath(), attachment.getSource())) {
                    exists = true;
                    break;
                }
            }
            if (!exists) {
                result.add(new InputAttachment(attachment.getName(), attachment.getPath(), attachment.getSource()));
            }
        }
        return result;
    }

    /**
     * 返回用户输入文本，不再将附件路径拼接到消息正文中。
     * 附件信息仅通过 ChatMessage.attachments 元数据传递。
     */
    private String composeUserContent(String text, List<InputAttachment> attachments) {
        String trimmed = text == null ? "" : text.trim();
        return trimmed;
    }

    private String recallText(String content, List<InputAttachment> attachments) {
        String value = content == null ? "" : content;
        // 兼容旧消息：旧版 composeUserContent 在只有附件无文本时会写入 "已附加文件"
        if ("已附加文件".equals(value.trim()) && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        if (value.trim().length() == 0 && attachments != null && !attachments.isEmpty()) {
            return "";
        }
        return value;
    }
}
