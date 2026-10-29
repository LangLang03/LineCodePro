package cn.lineai.feature.chat;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.kernel.error.DomainException;
import cn.lineai.core.kernel.util.Ids;
import cn.lineai.core.model.ContentBlock;
import cn.lineai.core.model.Message;
import cn.lineai.core.model.ModelConfig;
import cn.lineai.core.model.Role;

/**
 * Builds the assistant message that records a failed generation.
 *
 * <p>The legacy app replaced the streaming answer with the formatted failure text so the
 * conversation kept a visible explanation instead of an empty row. The same text is written to
 * the error log by the controller.</p>
 */
final class ChatFailure {

    private ChatFailure() {
    }

    /** Raw provider/transport detail; never prettified, so the real response body survives. */
    static String detail(DomainException error) {
        String raw = error.detail();
        return raw == null || raw.trim().isEmpty() ? error.code().name() : raw.trim();
    }

    /** Replaces the streaming row when there is one, otherwise creates a failed assistant row. */
    static Message message(Message streaming, DomainException error, String conversationId,
                           int seq, String text, long now, ModelConfig model,
                           List<ContentBlock> partial) {
        if (streaming == null) {
            return Message.builder()
                    .id(Ids.prefixed("msg"))
                    .conversationId(conversationId)
                    .seq(seq)
                    .role(Role.ASSISTANT)
                    .status(Message.Status.FAILED)
                    .model(model.modelId, model.providerId)
                    .error(error.code().name())
                    .finishReason("error")
                    .addBlock(ContentBlock.error(text))
                    .times(now, now)
                    .build();
        }
        List<ContentBlock> blocks = new ArrayList<>(partial);
        blocks.add(ContentBlock.error(text));
        return streaming.toBuilder()
                .blocks(blocks)
                .status(Message.Status.FAILED)
                .error(error.code().name())
                .finishReason("error")
                .times(streaming.createdAt, now)
                .build();
    }
}
