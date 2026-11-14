package cn.lineai.engine.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Accumulates streamed tool-call fragments; the single implementation used by every codec.
 *
 * <p>Providers deliver one call as several fragments keyed by index: the identifier and name may
 * arrive once while arguments arrive as deltas. Fragments are merged by index, so a merger must
 * not be shared across concurrent streams.</p>
 */
public final class ToolCallMerger {

    /** Per-index accumulation buffer for one in-flight call. */
    private static final class Draft {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }

    private final Map<Integer, Draft> drafts = new LinkedHashMap<>();

    /**
     * Merges one fragment into the draft for {@code index}.
     *
     * @param index provider-assigned position of the call
     * @param id call identifier; ignored when null or empty
     * @param name tool name; ignored when null or empty
     * @param argumentsDelta arguments text appended to the accumulated JSON; ignored when null or
     *        empty
     */
    public void accept(int index, String id, String name, String argumentsDelta) {
        Draft draft = drafts.computeIfAbsent(index, key -> new Draft());
        if (id != null && !id.isEmpty()) draft.id = id;
        if (name != null && !name.isEmpty()) draft.name = name;
        if (argumentsDelta != null && !argumentsDelta.isEmpty()) draft.arguments.append(argumentsDelta);
    }

    /**
     * Returns the current drafts ordered by index, including incomplete ones.
     *
     * @return one draft per observed index, ordered by index
     */
    public List<ToolCallDraft> snapshot() {
        List<Integer> indexes = new ArrayList<>(drafts.keySet());
        java.util.Collections.sort(indexes);
        List<ToolCallDraft> result = new ArrayList<>();
        for (Integer index : indexes) {
            Draft draft = drafts.get(index);
            result.add(new ToolCallDraft(index, draft.id, draft.name, draft.arguments.toString()));
        }
        return result;
    }

    /**
     * Returns only drafts whose tool name is known, ordered by index.
     *
     * @return the subset of {@link #snapshot()} safe to execute as tool calls
     */
    public List<ToolCallDraft> completed() {
        List<ToolCallDraft> result = new ArrayList<>();
        for (ToolCallDraft draft : snapshot()) {
            if (draft.complete()) result.add(draft);
        }
        return result;
    }

    /** Returns {@code true} when at least one fragment has been accepted. */
    public boolean hasCalls() {
        return !drafts.isEmpty();
    }

    /** Discards all accumulated fragments. */
    public void clear() {
        drafts.clear();
    }
}
