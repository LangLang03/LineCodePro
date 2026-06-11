package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import java.util.List;

public interface ModelController {
    List<ModelConfig> getModels();

    ModelConfig getModel(String id);

    String getSelectedModelId();

    void onModelSelected(String id);

    void onModelSaved(ModelConfig model);

    void onModelsDeleted(List<String> ids);

    void onModelQuickSwitch(String modelId);
}
