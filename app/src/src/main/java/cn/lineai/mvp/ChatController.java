package cn.lineai.mvp;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.model.InputAttachment;
import java.util.List;

public interface ChatController {
    void onNewConversation();

    void onConversationSelected(String id);

    void onConversationDeleted(String id);

    void onSendMessage(String text);

    void onSendMessage(String text, List<InputAttachment> attachments);

    void onRecallMessage(String messageId);

    void onAttachmentPickerRequested();

    void onAttachmentPickerNodeSelected(String path, boolean directory);

    void onAttachmentPickerCancelled();

    void onChatModeChanged(String mode);

    void onStopGeneration();

    void onToolReview(String toolCallId, String state, String diffId);

    List<ConversationRecord> getConversationMetas();

    String getCurrentConversationId();
}
