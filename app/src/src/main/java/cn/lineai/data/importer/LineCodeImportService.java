package cn.lineai.data.importer;

import android.content.Context;
import cn.lineai.data.repository.ConversationRecord;
import cn.lineai.data.repository.ConversationRepository;
import cn.lineai.data.repository.SettingsRepository;
import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelRepository;
import java.io.File;
import java.util.Map;

public final class LineCodeImportService {
    public enum Mode {
        MERGE,
        REPLACE
    }

    private final LineCodeImportMapper mapper = new LineCodeImportMapper();
    private final ModelRepository modelRepository;
    private final ConversationRepository conversationRepository;
    private final SettingsRepository settingsRepository;

    public LineCodeImportService(Context context) {
        modelRepository = new ModelRepository(context);
        conversationRepository = new ConversationRepository(context);
        settingsRepository = new SettingsRepository(context);
    }

    public ImportedLineCodeData importPayload(File payloadDir, Mode mode) throws Exception {
        ImportedLineCodeData data = mapper.readPayload(payloadDir);
        importData(data, mode == null ? Mode.MERGE : mode);
        return data;
    }

    public void importData(ImportedLineCodeData data, Mode mode) {
        if (data == null) {
            return;
        }
        if (mode == Mode.REPLACE) {
            modelRepository.clearAll();
            conversationRepository.clearAll();
            settingsRepository.clearLineCodeSettings();
        }
        for (ModelConfig model : data.getModels()) {
            modelRepository.save(model);
        }
        if (data.getSelectedModelId().length() > 0) {
            modelRepository.setSelectedModelId(data.getSelectedModelId());
        }
        for (Map.Entry<String, String> entry : data.getSettings().entrySet()) {
            settingsRepository.setString(entry.getKey(), entry.getValue());
        }
        for (ConversationRecord conversation : data.getConversations()) {
            conversationRepository.saveConversation(conversation);
        }
        if (data.getCurrentConversationId().length() > 0) {
            conversationRepository.setCurrentConversationId(data.getCurrentConversationId());
        }
    }
}
