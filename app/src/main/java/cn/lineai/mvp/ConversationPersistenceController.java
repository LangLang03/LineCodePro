package cn.lineai.mvp;
import cn.lineai.model.tool.ToolCall;

import android.content.Context;
import cn.lineai.data.repository.AiBehaviorSettingsRepository;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationStore;
import cn.lineai.data.repository.LearningContextStore;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.InputAttachment;
import java.util.ArrayList;
import org.json.JSONArray;
import org.json.JSONObject;

final class ConversationPersistenceController {
    interface Host {
        String projectPath();

        String defaultConversationTitle(Context context);

        String interruptedGenerationMessage(Context context);
    }

    private final Context context;
    private final ChatSessionStore chatSessionStore;
    private final ArrayList<ChatMessage> messages;
    private final ConversationStore conversationStore;
    private final AiBehaviorSettingsRepository aiBehaviorSettingsRepository;
    private final LearningContextStore learningContextStore;
    private final Host host;

    ConversationPersistenceController(
            Context context,
            ChatSessionStore chatSessionStore,
            ArrayList<ChatMessage> messages,
            ConversationStore conversationStore,
            AiBehaviorSettingsRepository aiBehaviorSettingsRepository,
            LearningContextStore learningContextStore,
            Host host
    ) {
        this.context = context.getApplicationContext();
        this.chatSessionStore = chatSessionStore;
        this.messages = messages;
        this.conversationStore = conversationStore;
        this.aiBehaviorSettingsRepository = aiBehaviorSettingsRepository;
        this.learningContextStore = learningContextStore;
        this.host = host;
    }

    void loadCurrentConversation() {
        ConversationRecord conversation = conversationStore.getCurrentConversation();
        if (conversation != null) {
            applyConversation(conversation);
        }
    }

    void loadConversation(String id) {
        ConversationRecord conversation = conversationStore.getConversation(id);
        if (conversation == null) {
            return;
        }
        applyConversation(conversation);
        conversationStore.setCurrentConversationId(conversation.getId());
    }

    void applyConversation(ConversationRecord conversation) {
        ConversationResumeSanitizer.Result result = ConversationResumeSanitizer.sanitize(
                conversation,
                host.interruptedGenerationMessage(context)
        );
        ConversationRecord nextConversation = result.conversation();
        chatSessionStore.applyConversation(nextConversation);
        chatSessionStore.setStreaming(false);
        if (result.changed() && nextConversation != null) {
            conversationStore.saveConversation(nextConversation);
        }
    }

    void ensureCurrentConversation() {
        chatSessionStore.ensureCurrentConversation(System.currentTimeMillis());
    }

    void persistCurrentConversation() {
        String currentConversationId = chatSessionStore.getCurrentConversationId();
        if (currentConversationId.length() == 0) {
            return;
        }
        if (messages.isEmpty()) {
            return;
        }
        long now = System.currentTimeMillis();
        ArrayList<MessageRecord> records = new ArrayList<>();
        for (ChatMessage message : messages) {
            records.add(new MessageRecord(
                    message.getId(),
                    message.getRole(),
                    message.getContent(),
                    message.getReasoningContent(),
                    now,
                    false,
                    message.isHidden(),
                    message.isExcludeFromContext(),
                    message.getToolCallId(),
                    message.getToolName(),
                    message.isError(),
                    messageRawJson(message)
            ));
        }
        String projectPath = host.projectPath();
        ConversationRecord conversation = new ConversationRecord(
                currentConversationId,
                deriveTitle(),
                projectPath,
                chatSessionStore.getCurrentConversationCreatedAt() > 0
                        ? chatSessionStore.getCurrentConversationCreatedAt()
                        : now,
                now,
                true,
                "",
                records
        );
        conversationStore.saveConversation(conversation);
        if (aiBehaviorSettingsRepository.get().isLearningModeEnabled()) {
            learningContextStore.indexConversation(projectPath, conversation);
        }
    }

    String deriveTitle() {
        for (ChatMessage message : messages) {
            if (message.getRole() == ChatMessage.Role.USER && message.getContent().trim().length() > 0) {
                String firstLine = message.getContent().trim().replace('\n', ' ');
                return firstLine.length() > 28 ? firstLine.substring(0, 28) + "..." : firstLine;
            }
        }
        return host.defaultConversationTitle(context);
    }

    String messageRawJson(ChatMessage message) {
        if (message == null) {
            return "";
        }
        try {
            JSONObject object = new JSONObject();
            if (message.getRole() == ChatMessage.Role.TOOL) {
                object.put("diff_id", message.getDiffId());
                object.put("review_state", message.getReviewState());
                object.put("review_message", message.getReviewMessage());
            }
            if (message.hasToolCalls()) {
                JSONArray array = new JSONArray();
                for (ToolCall call : message.getToolCalls()) {
                    array.put(new JSONObject()
                            .put("id", call.getId())
                            .put("name", call.getName())
                            .put("arguments", call.getArguments()));
                }
                object.put("tool_calls", array);
            }
            if (message.isCompactBlock()) {
                object.put("compact_status", message.getCompactStatus());
            }
            if (message.getResponseInputItemJson().length() > 0) {
                object.put("response_input_item_json", message.getResponseInputItemJson());
            }
            if (message.hasAttachments()) {
                JSONArray array = new JSONArray();
                for (InputAttachment attachment : message.getAttachments()) {
                    array.put(new JSONObject()
                            .put("name", attachment.getName())
                            .put("path", attachment.getPath())
                            .put("source", attachment.getSource()));
                }
                object.put("attachments", array);
            }
            return object.length() == 0 ? "" : object.toString();
        } catch (Exception ignored) {
            return "";
        }
    }
}
