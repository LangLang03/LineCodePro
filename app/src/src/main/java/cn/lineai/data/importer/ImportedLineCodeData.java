package cn.lineai.data.importer;

import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.model.ModelConfig;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ImportedLineCodeData {
    private final List<ModelConfig> models;
    private final String selectedModelId;
    private final List<ConversationRecord> conversations;
    private final String currentConversationId;
    private final Map<String, String> settings;

    public ImportedLineCodeData(
            List<ModelConfig> models,
            String selectedModelId,
            List<ConversationRecord> conversations,
            String currentConversationId,
            Map<String, String> settings
    ) {
        this.models = Collections.unmodifiableList(new ArrayList<>(models == null ? Collections.emptyList() : models));
        this.selectedModelId = selectedModelId == null ? "" : selectedModelId;
        this.conversations = Collections.unmodifiableList(new ArrayList<>(conversations == null ? Collections.emptyList() : conversations));
        this.currentConversationId = currentConversationId == null ? "" : currentConversationId;
        this.settings = Collections.unmodifiableMap(new LinkedHashMap<>(settings == null ? Collections.emptyMap() : settings));
    }

    public List<ModelConfig> getModels() {
        return models;
    }

    public String getSelectedModelId() {
        return selectedModelId;
    }

    public List<ConversationRecord> getConversations() {
        return conversations;
    }

    public String getCurrentConversationId() {
        return currentConversationId;
    }

    public Map<String, String> getSettings() {
        return settings;
    }
}
