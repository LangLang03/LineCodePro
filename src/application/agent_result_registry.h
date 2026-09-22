#pragma once

#include <cstddef>
#include <cstdint>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include "application/tool_text_catalog.h"
#include "domain/agent_run_progress.h"

namespace linecode::application {

// cn.lineai.mvp.agent.AgentResultRecord.PREVIEW_MAX_CHARS (line 4).
inline constexpr std::size_t kAgentPreviewMaxChars = 240U;

// cn.lineai.model.tool.ToolResult.MAX_TOOL_RESULT_CHARS (line 15). The
// `agent_output` tool truncates the body it returns with the shared
// middle-truncation helper of the file tools.
inline constexpr std::size_t kAgentOutputMaxResultChars = 50U * 1024U;

// One stored sub-agent run, mirroring the legacy `AgentResultRecord`.
//
// The legacy constructor normalized every field (null -> "", empty status ->
// "running", negative tool call count -> 0, non-positive timestamp -> now).
// C++ has no constructor to hide that in an aggregate, so
// `CreateAgentResultRecord` is the normalizing entry point and the `With*`
// members keep the invariant; the registry also normalizes on write so a
// hand-built aggregate can never leak an un-normalized row.
struct AgentResultRecord final {
  std::string agent_id;
  std::string tool_call_id;
  std::string tool_name;
  std::string status{"running"};
  std::string type;
  std::string description;
  std::string preview;
  std::string full_output;
  std::string thinking;
  std::string progress_json;
  int tool_call_count{};
  bool error{};
  bool async{};
  int generation_id{};
  std::int64_t updated_at_ms{};

  // Legacy `AgentResultRecord.running(...)` (lines 56-82).
  [[nodiscard]] static AgentResultRecord
  Running(std::string agent_id, std::string tool_call_id, std::string tool_name,
          std::string type, std::string description, bool async,
          int generation_id);

  // Legacy `withPreview` (lines 84-88).
  [[nodiscard]] AgentResultRecord WithPreview(std::string next_preview) const;
  // Legacy `withStatus` (lines 90-94).
  [[nodiscard]] AgentResultRecord WithStatus(std::string next_status,
                                             bool next_error,
                                             std::string next_preview) const;
  // Legacy `withFullOutput` (lines 96-108): promotes the status to
  // "error"/"done" and re-derives the preview from the full output.
  [[nodiscard]] AgentResultRecord WithFullOutput(std::string next_full_output,
                                                 std::string next_thinking,
                                                 std::string next_progress_json,
                                                 int next_tool_call_count,
                                                 bool next_error) const;

  // Legacy `StoredAgentResult.isRunning()`: "running"/"pending"/
  // "waiting_unlock" are all still in flight.
  [[nodiscard]] bool IsRunning() const noexcept;

  bool operator==(const AgentResultRecord &) const = default;
};

// Wall clock used for `updatedAtMs` / the id sequence. Exposed so callers can
// reason about the same clock the registry stamps records with.
[[nodiscard]] std::int64_t AgentResultNowMillis() noexcept;

// Legacy `AgentResultRecord` constructor (lines 22-54): null-safe strings,
// defaulted status, clamped counters and timestamp.
[[nodiscard]] AgentResultRecord CreateAgentResultRecord(
    std::string agent_id, std::string tool_call_id, std::string tool_name,
    std::string status, std::string type, std::string description,
    std::string preview, std::string full_output, std::string thinking,
    std::string progress_json, int tool_call_count, bool error, bool async,
    int generation_id, std::int64_t updated_at_ms);

// Legacy `AgentResultRecord.clampPreview` (lines 203-212): trim, then cut to
// PREVIEW_MAX_CHARS characters.
[[nodiscard]] std::string ClampAgentPreview(std::string_view value);

// Legacy `AgentResultRecord.previewFrom` (lines 214-219).
[[nodiscard]] std::string AgentPreviewFrom(std::string_view full_output);

// The two `include` modes of the `agent_output` tool.
enum class AgentOutputInclude : std::uint8_t { output, meta };

// Legacy `AgentOutputTool.execute` result. `content` is either the meta/running
// JSON object or the fetched body; `error` mirrors the legacy `ToolResult`
// error flag.
struct AgentOutputResult final {
  std::string content;
  bool error{};

  bool operator==(const AgentOutputResult &) const = default;
};

// Read-only projection for presentation or other consumers. It exposes the
// typed nested snapshot without leaking registry storage or requiring callers
// to parse progress_json themselves.
struct AgentResultView final {
  std::string agent_id;
  std::string status;
  std::string type;
  std::string description;
  std::string preview;
  std::string full_output;
  std::string thinking;
  int tool_call_count{};
  bool error{};
  bool async{};
  domain::AgentProgressSnapshot progress{};

  bool operator==(const AgentResultView &) const = default;
};

// Cheap change token for one stored run.
//
// `Read()` copies the whole record (full output, thinking, decoded progress), so
// a renderer that asks for a card on every frame pays for it 60 times a second.
// The presentation layer memoizes on this token instead: it is a handful of
// scalars, it changes whenever the run is written, and a reader that cannot
// produce one simply opts out of memoization.
struct AgentResultVersion final {
  std::int64_t updated_at_ms{};
  std::size_t output_bytes{};
  std::size_t thinking_bytes{};
  int tool_call_count{};
  bool error{};
  std::uint64_t status_fingerprint{};

  bool operator==(const AgentResultVersion &) const = default;
};

class AgentResultReader {
public:
  virtual ~AgentResultReader() = default;

  [[nodiscard]] virtual std::optional<AgentResultView>
  Read(std::string_view agent_id) const = 0;

  // Optional: absent means "this reader cannot prove freshness", and callers
  // must not cache anything derived from it.
  [[nodiscard]] virtual std::optional<AgentResultVersion>
  ReadVersion(std::string_view agent_id) const {
    static_cast<void>(agent_id);
    return std::nullopt;
  }
};

// Legacy `AgentOutputTool` `include` option: "meta" selects the status-only
// object, anything else (including an absent value, which defaults to
// "output") selects the body.
[[nodiscard]] AgentOutputInclude
ParseAgentOutputInclude(std::string_view value) noexcept;

// Session-scoped store of sub-agent results, mirroring the legacy
// `AgentResultRegistry` (itself the `ToolContext.AgentResultStore`).
//
// The registry is the contract between the tool layer and the (separately
// implemented) execution engine: the engine records a run here, the `agent` /
// `agent_pipeline` tools return the compact ref produced by
// `ToCompactJson`, and `agent_output` reads it back through `Fetch`.
class AgentResultRegistry final : public AgentResultReader {
public:
  // Legacy `AgentResultRegistry.COMPACT_MARKER` (line 11).
  inline static constexpr std::string_view kCompactMarker =
      "linecode_agent_ref";

  AgentResultRegistry() = default;
  AgentResultRegistry(const AgentResultRegistry &) = delete;
  AgentResultRegistry &operator=(const AgentResultRegistry &) = delete;
  AgentResultRegistry(AgentResultRegistry &&) = delete;
  AgentResultRegistry &operator=(AgentResultRegistry &&) = delete;

  // Legacy `allocateId()` (lines 17-21): "ag_" + base36(now) + "_" +
  // base36(sequence), so ids are unique within a session and sort by creation
  // time in their leading component.
  [[nodiscard]] std::string AllocateId();

  // Legacy `put(...)`: an empty agent id is ignored.
  void Put(AgentResultRecord record);
  // Legacy `getRecord(...)`: null for an unknown or empty id.
  [[nodiscard]] std::optional<AgentResultRecord>
  GetRecord(std::string_view agent_id) const;
  [[nodiscard]] std::optional<AgentResultView>
  Read(std::string_view agent_id) const override;
  [[nodiscard]] std::optional<AgentResultVersion>
  ReadVersion(std::string_view agent_id) const override;
  [[nodiscard]] bool Contains(std::string_view agent_id) const;
  // Insertion order, matching the legacy LinkedHashMap iteration.
  [[nodiscard]] std::vector<std::string> AgentIds() const;
  [[nodiscard]] std::size_t Size() const;

  // Legacy `updateStatus(...)` / `updateFullOutput(...)`: unknown ids are
  // ignored instead of inserting a partial row.
  void UpdateStatus(std::string_view agent_id, std::string status, bool error,
                    std::string preview);
  void UpdateFullOutput(std::string_view agent_id, std::string full_output,
                        std::string thinking, std::string progress_json,
                        int tool_call_count, bool error = false);

  // Legacy `clearGeneration(...)` and the whole-store reset.
  void ClearGeneration(int generation_id);
  void Clear();

  // Legacy `AgentResultRegistry.toCompactJson` (lines 112-134): the compact
  // ref returned by `agent` / `agent_pipeline`. Carries the marker, the id and
  // the status fields, never the full transcript.
  [[nodiscard]] static std::string
  ToCompactJson(const AgentResultRecord &record);
  // Legacy `parseCompact` (lines 136-169): recognizes a compact ref; anything
  // without the marker or without an agent id yields nullopt.
  [[nodiscard]] static std::optional<AgentResultRecord>
  ParseCompact(std::string_view content);

  // Legacy `AgentOutputTool.metaJson` (lines 126-141).
  [[nodiscard]] static std::string MetaJson(const AgentResultRecord &record);
  // Legacy `AgentOutputTool.runningJson` (lines 143-157).
  [[nodiscard]] static std::string RunningJson(const AgentResultRecord &record,
                                               std::string_view message);

  // Legacy `AgentOutputTool.execute` (lines 89-124) from the empty-id check
  // onwards, except that the engine boundary is the registry itself and the
  // localized messages come from the application-owned catalog.
  [[nodiscard]] AgentOutputResult
  Fetch(std::string_view agent_id, std::string_view include,
        ToolTextLanguage language = ToolTextLanguage::english) const;

private:
  mutable std::mutex lock_;
  // Legacy LinkedHashMap: std::vector keeps the insertion order explicit and
  // the session-sized row count makes the linear lookup irrelevant.
  std::vector<AgentResultRecord> records_;
  std::uint64_t sequence_{1};
};

// Resolves the agent_id from a persisted compact tool result, then reads the
// typed snapshot through the read-only port. This is the presentation-facing
// bridge; compact refs stay small and backward compatible.
[[nodiscard]] std::optional<AgentResultView>
ResolveAgentResultReference(std::string_view compact_ref,
                            const AgentResultReader &reader);

} // namespace linecode::application
