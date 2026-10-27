package cn.lineai.data.sqlite;

import android.app.Application;
import cn.lineai.core.kernel.time.Clock;
import cn.lineai.core.model.ModelConfig;
import java.io.File;
import java.util.Arrays;
import org.junit.After;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.junit.runner.RunWith;
import org.robolectric.RobolectricTestRunner;
import org.robolectric.annotation.Config;
import static org.junit.Assert.*;

@RunWith(RobolectricTestRunner.class)
@Config(sdk = 34, application = Application.class)
public class SqliteModelStoreTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();
    private LineCodeDatabase database;
    private SqliteModelStore models;

    @Before public void create() {
        database = new LineCodeDatabase(new File(temporary.getRoot(), "models.db"));
        models = new SqliteModelStore(database);
    }

    @After public void close() { database.close(); }

    @Test public void firstModelBecomesSelectedAutomatically() {
        models.save(model("m1", "DeepSeek", false));
        assertTrue(models.selected().isDefault);
        assertEquals("m1", models.selected().id);
    }

    @Test public void selectingAnotherModelClearsPreviousDefault() {
        models.save(model("m1", "DeepSeek", false));
        models.save(model("m2", "Claude", false));
        models.select("m2");
        assertEquals("m2", models.selected().id);
        long defaults = models.list().stream().filter(model -> model.isDefault).count();
        assertEquals(1, defaults);
    }

    @Test public void savingWithDefaultFlagReplacesTheDefault() {
        models.save(model("m1", "DeepSeek", false));
        models.save(model("m2", "Claude", true));
        assertEquals("m2", models.selected().id);
        assertFalse(models.find("m1").isDefault);
    }

    @Test public void deletingTheSelectedModelPromotesAnother() {
        models.save(model("m1", "DeepSeek", false));
        models.save(model("m2", "Claude", false));
        models.select("m2");
        models.delete(Arrays.asList("m2"));
        assertEquals("m1", models.selected().id);
        assertTrue(models.find("m1").isDefault);
    }

    @Test public void saveUpdatesEveryEditableField() {
        models.save(model("m1", "DeepSeek", false));
        ModelConfig updated = models.find("m1").toBuilder()
                .identity("m1", "Renamed")
                .endpoint("anthropic", "claude", "https://api.anthropic.com")
                .context(200000, 8192)
                .capabilities(true, true)
                .build();
        models.save(updated);
        ModelConfig stored = models.find("m1");
        assertEquals("Renamed", stored.displayName());
        assertEquals("https://api.anthropic.com", stored.baseUrl);
        assertEquals(200000, stored.contextSize);
        assertTrue(stored.supportsVision);
    }

    @Test public void seedsOnceFromLegacySettings() {
        SqliteSettingsStore settings = new SqliteSettingsStore(database, Clock.fixed(1));
        settings.put(SqliteModelStore.KEY_BASE_URL, "https://api.deepseek.com");
        settings.put(SqliteModelStore.KEY_MODEL_ID, "deepseek-chat");
        settings.put(SqliteModelStore.KEY_MODEL_NAME, "DeepSeek Chat");
        models.seedFromSettings(settings);
        assertEquals(1, models.list().size());
        assertEquals("DeepSeek Chat", models.selected().displayName());
        models.seedFromSettings(settings);
        assertEquals(1, models.list().size());
    }

    @Test public void seedsNothingWithoutEndpoint() {
        SqliteSettingsStore settings = new SqliteSettingsStore(database, Clock.fixed(1));
        settings.put(SqliteModelStore.KEY_MODEL_ID, "deepseek-chat");
        models.seedFromSettings(settings);
        assertTrue(models.list().isEmpty());
        assertNull(models.selected());
    }

    private static ModelConfig model(String id, String name, boolean isDefault) {
        return ModelConfig.builder()
                .identity(id, name)
                .endpoint("custom", id + "-model", "https://example.com/" + id)
                .secret("key")
                .context(128000, 4096)
                .flags(isDefault, true)
                .build();
    }
}
