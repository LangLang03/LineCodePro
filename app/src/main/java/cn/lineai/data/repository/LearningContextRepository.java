package cn.lineai.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LearningContextRepository extends BaseRepository implements LearningContextStore {
    private static final int SCAN_LIMIT = 120;
    private static final int OVERVIEW_LIMIT = 200;

    private final WorkspacePaths workspacePaths;
    private final ConversationIndexer conversationIndexer;
    private final MessageTextChunkStore textChunks;

    public LearningContextRepository(LineCodeDatabase database, WorkspacePaths workspacePaths, PromptTemplateRepository promptTemplateRepository) {
        super(database);
        this.workspacePaths = workspacePaths;
        this.conversationIndexer = new ConversationIndexer(database);
        this.textChunks = new MessageTextChunkStore(database);
    }

    @Override
    public synchronized void saveMemory(String id, String scope, String projectId, String content) {
        saveMemoryInternal(id, scope, projectId, content, "manual", 1.0);
    }

    @Override
    public synchronized void saveExtractedMemory(String scope, String projectId, String content, double confidence) {
        String normalizedScope = normalizeScope(scope);
        String existingId = findSimilarMemoryId(normalizedScope, projectId, content);
        saveMemoryInternal(existingId, normalizedScope, projectId, content, "auto", confidence);
    }

    private void saveMemoryInternal(String id, String scope, String projectId, String content, String source, double confidence) {
        String normalizedContent = safe(content).trim();
        if (normalizedContent.length() == 0) {
            return;
        }
        String normalizedScope = normalizeScope(scope);
        ExistingMemory existing = readExistingMemory(id);
        long now = System.currentTimeMillis();
        ContentValues values = new ContentValues();
        values.put("id", existing.id.length() == 0 ? "mem_" + UUID.randomUUID().toString().replace("-", "") : existing.id);
        values.put("scope", normalizedScope);
        if (MemoryOverviewState.Memory.SCOPE_USER.equals(normalizedScope)) {
            values.putNull("project_id");
        } else {
            values.put("project_id", safe(projectId));
        }
        values.put("content", normalizedContent);
        values.put("source", existing.source.length() == 0 ? safeSource(source) : existing.source);
        double resolvedConfidence = confidence <= 0 ? 1.0 : confidence;
        values.put("confidence", existing.confidence <= 0 ? resolvedConfidence : Math.max(existing.confidence, resolvedConfidence));
        values.put("created_at", existing.createdAt <= 0 ? now : existing.createdAt);
        values.put("updated_at", now);
        if (existing.lastUsedAt > 0) {
            values.put("last_used_at", existing.lastUsedAt);
        } else {
            values.putNull("last_used_at");
        }
        values.put("use_count", Math.max(0, existing.useCount));
        values.put("raw_json", "");
        database.getWritableDatabase().insertWithOnConflict("memories", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    @Override
    public synchronized void deleteMemory(String id) {
        String safeId = safe(id);
        if (safeId.length() == 0) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.delete("memories", "id = ?", new String[] {safeId});
        try {
            db.delete("memories_fts", "id = ?", new String[] {safeId});
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    public synchronized void deleteMemories(List<String> ids) {
        if (ids == null || ids.isEmpty()) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            for (String id : ids) {
                String safeId = safe(id);
                if (safeId.length() == 0) {
                    continue;
                }
                db.delete("memories", "id = ?", new String[] {safeId});
                try {
                    db.delete("memories_fts", "id = ?", new String[] {safeId});
                } catch (RuntimeException ignored) {
                }
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    @Override
    public synchronized void indexConversation(String projectId, ConversationRecord conversation) {
        conversationIndexer.indexConversation(projectId, conversation);
    }

    static List<String> extractKeywords(String input) {
        return TextTokenizer.extractKeywords(input);
    }

    static double relevanceScore(String query, String text) {
        return MemoryRanker.relevanceScore(query, text);
    }

    static double recencyBoost(long updatedAt, long now) {
        return MemoryRanker.recencyBoost(updatedAt, now);
    }

    static double rankingScore(String query, String text, long updatedAt, long now, double boost) {
        return MemoryRanker.rankingScore(query, text, updatedAt, now, boost);
    }

    public List<MemoryRanker.Candidate> readWorkingMemory(String projectId) {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        String now = String.valueOf(System.currentTimeMillis());
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT source, content, updated_at FROM working_memory "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), now, String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String source = value(cursor, "source");
                String content = compact(value(cursor, "content"), 420);
                items.add(new MemoryRanker.Candidate("", content + " " + source,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- [" + source + "] " + content));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryRanker.Candidate> readMemories(String projectId) {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, scope, project_id, source, confidence, content, updated_at FROM memories "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '' OR scope = 'user') "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String scope = value(cursor, "scope");
                String memoryProject = value(cursor, "project_id");
                String source = value(cursor, "source");
                String content = compact(value(cursor, "content"), 520);
                String scopeLabel = memoryProject.length() == 0 ? scope : scope + ":" + memoryProject;
                String confidence = String.format(Locale.ROOT, "%.2f", cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")));
                items.add(new MemoryRanker.Candidate(value(cursor, "id"), content + " " + source + " " + scopeLabel,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- [" + scopeLabel + "/" + source + ", confidence " + confidence + "] " + content));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryRanker.Candidate> readManualMemories(String projectId) {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, scope, project_id, source, confidence, content, updated_at FROM memories "
                        + "WHERE source = 'manual' AND (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '' OR scope = 'user') "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String scope = value(cursor, "scope");
                String memoryProject = value(cursor, "project_id");
                String source = value(cursor, "source");
                String content = compact(value(cursor, "content"), 520);
                String scopeLabel = memoryProject.length() == 0 ? scope : scope + ":" + memoryProject;
                String confidence = String.format(Locale.ROOT, "%.2f", cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")));
                items.add(new MemoryRanker.Candidate(value(cursor, "id"), content + " " + source + " " + scopeLabel,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- [" + scopeLabel + "/" + source + ", confidence " + confidence + "] " + content));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryRanker.Candidate> readConversationIndex(String projectId, String excludeConversationId) {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT conversation_id, role, substr(text, 1, 320) AS text, title, updated_at FROM conversation_index "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (? = '' OR conversation_id != ?) ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), safe(excludeConversationId), safe(excludeConversationId), String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String title = value(cursor, "title");
                String role = value(cursor, "role");
                String text = compact(value(cursor, "text"), 320);
                items.add(new MemoryRanker.Candidate("", title + " " + text,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- " + titleLabel(title, value(cursor, "conversation_id")) + " " + role + ": " + text));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryRanker.Candidate> readConversationMessages(String projectId, String excludeConversationId) {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT c.id AS conversation_id, c.title AS title, m.id AS message_id, m.role AS role, "
                        + "substr(m.content, 1, 320) AS text, m.timestamp AS updated_at "
                        + "FROM messages m JOIN conversations c ON c.id = m.conversation_id "
                        + "WHERE m.hidden = 0 AND m.exclude_from_context = 0 "
                        + "AND (m.content != '' OR EXISTS (SELECT 1 FROM message_text_chunks mtc WHERE mtc.message_id = m.id AND mtc.field_name = 'content' LIMIT 1)) "
                        + "AND (? = '' OR c.project_id = ? OR c.project_id IS NULL OR c.project_id = '') "
                        + "AND (? = '' OR c.id != ?) ORDER BY m.timestamp DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), safe(excludeConversationId), safe(excludeConversationId), String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String title = value(cursor, "title");
                String role = value(cursor, "role");
                String text = compact(readMessageContentPrefix(value(cursor, "message_id"), value(cursor, "text")), 320);
                items.add(new MemoryRanker.Candidate("", title + " " + text,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- " + titleLabel(title, value(cursor, "conversation_id")) + " " + role + ": " + text));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private String readMessageContentPrefix(String messageId, String legacyPrefix) {
        String text = textChunks.readFirstChars(database.getReadableDatabase(), messageId, "content", 320);
        return text.length() == 0 ? legacyPrefix : text;
    }

    public List<MemoryRanker.Candidate> readSkills() {
        ArrayList<MemoryRanker.Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT name, path, description, updated_at FROM skills WHERE enabled = 1 ORDER BY updated_at DESC LIMIT ?",
                new String[] {String.valueOf(SCAN_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                String name = value(cursor, "name");
                String path = value(cursor, "path");
                String description = value(cursor, "description");
                StringBuilder formatted = new StringBuilder();
                formatted.append("- ").append(name);
                if (description.length() > 0) {
                    formatted.append(" - ").append(description);
                }
                if (path.length() > 0) {
                    formatted.append("\n  - SKILL.md: ").append(new File(path, "SKILL.md").getAbsolutePath());
                    formatted.append("\n  - Root: ").append(path);
                }
                items.add(new MemoryRanker.Candidate("", name + " " + description + " " + path,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        formatted.toString()));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryOverviewState.Memory> readOverviewMemories(String scope, String projectId) {
        ArrayList<MemoryOverviewState.Memory> items = new ArrayList<>();
        String normalizedScope = normalizeScope(scope);
        Cursor cursor;
        if (MemoryOverviewState.Memory.SCOPE_USER.equals(normalizedScope)) {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, scope, project_id, content, source, confidence, created_at, updated_at, last_used_at, use_count "
                            + "FROM memories WHERE scope = 'user' ORDER BY updated_at DESC LIMIT ?",
                    new String[] {String.valueOf(OVERVIEW_LIMIT)});
        } else {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, scope, project_id, content, source, confidence, created_at, updated_at, last_used_at, use_count "
                            + "FROM memories WHERE scope = ? AND (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') ORDER BY updated_at DESC LIMIT ?",
                    new String[] {normalizedScope, safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)});
        }
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.Memory(
                        value(cursor, "id"), value(cursor, "scope"), value(cursor, "project_id"),
                        value(cursor, "content"), value(cursor, "source"), doubleValue(cursor, "confidence"),
                        longValue(cursor, "created_at"), longValue(cursor, "updated_at"),
                        longValue(cursor, "last_used_at"), intValue(cursor, "use_count")));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryOverviewState.WorkingMemory> readOverviewWorkingMemory(String projectId) {
        ArrayList<MemoryOverviewState.WorkingMemory> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, project_id, content, source, expires_at, created_at, updated_at FROM working_memory "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(System.currentTimeMillis()), String.valueOf(OVERVIEW_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.WorkingMemory(
                        value(cursor, "id"), value(cursor, "project_id"), value(cursor, "content"),
                        value(cursor, "source"), longValue(cursor, "expires_at"),
                        longValue(cursor, "created_at"), longValue(cursor, "updated_at")));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    public List<MemoryOverviewState.HistoryEntry> readOverviewHistory(String projectId) {
        ArrayList<MemoryOverviewState.HistoryEntry> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, project_id, conversation_id, message_id, role, substr(text, 1, 1000) AS text, title, created_at, updated_at "
                        + "FROM conversation_index WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)});
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.HistoryEntry(
                        value(cursor, "id"), value(cursor, "project_id"), value(cursor, "conversation_id"),
                        value(cursor, "message_id"), value(cursor, "role"), value(cursor, "text"),
                        value(cursor, "title"), longValue(cursor, "created_at"), longValue(cursor, "updated_at")));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private ExistingMemory readExistingMemory(String id) {
        String safeId = safe(id);
        if (safeId.length() == 0) {
            return new ExistingMemory();
        }
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, source, confidence, created_at, last_used_at, use_count FROM memories WHERE id = ? LIMIT 1",
                new String[] {safeId});
        try {
            if (!cursor.moveToFirst()) {
                return new ExistingMemory();
            }
            ExistingMemory memory = new ExistingMemory();
            memory.id = value(cursor, "id");
            memory.source = value(cursor, "source");
            memory.confidence = doubleValue(cursor, "confidence");
            memory.createdAt = longValue(cursor, "created_at");
            memory.lastUsedAt = longValue(cursor, "last_used_at");
            memory.useCount = intValue(cursor, "use_count");
            return memory;
        } finally {
            cursor.close();
        }
    }

    private String findSimilarMemoryId(String scope, String projectId, String content) {
        String target = normalizedMemoryKey(content);
        if (target.length() == 0) {
            return "";
        }
        Cursor cursor;
        if (MemoryOverviewState.Memory.SCOPE_USER.equals(normalizeScope(scope))) {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, content FROM memories WHERE scope = 'user' ORDER BY updated_at DESC LIMIT ?",
                    new String[] {String.valueOf(OVERVIEW_LIMIT)});
        } else {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, content FROM memories WHERE scope = ? AND (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') ORDER BY updated_at DESC LIMIT ?",
                    new String[] {normalizeScope(scope), safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)});
        }
        try {
            while (cursor.moveToNext()) {
                String existing = normalizedMemoryKey(value(cursor, "content"));
                if (existing.length() == 0) {
                    continue;
                }
                if (existing.equals(target) || existing.contains(target) || target.contains(existing)) {
                    return value(cursor, "id");
                }
            }
        } finally {
            cursor.close();
        }
        return "";
    }

    public void markMemoriesUsed(List<MemoryRanker.Candidate> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (MemoryRanker.Candidate memory : memories) {
                if (memory.id.length() == 0) {
                    continue;
                }
                db.execSQL("UPDATE memories SET last_used_at = ?, use_count = use_count + 1 WHERE id = ?",
                        new Object[] {now, memory.id});
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private String titleLabel(String title, String fallbackId) {
        String value = safe(title).trim();
        if (value.length() == 0) {
            value = safe(fallbackId).trim();
        }
        return value.length() == 0 ? "相关对话" : "「" + value + "」";
    }

    private String compact(String text, int maxChars) {
        String value = safe(text).replace('\n', ' ').replace('\r', ' ').trim();
        while (value.contains("  ")) {
            value = value.replace("  ", " ");
        }
        return value.length() <= maxChars ? value : value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String normalizeScope(String scope) {
        String value = safe(scope).trim().toLowerCase(Locale.ROOT);
        if (MemoryOverviewState.Memory.SCOPE_PROJECT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_PROJECT;
        }
        if (MemoryOverviewState.Memory.SCOPE_ENVIRONMENT.equals(value)) {
            return MemoryOverviewState.Memory.SCOPE_ENVIRONMENT;
        }
        return MemoryOverviewState.Memory.SCOPE_USER;
    }

    private String safeSource(String source) {
        String value = safe(source).trim().toLowerCase(Locale.ROOT);
        return "auto".equals(value) || "correction".equals(value) || "summary".equals(value) ? value : "manual";
    }

    private String normalizedMemoryKey(String content) {
        String value = safe(content).toLowerCase(Locale.ROOT);
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (Character.isLetterOrDigit(ch) || (ch >= '\u4e00' && ch <= '\u9fff')) {
                builder.append(ch);
            }
        }
        return builder.toString();
    }

    private static final class ExistingMemory {
        String id = "";
        String source = "";
        double confidence;
        long createdAt;
        long lastUsedAt;
        int useCount;
    }
}
