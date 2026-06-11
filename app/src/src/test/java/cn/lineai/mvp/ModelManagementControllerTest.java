package cn.lineai.mvp;

import cn.lineai.model.ModelConfig;
import cn.lineai.model.ModelProtocolType;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.junit.Assert;
import org.junit.Test;

public final class ModelManagementControllerTest {
    @Test
    public void selectModelUpdatesSelectionAndRefreshesModels() {
        Fixture fixture = new Fixture();

        fixture.controller.selectModel("m2");

        Assert.assertEquals("m2", fixture.store.selectedId);
        Assert.assertTrue(fixture.host.refreshedModels);
        Assert.assertTrue(fixture.host.rendered);
        Assert.assertFalse(fixture.host.returnedToModels);
    }

    @Test
    public void saveModelSelectsSavedIdAndReturnsToModels() {
        Fixture fixture = new Fixture();
        ModelConfig unsaved = model("", "Draft");

        fixture.controller.saveModel(unsaved);

        Assert.assertEquals("saved-id", fixture.store.selectedId);
        Assert.assertEquals("Draft", fixture.store.savedModel.getName());
        Assert.assertTrue(fixture.host.returnedToModels);
        Assert.assertTrue(fixture.host.rendered);
        Assert.assertFalse(fixture.host.refreshedModels);
    }

    @Test
    public void deleteModelsRefreshesModels() {
        Fixture fixture = new Fixture();

        fixture.controller.deleteModels(Arrays.asList("m1", "m2"));

        Assert.assertEquals(Arrays.asList("m1", "m2"), fixture.store.deletedIds);
        Assert.assertTrue(fixture.host.refreshedModels);
        Assert.assertTrue(fixture.host.rendered);
        Assert.assertFalse(fixture.host.returnedToModels);
    }

    @Test
    public void gettersDelegateToStore() {
        Fixture fixture = new Fixture();
        fixture.store.models.add(model("m1", "One"));
        fixture.store.selectedId = "m1";

        Assert.assertEquals(1, fixture.controller.getModels().size());
        Assert.assertEquals("One", fixture.controller.getModel("m1").getName());
        Assert.assertEquals("m1", fixture.controller.getSelectedModelId());
    }

    private static final class Fixture {
        private final FakeModelStore store = new FakeModelStore();
        private final FakeHost host = new FakeHost();
        private final ModelManagementController controller = new ModelManagementController(store, host);
    }

    private static final class FakeModelStore implements ModelManagementController.ModelStore {
        private final ArrayList<ModelConfig> models = new ArrayList<>();
        private String selectedId = "";
        private ModelConfig savedModel;
        private List<String> deletedIds = new ArrayList<>();

        @Override
        public List<ModelConfig> getModels() {
            return models;
        }

        @Override
        public ModelConfig getModel(String id) {
            for (ModelConfig model : models) {
                if (model.getId().equals(id)) {
                    return model;
                }
            }
            return null;
        }

        @Override
        public String getSelectedModelId() {
            return selectedId;
        }

        @Override
        public void setSelectedModelId(String id) {
            selectedId = id;
        }

        @Override
        public ModelConfig save(ModelConfig model) {
            savedModel = model.withId("saved-id");
            return savedModel;
        }

        @Override
        public void deleteModels(List<String> ids) {
            deletedIds = ids;
        }
    }

    private static final class FakeHost implements ModelManagementController.Host {
        private boolean refreshedModels;
        private boolean returnedToModels;
        private boolean rendered;

        @Override
        public void refreshModelsScreen() {
            refreshedModels = true;
        }

        @Override
        public void returnToModelsScreen() {
            returnedToModels = true;
        }

        @Override
        public void render() {
            rendered = true;
        }
    }

    private static ModelConfig model(String id, String name) {
        return new ModelConfig(
                id,
                name,
                ModelProtocolType.OPENAI_COMPATIBLE,
                "OpenAI",
                "https://example.invalid",
                "key",
                "gpt",
                ModelConfig.DEFAULT_TOOL_CALL_LIMIT
        );
    }
}
