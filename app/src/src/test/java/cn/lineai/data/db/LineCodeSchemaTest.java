package cn.lineai.data.db;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import org.junit.Test;

public final class LineCodeSchemaTest {
    @Test
    public void createSqlContainsCoreDataSystems() {
        String schema = String.join("\n", LineCodeSchema.CREATE_SQL);

        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS model_configs"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS conversations"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS messages"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS settings"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS projects"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS memories"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS working_memory"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS conversation_index"));
        assertTrue(schema.contains("CREATE TABLE IF NOT EXISTS import_jobs"));
        assertFalse(schema.contains("AsyncStorage"));
    }

    @Test
    public void optionalSearchUsesNativeAndroidFts4() {
        String fts = String.join("\n", LineCodeSchema.OPTIONAL_FTS_SQL).toLowerCase();

        assertTrue(fts.contains("using fts4"));
        assertTrue(fts.contains("memories_fts"));
        assertTrue(fts.contains("conversation_index_fts"));
        assertTrue(fts.contains("working_memory_fts"));
    }
}
