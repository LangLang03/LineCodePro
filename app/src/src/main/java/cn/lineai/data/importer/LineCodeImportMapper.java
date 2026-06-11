package cn.lineai.data.importer;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.MessageRecord;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.ModelConfig;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.json.JSONArray;
import org.json.JSONObject;

public final class LineCodeImportMapper {
    public static final String KEY_MODELS = "@lineai_models";
    public static final String KEY_SELECTED_MODEL = "@lineai_selected_model";
    public static final String KEY_CONVERSATION_LIST = "@lineai_conversation_list";
    public static final String KEY_CURRENT_CONVERSATION = "@lineai_current_conversation";
    public static final String KEY_CONVERSATION_PREFIX = "@lineai_conv_";
    public static final String KEY_CONVERSATION_CHUNK_PREFIX = "@lineai_conv_chunk_";

    private static final String CONVERSATION_FILES_DIR = "conversations";

    public ImportedLineCodeData readPayload(File payloadDir) throws Exception {
        File asyncStorage = new File(payloadDir, "async-storage.json");
        if (!asyncStorage.isFile()) {
            return new ImportedLineCodeData(null, "", null, "", null);
        }
        return fromAsyncStorageJson(readUtf8(asyncStorage), payloadDir);
    }

    public ImportedLineCodeData fromAsyncStorageJson(String json, File payloadDir) throws Exception {
        Map<String, String> entries = readAsyncStorageEntries(json);
        ArrayList<ModelConfig> models = readModels(entries.get(KEY_MODELS));
        String selectedModelId = value(entries, KEY_SELECTED_MODEL);
        String currentConversationId = value(entries, KEY_CURRENT_CONVERSATION);
        ArrayList<ConversationRecord> conversations = readConversations(entries, payloadDir, currentConversationId);
        Map<String, String> settings = readSettings(entries);
        return new ImportedLineCodeData(models, selectedModelId, conversations, currentConversationId, settings);
    }

    public Map<String, String> readAsyncStorageEntries(String json) throws Exception {
        LinkedHashMap<String, String> entries = new LinkedHashMap<>();
        if (json == null || json.trim().length() == 0) {
            return entries;
        }
        JSONArray array = new JSONArray(json);
        for (int i = 0; i < array.length(); i++) {
            JSONObject entry = array.optJSONObject(i);
            if (entry == null) {
                continue;
            }
            String key = entry.optString("key");
            if (key.length() == 0 || entry.isNull("value")) {
                continue;
            }
            entries.put(key, entry.optString("value"));
        }
        return entries;
    }

    private ArrayList<ModelConfig> readModels(String rawModels) throws Exception {
        ArrayList<ModelConfig> models = new ArrayList<>();
        if (rawModels == null || rawModels.trim().length() == 0) {
            return models;
        }
        JSONArray array = new JSONArray(rawModels);
        for (int i = 0; i < array.length(); i++) {
            JSONObject object = array.optJSONObject(i);
            if (object != null) {
                ModelConfig model = ModelConfig.fromJson(object);
                if (model.getId().length() > 0) {
                    models.add(model);
                }
            }
        }
        return models;
    }

    private ArrayList<ConversationRecord> readConversations(
            Map<String, String> entries,
            File payloadDir,
            String currentConversationId
    ) throws Exception {
        ArrayList<ConversationRecord> conversations = new ArrayList<>();
        Set<String> ids = readConversationIds(entries);
        for (String id : ids) {
            String raw = entries.get(KEY_CONVERSATION_PREFIX + id);
            if (raw == null || raw.length() == 0) {
                continue;
            }
            ConversationRecord conversation = readConversation(id, raw, entries, payloadDir, id.equals(currentConversationId));
            if (conversation != null) {
                conversations.add(conversation);
            }
        }
        return conversations;
    }

    private Set<String> readConversationIds(Map<String, String> entries) throws Exception {
        LinkedHashSet<String> ids = new LinkedHashSet<>();
        String listRaw = entries.get(KEY_CONVERSATION_LIST);
        if (listRaw != null && listRaw.trim().length() > 0) {
            JSONArray list = new JSONArray(listRaw);
            for (int i = 0; i < list.length(); i++) {
                JSONObject meta = list.optJSONObject(i);
                if (meta != null && meta.optString("id").length() > 0) {
                    ids.add(meta.optString("id"));
                }
            }
        }
        for (String key : entries.keySet()) {
            if (key.startsWith(KEY_CONVERSATION_PREFIX) && !key.startsWith(KEY_CONVERSATION_CHUNK_PREFIX)) {
                ids.add(key.substring(KEY_CONVERSATION_PREFIX.length()));
            }
        }
        return ids;
    }

    private ConversationRecord readConversation(
            String id,
            String storedValue,
            Map<String, String> entries,
            File payloadDir,
            boolean current
    ) throws Exception {
        JSONObject stored = new JSONObject(storedValue);
        JSONObject conversation = resolveConversationObject(id, stored, entries, payloadDir);
        if (conversation == null) {
            return null;
        }
        String conversationId = conversation.optString("id", id);
        long createdAt = conversation.optLong("createdAt", System.currentTimeMillis());
        long updatedAt = conversation.optLong("updatedAt", createdAt);
        JSONArray rawMessages = conversation.optJSONArray("messages");
        ArrayList<MessageRecord> messages = new ArrayList<>();
        if (rawMessages != null) {
            for (int i = 0; i < rawMessages.length(); i++) {
                JSONObject message = rawMessages.optJSONObject(i);
                if (message != null) {
                    messages.add(readMessage(message, i));
                }
            }
        }
        return new ConversationRecord(
                conversationId,
                conversation.optString("title", "新对话"),
                "",
                createdAt,
                updatedAt,
                current,
                conversation.toString(),
                messages
        );
    }

    private JSONObject resolveConversationObject(
            String id,
            JSONObject stored,
            Map<String, String> entries,
            File payloadDir
    ) throws Exception {
        if (stored.optBoolean("chunked", false)) {
            int chunks = stored.optInt("chunks", 0);
            StringBuilder builder = new StringBuilder();
            for (int i = 0; i < chunks; i++) {
                String chunk = entries.get(KEY_CONVERSATION_CHUNK_PREFIX + id + "_" + i);
                if (chunk == null) {
                    throw new IllegalStateException("LineCode 聊天分片缺失: " + id + "_" + i);
                }
                builder.append(chunk);
            }
            return new JSONObject(builder.toString());
        }
        if ("file".equals(stored.optString("storage"))) {
            if (payloadDir == null) {
                throw new IllegalStateException("LineCode 聊天文件需要 payloadDir: " + id);
            }
            String fileName = stored.optString("fileName", safeConversationFileName(id));
            File target = new File(new File(payloadDir, CONVERSATION_FILES_DIR), fileName);
            if (!target.isFile()) {
                File backup = new File(target.getAbsolutePath() + ".bak");
                target = backup.isFile() ? backup : target;
            }
            return new JSONObject(readUtf8(target));
        }
        return stored;
    }

    private MessageRecord readMessage(JSONObject message, int index) {
        String id = message.optString("id");
        if (id.length() == 0) {
            id = "imported_" + index;
        }
        return new MessageRecord(
                id,
                roleFromStorage(message.optString("role")),
                message.optString("content"),
                message.optString("reasoningContent", message.optString("reasoning")),
                message.optLong("timestamp", System.currentTimeMillis()),
                message.optBoolean("streaming", false),
                message.optBoolean("hidden", false),
                message.optBoolean("excludeFromContext", false),
                message.optString("toolCallId"),
                message.optString("toolName"),
                message.optBoolean("isError", false),
                message.toString()
        );
    }

    private Map<String, String> readSettings(Map<String, String> entries) {
        LinkedHashMap<String, String> settings = new LinkedHashMap<>();
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            String key = entry.getKey();
            if (!isLineCodeSettingKey(key)) {
                continue;
            }
            settings.put(key, entry.getValue());
        }
        return settings;
    }

    private boolean isLineCodeSettingKey(String key) {
        if (!(key.startsWith("@lineai_") || key.startsWith("@linecode_"))) {
            return false;
        }
        if (KEY_MODELS.equals(key) || KEY_SELECTED_MODEL.equals(key)
                || KEY_CONVERSATION_LIST.equals(key) || KEY_CURRENT_CONVERSATION.equals(key)) {
            return false;
        }
        return !key.startsWith(KEY_CONVERSATION_PREFIX) && !key.startsWith(KEY_CONVERSATION_CHUNK_PREFIX);
    }

    private ChatMessage.Role roleFromStorage(String role) {
        if ("system".equals(role)) {
            return ChatMessage.Role.SYSTEM;
        }
        if ("assistant".equals(role)) {
            return ChatMessage.Role.ASSISTANT;
        }
        if ("tool".equals(role)) {
            return ChatMessage.Role.TOOL;
        }
        return ChatMessage.Role.USER;
    }

    private String value(Map<String, String> entries, String key) {
        String value = entries.get(key);
        return value == null ? "" : value;
    }

    private String safeConversationFileName(String id) {
        String safeId = id.replaceAll("[^a-zA-Z0-9_-]", "_");
        return (safeId.length() == 0 ? "conversation" : safeId) + ".json";
    }

    private String readUtf8(File file) throws Exception {
        FileInputStream input = new FileInputStream(file);
        try {
            ByteArrayOutputStream output = new ByteArrayOutputStream();
            byte[] buffer = new byte[4096];
            int read;
            while ((read = input.read(buffer)) != -1) {
                output.write(buffer, 0, read);
            }
            return output.toString(StandardCharsets.UTF_8.name());
        } finally {
            input.close();
        }
    }
}
