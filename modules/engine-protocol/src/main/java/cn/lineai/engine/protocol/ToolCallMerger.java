package cn.lineai.engine.protocol;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Accumulates streamed tool-call fragments; the single implementation used by every codec. */
public final class ToolCallMerger {

    private static final class Draft {
        String id = "";
        String name = "";
        final StringBuilder arguments = new StringBuilder();
    }

    private final Map<Integer, Draft> drafts = new LinkedHashMap<>();

    public void accept(int index, String id, String name, String argumentsDelta) {
        Draft draft = drafts.computeIfAbsent(index, key -> new Draft());
        if (id != null && !id.isEmpty()) draft.id = id;
        if (name != null && !name.isEmpty()) draft.name = name;
        if (argumentsDelta != null && !argumentsDelta.isEmpty()) draft.arguments.append(argumentsDelta);
    }

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

    public List<ToolCallDraft> completed() {
        List<ToolCallDraft> result = new ArrayList<>();
        for (ToolCallDraft draft : snapshot()) {
            if (draft.complete()) result.add(draft);
        }
        return result;
    }

    public boolean hasCalls() {
        return !drafts.isEmpty();
    }

    public void clear() {
        drafts.clear();
    }
}
