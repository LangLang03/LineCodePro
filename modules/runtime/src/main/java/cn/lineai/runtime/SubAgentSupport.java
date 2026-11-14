package cn.lineai.runtime;

import java.util.ArrayList;
import java.util.List;

import cn.lineai.core.model.AgentExtension;
import cn.lineai.core.model.AgentSessionRecord;
import cn.lineai.core.model.AgentSessionStatus;
import cn.lineai.core.model.Conversation;
import cn.lineai.feature.chat.ChatState;
import cn.lineai.tool.api.AgentStatus;

/**
 * Pure helpers behind the sub agent registry: status mapping both ways, record recovery and
 * the small text utilities the cards and titles use. Kept separate so the registry stays
 * within the line budget.
 */
final class SubAgentSupport {

    private SubAgentSupport() {
    }

    /**
     * Returns the records that must be rewritten because they died mid-turn
     * ({@code RUNNING}/{@code WAITING_INPUT} at load time become INTERRUPTED).
     *
     * @param records the loaded records
     * @param now transition timestamp in epoch milliseconds
     * @return the records that changed; empty when everything is consistent
     */
    static List<AgentSessionRecord> interruptedOnLoad(List<AgentSessionRecord> records,
                                                      long now) {
        List<AgentSessionRecord> changed = new ArrayList<>();
        for (AgentSessionRecord record : records) {
            if (record.status == AgentSessionStatus.RUNNING
                    || record.status == AgentSessionStatus.WAITING_INPUT) {
                changed.add(record.withStatus(AgentSessionStatus.INTERRUPTED, now, ""));
            }
        }
        return changed;
    }

    /** Display name of an agent definition. */
    static String displayName(AgentExtension agent) {
        return agent.name.isEmpty() ? agent.slug : agent.name;
    }

    /** Conversation kind of a persisted record. */
    static String kindOf(AgentSessionRecord record) {
        return record.persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
    }

    /** Conversation kind of a live session. */
    static String kindOf(SubAgentRegistry.Session session) {
        return session.persistent ? Conversation.KIND_AGENT : Conversation.KIND_AGENT_TRANSIENT;
    }

    /** Persisted status for one live status. */
    static AgentSessionStatus recordStatus(AgentStatus status) {
        switch (status) {
            case RUNNING:
                return AgentSessionStatus.RUNNING;
            case WAITING_INPUT:
                return AgentSessionStatus.WAITING_INPUT;
            case FAILED:
                return AgentSessionStatus.FAILED;
            default:
                return AgentSessionStatus.COMPLETED;
        }
    }

    /** Entry status for one persisted status. */
    static AgentStatus entryStatus(AgentSessionStatus status) {
        switch (status) {
            case RUNNING:
                return AgentStatus.RUNNING;
            case WAITING_INPUT:
                return AgentStatus.WAITING_INPUT;
            case FAILED:
                return AgentStatus.FAILED;
            case INTERRUPTED:
                return AgentStatus.INTERRUPTED;
            default:
                return AgentStatus.COMPLETED;
        }
    }

    /** Id of the last completed assistant answer, used as the persisted last turn marker. */
    static String lastAssistantId(ChatState state) {
        for (int i = state.messages.size() - 1; i >= 0; i--) {
            cn.lineai.core.model.Message message = state.messages.get(i);
            if (message.role == cn.lineai.core.model.Role.ASSISTANT
                    && message.toolCalls.isEmpty()
                    && message.status == cn.lineai.core.model.Message.Status.COMPLETE
                    && message.visibility == cn.lineai.core.model.Message.Visibility.VISIBLE) {
                return message.id;
            }
        }
        return "";
    }

    /** Scans backwards for the last completed assistant answer without tool calls. */
    static String finalOutput(ChatState state) {
        for (int i = state.messages.size() - 1; i >= 0; i--) {
            cn.lineai.core.model.Message message = state.messages.get(i);
            if (message.role == cn.lineai.core.model.Role.ASSISTANT
                    && message.toolCalls.isEmpty()
                    && message.status == cn.lineai.core.model.Message.Status.COMPLETE
                    && message.visibility == cn.lineai.core.model.Message.Visibility.VISIBLE) {
                return message.plainText();
            }
        }
        return "";
    }

    /** First line of a task, clipped for the conversation title. */
    static String firstLine(String task) {
        String line = task == null ? "" : task.trim();
        int newline = line.indexOf('\n');
        if (newline > 0) line = line.substring(0, newline);
        return line.length() > 60 ? line.substring(0, 60) : line;
    }
}
