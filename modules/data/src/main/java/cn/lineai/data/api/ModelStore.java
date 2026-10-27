package cn.lineai.data.api;

import java.util.List;

import cn.lineai.core.model.ModelConfig;

/** Model configurations plus the single selected default. */
public interface ModelStore {

    List<ModelConfig> list();

    ModelConfig find(String id);

    ModelConfig selected();

    void save(ModelConfig model);

    void select(String id);

    void delete(List<String> ids);

    /** Seeds the first model from the legacy single-model settings keys, if any. */
    void seedFromSettings(SettingsStore settings);
}
