package cn.lineai.data.repository;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import cn.lineai.ai.prompt.StringTemplate;
import cn.lineai.data.db.LineCodeDatabase;
import cn.lineai.model.ChatMessage;
import cn.lineai.model.MemoryOverviewState;
import cn.lineai.workspace.WorkspacePaths;
import java.io.File;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.UUID;

public final class LearningContextRepository {
    private static final int SCAN_LIMIT = 120;
    private static final int OVERVIEW_LIMIT = 200;
    private static final double RECENCY_WEIGHT = 0.15;
    private static final double WORKING_MEMORY_BOOST = 0.30;
    private static final double BM25_K = 1.2;
    private static final double BM25_B = 0.75;
    private static final String[] STOP_WORDS = new String[] {
            "the", "and", "for", "with", "this", "that", "from", "have", "into", "your", "you", "are", "how",
            "一个", "这个", "那个", "怎么", "如何", "什么", "以及", "或者", "可以", "需要", "进行", "使用"
    };

    private final Context context;
    private final LineCodeDatabase database;
    private final WorkspacePaths workspacePaths;
    private final PromptTemplateRepository promptTemplateRepository;

    public LearningContextRepository(Context context) {
        this.context = context.getApplicationContext();
        this.database = LineCodeDatabase.getInstance(this.context);
        this.workspacePaths = new WorkspacePaths(this.context);
        this.promptTemplateRepository = new PromptTemplateRepository(this.context);
    }

    public synchronized String buildLearningContext(String projectId, String userInput, String excludeConversationId) {
        List<Candidate> workingMemory = rank(readWorkingMemory(projectId), userInput, 5, true, WORKING_MEMORY_BOOST);
        List<Candidate> memories = rank(readMemories(projectId), userInput, 6, false);
        List<Candidate> history = rank(readConversationIndex(projectId, excludeConversationId), userInput, 6, false);
        if (history.isEmpty()) {
            history = rank(readConversationMessages(projectId, excludeConversationId), userInput, 6, false);
        }
        List<Candidate> skills = rank(readSkills(), userInput, 8, true);
        markMemoriesUsed(memories);

        HashMap<String, String> values = new HashMap<>();
        values.put("WORKING_MEMORY_SECTION", section("### 短期/工作记忆（当前项目 RAG Top-K）", workingMemory));
        values.put("MEMORY_SECTION", section("### 长期记忆（本地检索 Top-K）", memories));
        values.put("HISTORY_SECTION", section("### 相关聊天记录（当前项目本地检索 Top-K）", history));
        values.put("SKILL_PATHS_SECTION", skillPathsSection(projectId));
        values.put("SKILLS_SECTION", section("### 可用 Skills（RAG Top-K）", skills));
        values.put("PRIVATE_BOUNDARY_SECTION", privateBoundarySection());
        return template().render(values);
    }

    public synchronized MemoryOverviewState getOverview(String projectId) {
        String safeProjectId = safe(projectId);
        return new MemoryOverviewState(
                safeProjectId,
                readOverviewMemories(MemoryOverviewState.Memory.SCOPE_USER, safeProjectId),
                readOverviewMemories(MemoryOverviewState.Memory.SCOPE_PROJECT, safeProjectId),
                readOverviewMemories(MemoryOverviewState.Memory.SCOPE_ENVIRONMENT, safeProjectId),
                readOverviewWorkingMemory(safeProjectId),
                readOverviewHistory(safeProjectId)
        );
    }

    public synchronized void saveMemory(String id, String scope, String projectId, String content) {
        saveMemoryInternal(id, scope, projectId, content, "manual", 1.0);
    }

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

    public synchronized void indexConversation(String projectId, ConversationRecord conversation) {
        if (conversation == null || conversation.getId().length() == 0) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            db.delete("conversation_index", "conversation_id = ?", new String[] {conversation.getId()});
            for (MessageRecord message : conversation.getMessages()) {
                if (!shouldIndex(message)) {
                    continue;
                }
                ContentValues values = new ContentValues();
                values.put("id", conversation.getId() + ":" + message.getId());
                values.put("project_id", safe(projectId));
                values.put("conversation_id", conversation.getId());
                values.put("message_id", message.getId());
                values.put("role", message.getRole().getProtocolName());
                values.put("text", message.getContent());
                values.put("title", conversation.getTitle());
                values.put("created_at", message.getTimestamp() > 0 ? message.getTimestamp() : conversation.getCreatedAt());
                values.put("updated_at", conversation.getUpdatedAt());
                values.put("raw_json", message.getRawJson());
                db.insertWithOnConflict("conversation_index", null, values, SQLiteDatabase.CONFLICT_REPLACE);
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private boolean shouldIndex(MessageRecord message) {
        if (message == null || message.isHidden() || message.isExcludeFromContext()) {
            return false;
        }
        ChatMessage.Role role = message.getRole();
        return (role == ChatMessage.Role.USER || role == ChatMessage.Role.ASSISTANT)
                && message.getContent().trim().length() > 0;
    }

    private List<Candidate> readWorkingMemory(String projectId) {
        ArrayList<Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT source, content, updated_at FROM working_memory "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(System.currentTimeMillis()), String.valueOf(SCAN_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                String source = value(cursor, "source");
                String content = compact(value(cursor, "content"), 420);
                items.add(new Candidate("", content + " " + source, cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- [" + source + "] " + content));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<Candidate> readMemories(String projectId) {
        ArrayList<Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, scope, project_id, source, confidence, content, updated_at FROM memories "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '' OR scope = 'user') "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(SCAN_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                String scope = value(cursor, "scope");
                String memoryProject = value(cursor, "project_id");
                String source = value(cursor, "source");
                String content = compact(value(cursor, "content"), 520);
                String scopeLabel = memoryProject.length() == 0 ? scope : scope + ":" + memoryProject;
                String confidence = String.format(Locale.ROOT, "%.2f", cursor.getDouble(cursor.getColumnIndexOrThrow("confidence")));
                items.add(new Candidate(value(cursor, "id"), content + " " + source + " " + scopeLabel,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- [" + scopeLabel + "/" + source + ", confidence " + confidence + "] " + content));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<Candidate> readConversationIndex(String projectId, String excludeConversationId) {
        ArrayList<Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT conversation_id, role, text, title, updated_at FROM conversation_index "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (? = '' OR conversation_id != ?) "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), safe(excludeConversationId), safe(excludeConversationId), String.valueOf(SCAN_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                String title = value(cursor, "title");
                String role = value(cursor, "role");
                String text = compact(value(cursor, "text"), 320);
                items.add(new Candidate("", title + " " + text,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- " + titleLabel(title, value(cursor, "conversation_id")) + " " + role + ": " + text));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<Candidate> readConversationMessages(String projectId, String excludeConversationId) {
        ArrayList<Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT c.id AS conversation_id, c.title AS title, m.role AS role, m.content AS text, m.timestamp AS updated_at "
                        + "FROM messages m JOIN conversations c ON c.id = m.conversation_id "
                        + "WHERE m.hidden = 0 AND m.exclude_from_context = 0 AND m.content != '' "
                        + "AND (? = '' OR c.project_id = ? OR c.project_id IS NULL OR c.project_id = '') "
                        + "AND (? = '' OR c.id != ?) "
                        + "ORDER BY m.timestamp DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), safe(excludeConversationId), safe(excludeConversationId), String.valueOf(SCAN_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                String title = value(cursor, "title");
                String role = value(cursor, "role");
                String text = compact(value(cursor, "text"), 320);
                items.add(new Candidate("", title + " " + text,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        "- " + titleLabel(title, value(cursor, "conversation_id")) + " " + role + ": " + text));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<Candidate> readSkills() {
        ArrayList<Candidate> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT name, path, description, updated_at FROM skills WHERE enabled = 1 ORDER BY updated_at DESC LIMIT ?",
                new String[] {String.valueOf(SCAN_LIMIT)}
        );
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
                items.add(new Candidate("", name + " " + description + " " + path,
                        cursor.getLong(cursor.getColumnIndexOrThrow("updated_at")),
                        formatted.toString()));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<MemoryOverviewState.Memory> readOverviewMemories(String scope, String projectId) {
        ArrayList<MemoryOverviewState.Memory> items = new ArrayList<>();
        String normalizedScope = normalizeScope(scope);
        Cursor cursor;
        if (MemoryOverviewState.Memory.SCOPE_USER.equals(normalizedScope)) {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, scope, project_id, content, source, confidence, created_at, updated_at, last_used_at, use_count "
                            + "FROM memories WHERE scope = 'user' ORDER BY updated_at DESC LIMIT ?",
                    new String[] {String.valueOf(OVERVIEW_LIMIT)}
            );
        } else {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, scope, project_id, content, source, confidence, created_at, updated_at, last_used_at, use_count "
                            + "FROM memories WHERE scope = ? "
                            + "AND (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                            + "ORDER BY updated_at DESC LIMIT ?",
                    new String[] {normalizedScope, safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)}
            );
        }
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.Memory(
                        value(cursor, "id"),
                        value(cursor, "scope"),
                        value(cursor, "project_id"),
                        value(cursor, "content"),
                        value(cursor, "source"),
                        doubleValue(cursor, "confidence"),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at"),
                        longValue(cursor, "last_used_at"),
                        intValue(cursor, "use_count")
                ));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<MemoryOverviewState.WorkingMemory> readOverviewWorkingMemory(String projectId) {
        ArrayList<MemoryOverviewState.WorkingMemory> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, project_id, content, source, expires_at, created_at, updated_at FROM working_memory "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "AND (expires_at IS NULL OR expires_at = 0 OR expires_at > ?) "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(System.currentTimeMillis()), String.valueOf(OVERVIEW_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.WorkingMemory(
                        value(cursor, "id"),
                        value(cursor, "project_id"),
                        value(cursor, "content"),
                        value(cursor, "source"),
                        longValue(cursor, "expires_at"),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at")
                ));
            }
        } finally {
            cursor.close();
        }
        return items;
    }

    private List<MemoryOverviewState.HistoryEntry> readOverviewHistory(String projectId) {
        ArrayList<MemoryOverviewState.HistoryEntry> items = new ArrayList<>();
        Cursor cursor = database.getReadableDatabase().rawQuery(
                "SELECT id, project_id, conversation_id, message_id, role, text, title, created_at, updated_at "
                        + "FROM conversation_index "
                        + "WHERE (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                        + "ORDER BY updated_at DESC LIMIT ?",
                new String[] {safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)}
        );
        try {
            while (cursor.moveToNext()) {
                items.add(new MemoryOverviewState.HistoryEntry(
                        value(cursor, "id"),
                        value(cursor, "project_id"),
                        value(cursor, "conversation_id"),
                        value(cursor, "message_id"),
                        value(cursor, "role"),
                        value(cursor, "text"),
                        value(cursor, "title"),
                        longValue(cursor, "created_at"),
                        longValue(cursor, "updated_at")
                ));
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
                new String[] {safeId}
        );
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
                    new String[] {String.valueOf(OVERVIEW_LIMIT)}
            );
        } else {
            cursor = database.getReadableDatabase().rawQuery(
                    "SELECT id, content FROM memories WHERE scope = ? "
                            + "AND (? = '' OR project_id = ? OR project_id IS NULL OR project_id = '') "
                            + "ORDER BY updated_at DESC LIMIT ?",
                    new String[] {normalizeScope(scope), safe(projectId), safe(projectId), String.valueOf(OVERVIEW_LIMIT)}
            );
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

    private void markMemoriesUsed(List<Candidate> memories) {
        if (memories == null || memories.isEmpty()) {
            return;
        }
        SQLiteDatabase db = database.getWritableDatabase();
        db.beginTransaction();
        try {
            long now = System.currentTimeMillis();
            for (Candidate memory : memories) {
                if (memory.id.length() == 0) {
                    continue;
                }
                db.execSQL(
                        "UPDATE memories SET last_used_at = ?, use_count = use_count + 1 WHERE id = ?",
                        new Object[] {now, memory.id}
                );
            }
            db.setTransactionSuccessful();
        } finally {
            db.endTransaction();
        }
    }

    private List<Candidate> rank(List<Candidate> candidates, String userInput, int limit, boolean allowRecentFallback) {
        return rank(candidates, userInput, limit, allowRecentFallback, 0.0);
    }

    private List<Candidate> rank(List<Candidate> candidates, String userInput, int limit, boolean allowRecentFallback, double boost) {
        long now = System.currentTimeMillis();
        boolean hasMatches = false;
        for (Candidate candidate : candidates) {
            candidate.relevanceScore = relevanceScore(userInput, candidate.searchText);
            candidate.score = rankingScore(userInput, candidate.searchText, candidate.updatedAt, now, boost);
            hasMatches = hasMatches || candidate.relevanceScore > 0;
        }
        if (!hasMatches && !allowRecentFallback) {
            return Collections.emptyList();
        }
        Collections.sort(candidates, new Comparator<Candidate>() {
            @Override
            public int compare(Candidate left, Candidate right) {
                int scoreCompare = Double.compare(right.score, left.score);
                if (scoreCompare != 0) {
                    return scoreCompare;
                }
                return Long.compare(right.updatedAt, left.updatedAt);
            }
        });
        ArrayList<Candidate> selected = new ArrayList<>();
        for (Candidate candidate : candidates) {
            if (hasMatches && candidate.relevanceScore <= 0) {
                continue;
            }
            if (!hasMatches && candidate.score <= 0) {
                continue;
            }
            selected.add(candidate);
            if (selected.size() >= limit) {
                break;
            }
        }
        return selected;
    }

    static double rankingScore(String query, String text, long updatedAt, long now, double boost) {
        return relevanceScore(query, text) + recencyBoost(updatedAt, now) * RECENCY_WEIGHT + Math.max(0.0, boost);
    }

    static double relevanceScore(String query, String text) {
        List<String> queryKeywords = extractKeywords(query);
        List<String> docKeywords = extractKeywords(text);
        if (queryKeywords.isEmpty() || docKeywords.isEmpty()) {
            return 0.0;
        }
        HashMap<String, Integer> queryTerms = termFrequency(queryKeywords);
        HashMap<String, Integer> docTerms = termFrequency(docKeywords);
        int docLength = Math.max(1, docKeywords.size());
        String rawText = safeStatic(text).toLowerCase(Locale.ROOT);
        double score = 0.0;
        for (String term : queryTerms.keySet()) {
            int docTf = docTerms.containsKey(term) ? docTerms.get(term) : rawText.contains(term) ? 1 : 0;
            if (docTf <= 0) {
                continue;
            }
            int queryTf = queryTerms.get(term);
            double tf = docTf / (docTf + BM25_K + BM25_B * docLength / 100.0);
            score += (1.0 + Math.log(queryTf)) * tf;
        }
        String queryPhrase = safeStatic(query).trim().toLowerCase(Locale.ROOT);
        if (queryPhrase.length() >= 4 && rawText.contains(queryPhrase)) {
            score += 2.0;
        }
        return score;
    }

    static double recencyBoost(long updatedAt, long now) {
        if (updatedAt <= 0 || now <= 0) {
            return 0.0;
        }
        double ageDays = Math.max(0.0, (now - updatedAt) / 86400000.0);
        return 1.0 / (1.0 + ageDays / 30.0);
    }

    static List<String> extractKeywords(String input) {
        ArrayList<String> keywords = new ArrayList<>();
        String value = safeStatic(input).toLowerCase(Locale.ROOT);
        StringBuilder latin = new StringBuilder();
        StringBuilder cjk = new StringBuilder();
        for (int i = 0; i < value.length(); i++) {
            char ch = value.charAt(i);
            if (isLatinTokenChar(ch)) {
                latin.append(ch);
            } else {
                addKeyword(keywords, latin.toString());
                latin.setLength(0);
            }
            if (isCjk(ch)) {
                cjk.append(ch);
            } else {
                addCjkKeywords(keywords, cjk.toString());
                cjk.setLength(0);
            }
        }
        addKeyword(keywords, latin.toString());
        addCjkKeywords(keywords, cjk.toString());
        return keywords;
    }

    private static HashMap<String, Integer> termFrequency(List<String> keywords) {
        HashMap<String, Integer> counts = new HashMap<>();
        for (String keyword : keywords) {
            Integer count = counts.get(keyword);
            counts.put(keyword, count == null ? 1 : count + 1);
        }
        return counts;
    }

    private static boolean isLatinTokenChar(char ch) {
        return (ch >= 'a' && ch <= 'z')
                || (ch >= '0' && ch <= '9')
                || ch == '_'
                || ch == '#'
                || ch == '.'
                || ch == '-';
    }

    private static boolean isCjk(char ch) {
        return ch >= '\u4e00' && ch <= '\u9fff';
    }

    private static void addCjkKeywords(ArrayList<String> keywords, String chunk) {
        String value = safeStatic(chunk).trim();
        if (value.length() < 2) {
            return;
        }
        if (value.length() <= 4) {
            addKeyword(keywords, value);
            return;
        }
        for (int i = 0; i < value.length() - 1; i++) {
            addKeyword(keywords, value.substring(i, i + 2));
        }
    }

    private static void addKeyword(ArrayList<String> keywords, String term) {
        String value = trimToken(safeStatic(term).trim());
        if (value.length() < 2) {
            return;
        }
        if (value.length() > 32) {
            value = value.substring(0, 32);
        }
        if (!isStopWord(value)) {
            keywords.add(value);
        }
    }

    private static String trimToken(String value) {
        int start = 0;
        int end = value.length();
        while (start < end && isTokenTrimChar(value.charAt(start))) {
            start++;
        }
        while (end > start && isTokenTrimChar(value.charAt(end - 1))) {
            end--;
        }
        return value.substring(start, end);
    }

    private static boolean isTokenTrimChar(char ch) {
        return ch == '.' || ch == '-' || ch == '_' || ch == '#';
    }

    private static boolean isStopWord(String term) {
        for (String stopWord : STOP_WORDS) {
            if (stopWord.equals(term)) {
                return true;
            }
        }
        return false;
    }

    private String section(String title, List<Candidate> rows) {
        if (rows.isEmpty()) {
            return "";
        }
        StringBuilder builder = new StringBuilder(title);
        for (Candidate row : rows) {
            builder.append('\n').append(row.formatted);
        }
        return builder.toString();
    }

    private String skillPathsSection(String projectId) {
        StringBuilder builder = new StringBuilder("### Skills 路径");
        builder.append('\n').append("- app: ").append(workspacePaths.getSkillsRoot().getAbsolutePath());
        if (safe(projectId).length() > 0) {
            builder.append('\n').append("- project: ").append(new File(projectId, ".linecode/skills").getAbsolutePath());
        }
        builder.append('\n').append("- ssh: ~/.linecode/skills");
        builder.append('\n').append("需要完整 Skill 指南时，可使用文件读取/搜索/列目录工具访问对应 SKILL.md；Skills 的创建和安装由扩展系统处理，文件工具只维护已授权目录里的 SKILL.md。");
        return builder.toString();
    }

    private String privateBoundarySection() {
        String skillsRoot = workspacePaths.getSkillsRoot().getAbsolutePath();
        return "### 私有目录边界\n"
                + "文件工具可使用系统明确注入的 Skills 路径（例如 " + skillsRoot + " 和当前项目 .linecode/skills）；不要猜测或访问其他应用私有目录。";
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
        if (value.length() <= maxChars) {
            return value;
        }
        return value.substring(0, Math.max(0, maxChars - 3)) + "...";
    }

    private String value(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? "" : cursor.getString(index);
    }

    private long longValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0L : cursor.getLong(index);
    }

    private int intValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0 : cursor.getInt(index);
    }

    private double doubleValue(Cursor cursor, String column) {
        int index = cursor.getColumnIndexOrThrow(column);
        return cursor.isNull(index) ? 0.0 : cursor.getDouble(index);
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
        if ("auto".equals(value) || "correction".equals(value) || "summary".equals(value)) {
            return value;
        }
        return "manual";
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

    private String safe(String value) {
        return value == null ? "" : value;
    }

    private static String safeStatic(String value) {
        return value == null ? "" : value;
    }

    private StringTemplate template() {
        return new StringTemplate(promptTemplateRepository.getTemplateText(PromptTemplateRepository.ID_LEARNING_CONTEXT));
    }

    private static final class Candidate {
        final String id;
        final String searchText;
        final long updatedAt;
        final String formatted;
        double relevanceScore;
        double score;

        Candidate(String id, String searchText, long updatedAt, String formatted) {
            this.id = id == null ? "" : id;
            this.searchText = searchText == null ? "" : searchText;
            this.updatedAt = updatedAt;
            this.formatted = formatted == null ? "" : formatted;
        }
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
