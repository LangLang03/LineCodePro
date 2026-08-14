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
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
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
    /**
     * 会话持久化走单线程后台执行器，避免在主线程热路径（每次收发/流式步骤/工具批次后）同步写库造成 ANR。
     * 采用 latest-wins 合并：若已有一次在途写入，则仅更新待写快照，始终持久化最新状态，避免任务堆积。
     */
    private final ExecutorService persistExecutor = Executors.newSingleThreadExecutor(runnable -> {
        Thread thread = new Thread(runnable, "linecode-conversation-persist");
        thread.setDaemon(true);
        return thread;
    });
    private final Object persistLock = new Object();
    private boolean persistScheduled;
    private ConversationRecord persistSnapshot;

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
        // 加载/切换会话前先落库所有已排队的异步持久化，确保后续同步写入不会被过期异步写覆盖。
        awaitPendingPersist();
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
        // 消息列表在这里已快照为不可变的 ConversationRecord，DB 写入在线程上异步执行，
        // 不阻塞主线程热路径，也避免后续对 messages 的并发修改造成数据竞争。
        boolean learningEnabled = aiBehaviorSettingsRepository.get().isLearningModeEnabled();
        synchronized (persistLock) {
            persistSnapshot = conversation;
            if (persistScheduled) {
                return; // 已有写入在途，latest-wins：保留最新快照即可
            }
            persistScheduled = true;
        }
        persistExecutor.execute(() -> runPersist(learningEnabled));
    }

    private void runPersist(boolean learningEnabled) {
        ConversationRecord snapshot;
        synchronized (persistLock) {
            snapshot = persistSnapshot;
            persistScheduled = false;
            persistSnapshot = null;
        }
        if (snapshot == null) {
            return;
        }
        conversationStore.saveConversation(snapshot);
        if (learningEnabled) {
            try {
                learningContextStore.indexConversation(snapshot.getProjectId(), snapshot);
            } catch (Exception ignored) {
            }
        }
    }

    /** 阻塞等待所有已排队的持久化任务落库，用于加载/切换会话前避免过期异步写入覆盖新状态。 */
    private void awaitPendingPersist() {
        final CountDownLatch latch = new CountDownLatch(1);
        synchronized (persistLock) {
            if (!persistScheduled) {
                return;
            }
            persistExecutor.execute(latch::countDown);
        }
        try {
            latch.await(10L, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
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
