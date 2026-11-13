package cn.lineai.feature.chat.toolcall;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import cn.lineai.tool.api.ToolJson;
import cn.lineai.tool.api.ToolJsonArray;

/** Parsed pipeline request/result: stage rows plus the header summary counters. */
final class PipelineProgress {

    /** One pipeline stage merged from the request and the result payload. */
    static final class Stage {
        /** Stage id used for dependency edges. */
        final String id;
        /** Human description from the request. */
        final String description;
        /** Agent type token (explore, sub_coding, ...). */
        final String type;
        /** Ids of stages this one depends on. */
        final List<String> dependencies;
        /** Wire status; waiting when the result omits it. */
        final String status;
        /** True when the stage reported an error. */
        final boolean error;
        /** Reasoning text, if any. */
        final String thinking;
        /** Stage output text, if any. */
        final String output;
        /** Nested tool calls carried by the result. */
        final ToolJsonArray toolCalls;

        /** Null-safe constructor defaulting the status to waiting. */
        Stage(String id, String description, String type, List<String> dependencies,
              String status, boolean error, String thinking, String output,
              ToolJsonArray toolCalls) {
            this.id = id == null ? "" : id;
            this.description = description == null ? "" : description;
            this.type = type == null ? "" : type;
            this.dependencies = dependencies == null ? new ArrayList<>() : dependencies;
            this.status = status == null || status.isEmpty()
                    ? ToolRunStatus.WAITING.wire : status;
            this.error = error;
            this.thinking = thinking == null ? "" : thinking;
            this.output = output == null ? "" : output;
            this.toolCalls = toolCalls;
        }

        /** Display name: description, or the id when undescribed. */
        String name() {
            return description.isEmpty() ? id : description;
        }

        /** True while the stage status is running. */
        boolean running() {
            return ToolRunStatus.RUNNING.matches(status);
        }
    }

    /** Merged stage list in request order, with result-only stages appended. */
    final List<Stage> stages;
    /** Count of completed stages. */
    final int completed;
    /** Count of running stages. */
    final int running;
    /** Count of stages awaiting review. */
    final int pending;
    /** Count of failed stages. */
    final int failed;
    /** True when the whole pipeline finished successfully. */
    final boolean complete;
    /** True when the call failed or the payload reported an error. */
    final boolean error;
    /** Pipeline summary text. */
    final String output;

    /** Computes the header counters from the merged stages. */
    private PipelineProgress(List<Stage> stages, boolean complete, boolean error, String output) {
        this.stages = stages;
        int done = 0;
        int active = 0;
        int review = 0;
        int broken = 0;
        for (Stage stage : stages) {
            if (stage.error || ToolRunStatus.ERROR.matches(stage.status)) broken++;
            else if (ToolRunStatus.DONE.matches(stage.status)) done++;
            else if (ToolRunStatus.RUNNING.matches(stage.status)) active++;
            else if (ToolRunStatus.PENDING.matches(stage.status)) review++;
        }
        this.completed = done;
        this.running = active;
        this.pending = review;
        this.failed = broken;
        this.complete = complete;
        this.error = error;
        this.output = output == null ? "" : output;
    }

    /**
     * Merges the requested agents (arguments) with the reported ones (progress envelope) by id,
     * keeping the request order and appending result-only stages.
     */
    static PipelineProgress parse(ToolCallEntry entry) {
        ToolJson arguments = ToolCardSupport.arguments(entry);
        ToolJsonArray requested = arguments.array("agents");
        if (requested == null) requested = arguments.array("tasks");
        ToolJson progress = progressEnvelope(entry.result);
        boolean progressError = progress != null
                && ToolRunStatus.ERROR.matches(progress.text("status"));
        boolean error = entry.failed || progressError;
        boolean complete = progress != null
                ? ToolRunStatus.DONE.matches(progress.text("status"))
                : !entry.running && !ToolRunStatus.PENDING.matches(entry.reviewState);
        Map<String, Stage> resultById = resultStages(progress);
        List<Stage> stages = new ArrayList<>();
        if (requested != null) {
            for (int i = 0; i < requested.length(); i++) {
                ToolJson agent = requested.object(i);
                if (agent == null) continue;
                String id = agent.text("id", "stage-" + i);
                Stage parsed = resultById.remove(id);
                stages.add(new Stage(id, agent.text("description"),
                        agent.text("type"), dependencies(agent),
                        parsed == null ? ToolRunStatus.WAITING.wire : parsed.status,
                        parsed != null && parsed.error,
                        parsed == null ? "" : parsed.thinking,
                        parsed == null ? "" : parsed.output,
                        parsed == null ? null : parsed.toolCalls));
            }
        }
        stages.addAll(resultById.values());
        String output = progress != null ? progress.text("summary")
                : (entry.running ? "" : entry.result);
        return new PipelineProgress(stages, complete, error, output);
    }

    /** Result stages keyed by id for the merge. */
    private static Map<String, Stage> resultStages(ToolJson progress) {
        Map<String, Stage> values = new LinkedHashMap<>();
        if (progress == null) return values;
        ToolJsonArray array = progress.array("agents");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            ToolJson agent = array.object(i);
            if (agent == null) continue;
            String id = agent.text("id").trim();
            if (id.isEmpty()) id = "stage-" + i;
            String status = agent.text("status", ToolRunStatus.WAITING.wire);
            values.put(id, new Stage(id, agent.text("description"),
                    agent.text("type"), dependencies(agent), status,
                    agent.flag("error") || ToolRunStatus.ERROR.matches(status),
                    agent.text("thinking"), agent.text("output"),
                    agent.array("tool_calls")));
        }
        return values;
    }

    /** The result JSON only when it carries the pipeline-progress flag. */
    private static ToolJson progressEnvelope(String content) {
        ToolJson json = ToolCardSupport.resultJson(content);
        if (json == null) return null;
        return json.flag("linecode_agent_pipeline_progress") ? json : null;
    }

    /** Non-empty depends_on ids of one agent object. */
    private static List<String> dependencies(ToolJson agent) {
        List<String> values = new ArrayList<>();
        ToolJsonArray array = agent.array("depends_on");
        if (array == null) return values;
        for (int i = 0; i < array.length(); i++) {
            String value = array.text(i).trim();
            if (!value.isEmpty()) values.add(value);
        }
        return values;
    }
}
