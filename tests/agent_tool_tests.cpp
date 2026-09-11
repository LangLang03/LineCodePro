// Contract tests for the agent tool group: declarations, argument validation,
// pipeline dependency parsing and the agent result registry.

#include <algorithm>
#include <cassert>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <iostream>
#include <memory>
#include <optional>
#include <ranges>
#include <stdexcept>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/agent_result_registry.h"
#include "application/agent_tool_registry.h"
#include "application/mcp_execution_settings.h"
#include "application/tool_text_catalog.h"
#include "domain/agent_pipeline.h"
#include "infrastructure/archive_json.h"

namespace {

using namespace linecode;

namespace json = infrastructure::archive_json;

// Verbatim legacy getDescription() strings (AgentTool.java:29-31,
// AgentPipelineTool.java:29, AgentOutputTool.java:25-28).
constexpr std::string_view kLegacyAgentDescription =
    "Dispatch a sub-Agent to handle a task. explore is read-only; sub-coding must have a clear and unique write scope. "
    "Returns a compact ref with agent_id (not full transcript). Use agent_output(agent_id) to fetch full output when needed. "
    "Optional async=true returns immediately for explore agents.";

constexpr std::string_view kLegacyPipelineDescription =
    "Create a pipeline of Agent tasks with dependencies. sub-coding must declare a unique write_scope; multiple Agents cannot write the same file or overlapping directories.";

constexpr std::string_view kLegacyAgentOutputDescription =
    "Fetch a previously started agent result by agent_id. "
    "The agent / agent_pipeline tools return a compact ref with agent_id; "
    "call this tool when you need the full output (or while async agents are still running). "
    "Do not invent agent_id values — only use ids returned by agent tools.";

// Verbatim legacy getParameters() output, re-serialized in the canonical
// sorted-key form of infrastructure::archive_json.
constexpr std::string_view kLegacyAgentSchema =
    R"({"properties":{"async":{"description":"If true, return immediately with agent_id (explore only; default false). Fetch full output later via agent_output.","type":"boolean"},"description":{"description":"Task title of 3-8 words","type":"string"},"prompt":{"description":"Detailed task assigned to the Agent, including scope, constraints, and acceptance criteria. Must state that unauthorized files must not be modified; if out-of-scope files must be modified, stop and report.","type":"string"},"read_scope":{"description":"List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task","items":{"type":"string"},"type":"array"},"type":{"description":"Agent type: explore for read-only exploration, sub-coding for programming subtasks","enum":["explore","sub-coding"],"type":"string"},"write_scope":{"description":"Unique list of files or directories sub-coding is allowed to write; explore must be empty. Do not assign the same file to multiple Agents","items":{"type":"string"},"type":"array"}},"required":["type","description","prompt"],"type":"object"})";

constexpr std::string_view kLegacyPipelineSchema =
    R"({"properties":{"agents":{"description":"List of Agent tasks","items":{"properties":{"depends_on":{"description":"List of dependent Agent IDs","items":{"type":"string"},"type":"array"},"description":{"description":"Short task title","type":"string"},"id":{"description":"Unique identifier within the pipeline","type":"string"},"prompt":{"description":"Detailed task description. Must state the task boundaries, acceptance criteria, and that files outside write_scope must not be modified","type":"string"},"read_scope":{"description":"List of files or directories allowed to read. When empty, still read only the minimum scope needed to complete the task","items":{"type":"string"},"type":"array"},"type":{"description":"Agent type","enum":["explore","sub-coding"],"type":"string"},"write_scope":{"description":"Unique list of files or directories sub-coding is allowed to write; explore must be empty. Multiple Agents' write_scope must not be identical, nor contain or be contained by one another","items":{"type":"string"},"type":"array"}},"required":["id","type","description","prompt"],"type":"object"},"type":"array"}},"required":["agents"],"type":"object"})";

constexpr std::string_view kLegacyAgentOutputSchema =
    R"({"properties":{"agent_id":{"description":"agent_id from a prior agent / agent_pipeline tool result","type":"string"},"include":{"description":"output (default): full body when done; meta: status fields only","enum":["output","meta"],"type":"string"}},"required":["agent_id"],"type":"object"})";

// feature-tool/src/main/res/values/strings.xml, English source strings.
constexpr std::string_view kInvalidType =
    "Agent type must be explore or sub-coding.";
constexpr std::string_view kExploreNoWrite =
    "explore Agent cannot declare write_scope and cannot write files.";
constexpr std::string_view kDescriptionEmpty =
    "Agent description cannot be empty.";
constexpr std::string_view kPromptEmpty = "Agent prompt cannot be empty.";
constexpr std::string_view kRunnerUnavailable =
    "Agent runner not available, cannot run sub-Agent.";
constexpr std::string_view kAgentsEmpty =
    "agent_pipeline.agents cannot be empty.";
constexpr std::string_view kIdMissing = "agent_id is required.";
constexpr std::string_view kStoreMissing =
    "Agent result store is not available.";
constexpr std::string_view kNotFound = "Unknown agent_id: ghost";
constexpr std::string_view kStillRunning =
    "Agent is still running. Poll again later or wait for completion.";
constexpr std::string_view kOutputFailed = "Agent finished with error.";
constexpr std::string_view kOutputEmpty = "Agent finished with empty output.";
constexpr std::string_view kPipelineInvalidType =
    "agents[0].type must be explore or sub-coding.";
constexpr std::string_view kPipelineRunnerUnavailable =
    "Agent pipeline runner not available, cannot run.";

// ---------------------------------------------------------------------------
// Fakes
// ---------------------------------------------------------------------------

class FakeMcpSettings final : public application::McpExecutionSettingsService {
public:
  huxerui::Task<application::SettingsResult<domain::McpExecutionSettings>>
  Load() override {
    if (fail_load) {
      co_return std::unexpected(application::SettingsStoreError{
          .message = "injected execution-mode settings failure"});
    }
    co_return value;
  }

  huxerui::Task<application::SettingsResult<void>>
  SetMode(domain::McpExecutionMode mode) override {
    value.mode = mode;
    co_return application::SettingsResult<void>{};
  }

  huxerui::Task<application::SettingsResult<void>>
  SetToolGroupEnabled(domain::McpExecutionMode, std::string, bool) override {
    co_return application::SettingsResult<void>{};
  }

  domain::McpExecutionSettings value{domain::DefaultMcpExecutionSettings()};
  bool fail_load{};
};

class FakeAgentRunner final : public application::AgentRunner {
public:
  huxerui::Task<application::AgentRunResult>
  RunAgent(application::AgentRunRequest request) override {
    ++agent_runs;
    last_request = std::move(request);
    co_return application::AgentRunResult{
        .output = agent_output,
        .tool_call_count = 2,
        .error = agent_error,
    };
  }

  huxerui::Task<application::AgentRunResult>
  RunAgentPipeline(application::AgentPipelineRunRequest request) override {
    ++pipeline_runs;
    last_pipeline = std::move(request);
    co_return application::AgentRunResult{
        .output = pipeline_output,
        .tool_call_count = 7,
        .error = pipeline_error,
    };
  }

  std::string agent_output{"compact-agent-ref"};
  std::string pipeline_output{"compact-pipeline-ref"};
  bool agent_error{};
  bool pipeline_error{};
  int agent_runs{};
  int pipeline_runs{};
  std::optional<application::AgentRunRequest> last_request;
  std::optional<application::AgentPipelineRunRequest> last_pipeline;
};

struct Scenario final {
  std::shared_ptr<FakeMcpSettings> mcp{std::make_shared<FakeMcpSettings>()};
  std::shared_ptr<application::AgentResultRegistry> results{
      std::make_shared<application::AgentResultRegistry>()};
  std::shared_ptr<FakeAgentRunner> runner{std::make_shared<FakeAgentRunner>()};
  std::shared_ptr<application::AgentToolRegistry> tools{
      std::make_shared<application::AgentToolRegistry>(mcp, results, runner)};
  bool done{};
};

std::shared_ptr<Scenario> active;

domain::McpToolGroupState &
Group(const std::shared_ptr<FakeMcpSettings> &settings, std::string_view id) {
  const auto found = std::ranges::find(settings->value.groups, id,
                                       &domain::McpToolGroupState::id);
  assert(found != settings->value.groups.end());
  return *found;
}

// ---------------------------------------------------------------------------
// JSON helpers
// ---------------------------------------------------------------------------

const json::Object *ObjectAt(const json::Object *object,
                             std::string_view key) {
  return object == nullptr ? nullptr : json::AsObject(json::Find(*object, key));
}

std::string StringAt(const json::Object *object, std::string_view key) {
  if (object == nullptr)
    return {};
  const auto *value = json::AsString(json::Find(*object, key));
  return value == nullptr ? std::string{} : *value;
}

std::string StringAt(const json::Value *value) {
  const auto *text = json::AsString(value);
  return text == nullptr ? std::string{} : *text;
}

bool BoolAt(const json::Object *object, std::string_view key) {
  if (object == nullptr)
    return false;
  const auto *value = json::Find(*object, key);
  const auto *flag = value == nullptr ? nullptr : std::get_if<bool>(value);
  return flag != nullptr && *flag;
}

json::Object MustParseObject(std::string_view text) {
  auto parsed = json::Parse(text);
  assert(parsed);
  const auto *object = json::AsObject(&*parsed);
  assert(object != nullptr);
  return *object;
}

std::vector<std::string> RequiredNames(const json::Object &schema) {
  std::vector<std::string> names;
  const auto *required = json::AsArray(json::Find(schema, "required"));
  assert(required != nullptr);
  for (const auto &value : *required)
    names.push_back(StringAt(&value));
  return names;
}

std::size_t SchemaPropertyCount(const json::Object &schema) {
  const auto *properties = ObjectAt(&schema, "properties");
  assert(properties != nullptr);
  return properties->size();
}

// ---------------------------------------------------------------------------
// Pure functions
// ---------------------------------------------------------------------------

void NormalizeTypeChecks() {
  using application::NormalizeAgentType;
  // The three legacy aliases map onto "sub-coding" (AgentTool.java:119-121).
  assert(NormalizeAgentType("sub_coding") == "sub-coding");
  assert(NormalizeAgentType("subcoding") == "sub-coding");
  assert(NormalizeAgentType("coding") == "sub-coding");
  // Trim + Locale.US lowering first, then the value passes through unchanged.
  assert(NormalizeAgentType(" SUB_CODING ") == "sub-coding");
  assert(NormalizeAgentType("Explore") == "explore");
  assert(NormalizeAgentType("sub-coding") == "sub-coding");
  assert(NormalizeAgentType("weird") == "weird");
  assert(NormalizeAgentType("") == "");
}

void NormalizeScopeChecks() {
  using application::NormalizeAgentScope;
  // 1. backslashes become forward slashes.
  assert(NormalizeAgentScope("src\\main\\a.cpp") == "src/main/a.cpp");
  // 2. every leading "./" is stripped.
  assert(NormalizeAgentScope("./src/a.cpp") == "src/a.cpp");
  assert(NormalizeAgentScope("././src") == "src");
  // 3. "//" is collapsed.
  assert(NormalizeAgentScope("src//main//a.cpp") == "src/main/a.cpp");
  assert(NormalizeAgentScope("a///b") == "a/b");
  // 4. a trailing slash is dropped unless the value is a bare "/".
  assert(NormalizeAgentScope("src/main/") == "src/main");
  assert(NormalizeAgentScope("/") == "/");
  assert(NormalizeAgentScope("//") == "/");
  // Surrounding whitespace is trimmed first; a blank scope stays blank.
  assert(NormalizeAgentScope("  src  ") == "src");
  assert(NormalizeAgentScope("") == "");
  assert(NormalizeAgentScope("   ") == "");
}

void ScopesOverlapChecks() {
  using application::AgentScopesOverlap;
  // 1. "." and "/" overlap every non-empty scope.
  assert(AgentScopesOverlap(".", "src/a.cpp"));
  assert(AgentScopesOverlap("/", "src"));
  assert(AgentScopesOverlap("src", "."));
  // 2. equal scopes overlap.
  assert(AgentScopesOverlap("src/a.cpp", "src/a.cpp"));
  // 3. prefix containment overlaps in both directions.
  assert(AgentScopesOverlap("src", "src/a.cpp"));
  assert(AgentScopesOverlap("src/a.cpp", "src"));
  // 4. unrelated paths do not overlap.
  assert(!AgentScopesOverlap("src/a.cpp", "src/b.cpp"));
  assert(!AgentScopesOverlap("src", "src2"));
  // Empty scopes never overlap anything.
  assert(!AgentScopesOverlap("", "src"));
  assert(!AgentScopesOverlap("src", ""));
  assert(!AgentScopesOverlap("", ""));
}

void PreviewChecks() {
  using application::AgentPreviewFrom;
  using application::ClampAgentPreview;
  // AgentResultRecord.PREVIEW_MAX_CHARS.
  static_assert(application::kAgentPreviewMaxChars == 240U);
  // Blank input yields an empty preview rather than whitespace.
  assert(ClampAgentPreview("") == "");
  assert(ClampAgentPreview("   \n\t ") == "");
  assert(ClampAgentPreview("  hello  ") == "hello");
  // At the limit the trimmed text is returned unchanged.
  assert(ClampAgentPreview(std::string(240, 'a')) == std::string(240, 'a'));
  // Over the limit it is cut to exactly PREVIEW_MAX_CHARS characters.
  assert(ClampAgentPreview(std::string(300, 'a')) == std::string(240, 'a'));
  // Multi-byte text is counted in UTF-16 code units, not bytes: 300 BMP
  // characters are 900 bytes but only 300 units.
  std::string cjk;
  for (int index = 0; index < 300; ++index)
    cjk += "中";
  assert(cjk.size() == 900U);
  assert(ClampAgentPreview(cjk).size() == 720U);
  // A non-BMP code point costs two units and is never split in half.
  std::string emoji;
  for (int index = 0; index < 200; ++index)
    emoji += "\xF0\x9F\x98\x80";
  const auto emoji_preview = ClampAgentPreview(emoji);
  assert(emoji_preview.size() == 480U);
  assert(emoji_preview == emoji.substr(0, 480U));
  // previewFrom delegates to clampPreview.
  assert(AgentPreviewFrom("") == "");
  assert(AgentPreviewFrom("  body  ") == "body");
  assert(AgentPreviewFrom(std::string(400, 'x')).size() == 240U);
}

void RecordChecks() {
  using application::AgentResultRecord;
  using application::CreateAgentResultRecord;

  // The normalizing constructor: empty status, negative count, missing
  // timestamp and an over-long preview are all repaired.
  auto record = CreateAgentResultRecord(
      "ag_9", "call_9", "agent", "", "explore", "desc", std::string(300, 'p'),
      "body", "thinking", R"({"status":"running"})", -5, true, true, 4, 0);
  assert(record.agent_id == "ag_9");
  assert(record.tool_call_id == "call_9");
  assert(record.tool_name == "agent");
  assert(record.status == "running");
  assert(record.preview.size() == 240U);
  assert(record.full_output == "body");
  assert(record.thinking == "thinking");
  assert(record.progress_json == R"({"status":"running"})");
  assert(record.tool_call_count == 0);
  assert(record.error);
  assert(record.async);
  assert(record.generation_id == 4);
  assert(record.updated_at_ms > 0);

  // Running(): the legacy factory used for a dispatched agent.
  auto running = AgentResultRecord::Running("ag_1", "call_1", "agent",
                                            "explore", "scout the tree", true,
                                            7);
  assert(running.status == "running");
  assert(running.IsRunning());
  assert(running.preview.empty());
  assert(running.full_output.empty());
  assert(running.tool_call_count == 0);
  assert(!running.error);
  assert(running.async);
  assert(running.generation_id == 7);

  // isRunning() covers the three legacy in-flight statuses.
  const auto with_status = [](std::string status) {
    auto record = AgentResultRecord::Running("ag_s", "", "agent", "explore",
                                             "d", false, 0);
    record.status = std::move(status);
    return record;
  };
  assert(with_status("pending").IsRunning());
  assert(with_status("waiting_unlock").IsRunning());
  assert(!with_status("done").IsRunning());
  assert(!with_status("error").IsRunning());

  // withStatus() replaces status/error/preview and stamps a new time.
  auto updated = running.WithStatus("done", false, "next preview");
  assert(updated.status == "done");
  assert(updated.preview == "next preview");
  assert(updated.agent_id == "ag_1");
  assert(updated.async);

  // withFullOutput() promotes the status and re-derives the preview.
  auto finished = running.WithFullOutput("the full body", "why", "{}", 3, false);
  assert(finished.status == "done");
  assert(finished.full_output == "the full body");
  assert(finished.thinking == "why");
  assert(finished.progress_json == "{}");
  assert(finished.tool_call_count == 3);
  assert(!finished.error);
  assert(finished.preview == "the full body");
  auto failed = running.WithFullOutput("boom", "", "", 1, true);
  assert(failed.status == "error");
  assert(failed.error);

  // withPreview() only swaps the preview.
  assert(running.WithPreview("  partial  ").preview == "partial");
  assert(running.WithPreview("  partial  ").status == "running");
}

void RegistryChecks() {
  using application::AgentOutputResult;
  using application::AgentResultRecord;
  using application::AgentResultRegistry;
  using application::ToolTextLanguage;

  AgentResultRegistry registry;

  // Legacy allocateId(): "ag_" + base36(now) + "_" + base36(sequence).
  const auto first = registry.AllocateId();
  const auto second = registry.AllocateId();
  assert(first.starts_with("ag_"));
  assert(second.starts_with("ag_"));
  assert(first != second);
  assert(first.ends_with("_1"));
  assert(second.ends_with("_2"));

  // A record without an agent id is silently dropped.
  registry.Put(AgentResultRecord{});
  assert(registry.Size() == 0U);
  assert(!registry.Contains("ag_missing"));
  assert(!registry.GetRecord("").has_value());
  assert(!registry.GetRecord("ag_missing").has_value());

  registry.Put(AgentResultRecord::Running("ag_1", "call_1", "agent", "explore",
                                          "scout", true, 7));
  registry.Put(AgentResultRecord::Running("ag_2", "call_2", "agent_pipeline",
                                          "pipeline", "2 agents", false, 7));
  registry.Put(AgentResultRecord::Running("ag_3", "call_3", "agent", "explore",
                                          "old generation", false, 8));
  assert(registry.Size() == 3U);
  assert(registry.Contains("ag_1"));
  // Insertion order, like the legacy LinkedHashMap.
  assert(registry.AgentIds() ==
         (std::vector<std::string>{"ag_1", "ag_2", "ag_3"}));

  // Updating an unknown id is a no-op instead of inserting a partial row.
  registry.UpdateStatus("ag_missing", "done", false, "x");
  registry.UpdateFullOutput("ag_missing", "x", "", "", 1);
  assert(registry.Size() == 3U);

  registry.UpdateStatus("ag_1", "done", false, "partial preview");
  const auto updated = registry.GetRecord("ag_1");
  assert(updated.has_value());
  assert(updated->status == "done");
  assert(updated->preview == "partial preview");
  assert(updated->async);

  registry.UpdateFullOutput("ag_2", "pipeline summary", "think", "{}", 5, true);
  const auto finished = registry.GetRecord("ag_2");
  assert(finished.has_value());
  assert(finished->status == "error");
  assert(finished->full_output == "pipeline summary");
  assert(finished->tool_call_count == 5);
  assert(finished->preview == "pipeline summary");

  // Re-putting an existing id replaces the row in place.
  registry.Put(AgentResultRecord::Running("ag_1", "call_1", "agent", "explore",
                                          "replacement", false, 7));
  assert(registry.AgentIds() ==
         (std::vector<std::string>{"ag_1", "ag_2", "ag_3"}));
  assert(registry.GetRecord("ag_1")->description == "replacement");

  // clearGeneration() drops only the matching generation.
  registry.ClearGeneration(8);
  assert(registry.Size() == 2U);
  assert(!registry.Contains("ag_3"));
  registry.Clear();
  assert(registry.Size() == 0U);
  assert(registry.AgentIds().empty());

  // ---- agent_output ------------------------------------------------------
  AgentResultRegistry store;
  // An unknown id is reported with the legacy message.
  auto missing = store.Fetch("ghost", "output");
  assert(missing.error);
  assert(missing.content == kNotFound);
  // The Chinese catalog resolves the same key.
  assert(store.Fetch("ghost", "output", ToolTextLanguage::chinese).content ==
         "未知 agent_id: ghost");

  // A running agent answers `include=meta` with the status fields, and the
  // default output mode with the "still running" envelope plus the message.
  store.Put(AgentResultRecord::Running("ag_run", "call_run", "agent", "explore",
                                       "scan the tree", true, 3));
  auto meta = store.Fetch("ag_run", "meta");
  assert(!meta.error);
  const auto meta_object = MustParseObject(meta.content);
  assert(meta_object.size() == 8U);
  assert(StringAt(&meta_object, "agent_id") == "ag_run");
  assert(StringAt(&meta_object, "status") == "running");
  assert(StringAt(&meta_object, "type") == "explore");
  assert(StringAt(&meta_object, "description") == "scan the tree");
  assert(StringAt(&meta_object, "preview").empty());
  assert(!BoolAt(&meta_object, "error"));
  assert(BoolAt(&meta_object, "async"));
  assert(json::Find(meta_object, "tool_call_count") != nullptr);
  // `include` is trimmed before the comparison, and meta wins over running.
  assert(store.Fetch("ag_run", "  meta  ").content == meta.content);

  auto running_body = store.Fetch("ag_run", "output");
  assert(!running_body.error);
  const auto running_object = MustParseObject(running_body.content);
  assert(running_object.size() == 7U);
  assert(StringAt(&running_object, "agent_id") == "ag_run");
  assert(StringAt(&running_object, "message") == kStillRunning);
  assert(json::Find(running_object, "error") == nullptr);
  // An absent include defaults to "output".
  assert(store.Fetch("ag_run", "").content == running_body.content);

  // A finished agent returns the full output, and falls back to the preview
  // when the full output is blank.
  auto done = AgentResultRecord::Running("ag_done", "call_done", "agent",
                                         "sub-coding", "write it", false, 3)
                  .WithFullOutput("the full body", "", "", 4, false);
  store.Put(done);
  const auto output = store.Fetch("ag_done", "output");
  assert(!output.error);
  assert(output.content == "the full body");
  assert(store.Fetch("ag_done", "meta").content != output.content);

  auto preview_only =
      application::CreateAgentResultRecord("ag_preview", "call", "agent", "done",
                                           "explore", "desc", "  the preview  ",
                                           "", "", "", 1, false, false, 3, 0);
  store.Put(preview_only);
  const auto preview_body = store.Fetch("ag_preview", "output");
  assert(!preview_body.error);
  assert(preview_body.content == "the preview");

  // An errored agent returns its body flagged as an error, or the legacy
  // "finished with error" text when there is no body at all.
  auto errored = AgentResultRecord::Running("ag_error", "call_error", "agent",
                                            "explore", "boom", false, 3)
                     .WithFullOutput("partial failure text", "", "", 2, true);
  store.Put(errored);
  const auto error_body = store.Fetch("ag_error", "output");
  assert(error_body.error);
  assert(error_body.content == "partial failure text");

  auto empty_error =
      application::CreateAgentResultRecord("ag_empty_error", "call", "agent",
                                           "done", "explore", "desc", "", "", "",
                                           "", 0, true, false, 3, 0);
  store.Put(empty_error);
  const auto empty_error_body = store.Fetch("ag_empty_error", "output");
  assert(empty_error_body.error);
  assert(empty_error_body.content == kOutputFailed);
  // meta never fails, even for an errored record.
  const auto empty_error_meta = store.Fetch("ag_empty_error", "meta");
  assert(!empty_error_meta.error);
  const auto empty_error_meta_object = MustParseObject(empty_error_meta.content);
  assert(BoolAt(&empty_error_meta_object, "error"));

  auto empty_done =
      application::CreateAgentResultRecord("ag_empty", "call", "agent", "done",
                                           "explore", "desc", "", "", "", "", 0,
                                           false, false, 3, 0);
  store.Put(empty_done);
  const auto empty_body = store.Fetch("ag_empty", "output");
  assert(!empty_body.error);
  assert(empty_body.content == kOutputEmpty);

  // A body over the 50KB single-result limit is middle truncated.
  store.Put(application::CreateAgentResultRecord(
      "ag_big", "call", "agent", "done", "explore", "desc", "", std::string(60000, 'x'),
      "", "", 0, false, false, 3, 0));
  const auto big = store.Fetch("ag_big", "output");
  assert(!big.error);
  assert(big.content.contains("(8800 chars truncated)"));
  assert(big.content.size() > 51200U);
  assert(big.content.starts_with(std::string(25600, 'x')));
  assert(big.content.ends_with(std::string(25600, 'x')));
}

void CompactRefChecks() {
  using application::AgentResultRecord;
  using application::AgentResultRegistry;

  assert(AgentResultRegistry::kCompactMarker == "linecode_agent_ref");

  auto record = AgentResultRecord::Running("ag_7", "call_7", "agent", "explore",
                                           "scan the tree", true, 3)
                    .WithFullOutput("the full transcript", "", "", 6, false);
  const auto compact = AgentResultRegistry::ToCompactJson(record);
  const auto object = MustParseObject(compact);
  assert(object.size() == 10U);
  assert(BoolAt(&object, "linecode_agent_ref"));
  assert(StringAt(&object, "agent_id") == "ag_7");
  assert(StringAt(&object, "status") == "done");
  assert(StringAt(&object, "type") == "explore");
  assert(StringAt(&object, "description") == "scan the tree");
  assert(StringAt(&object, "preview") == "the full transcript");
  assert(StringAt(&object, "tool_call_id") == "call_7");
  assert(BoolAt(&object, "async"));
  assert(!BoolAt(&object, "error"));
  // The compact ref never carries the transcript or the progress payload.
  assert(json::Find(object, "full_output") == nullptr);
  assert(json::Find(object, "thinking") == nullptr);
  assert(json::Find(object, "progress_json") == nullptr);

  // The originating tool call id is omitted when there is none.
  auto anonymous = AgentResultRecord::Running("ag_8", "", "agent", "explore",
                                              "scan", false, 3);
  const auto anonymous_object =
      MustParseObject(AgentResultRegistry::ToCompactJson(anonymous));
  assert(anonymous_object.size() == 9U);
  assert(json::Find(anonymous_object, "tool_call_id") == nullptr);

  // parseCompact() recognizes its own output.
  const auto round_trip = AgentResultRegistry::ParseCompact(compact);
  assert(round_trip.has_value());
  assert(round_trip->agent_id == "ag_7");
  assert(round_trip->tool_call_id == "call_7");
  assert(round_trip->status == "done");
  assert(round_trip->type == "explore");
  assert(round_trip->description == "scan the tree");
  assert(round_trip->preview == "the full transcript");
  assert(round_trip->tool_call_count == 6);
  assert(round_trip->async);
  assert(!round_trip->error);
  assert(round_trip->full_output.empty());

  // Anything without the marker (or without an id) is not a compact ref.
  assert(!AgentResultRegistry::ParseCompact("").has_value());
  assert(!AgentResultRegistry::ParseCompact("   ").has_value());
  assert(!AgentResultRegistry::ParseCompact("not json").has_value());
  assert(!AgentResultRegistry::ParseCompact("[1,2,3]").has_value());
  assert(!AgentResultRegistry::ParseCompact(R"({"agent_id":"ag_1"})")
              .has_value());
  assert(!AgentResultRegistry::ParseCompact(
              R"({"linecode_agent_ref":false,"agent_id":"ag_1"})")
              .has_value());
  assert(!AgentResultRegistry::ParseCompact(
              R"({"linecode_agent_ref":true,"agent_id":"  "})")
              .has_value());
  // Status defaults to "running", like the legacy optString fallback.
  const auto minimal = AgentResultRegistry::ParseCompact(
      R"({"linecode_agent_ref":true,"agent_id":" ag_1 "})");
  assert(minimal.has_value());
  assert(minimal->agent_id == "ag_1");
  assert(minimal->status == "running");
  assert(minimal->IsRunning());
}

void PipelineParseChecks() {
  using application::ParsePipelineAgents;
  using domain::PipelinePlanErrorCode;
  using domain::PlanPipeline;

  // A well-formed array is normalized: ids and text trimmed, types mapped.
  const auto agents = ParsePipelineAgents(R"([
      {"id":" a ","type":"Explore","description":" scan ","prompt":" do it ",
       "read_scope":[" src ","  "],"write_scope":[],"depends_on":[]},
      {"id":"b","type":"coding","description":"write","prompt":"write it",
       "write_scope":[" src/b.cpp "],"depends_on":["a"]}
    ])");
  assert(agents.size() == 2U);
  assert(agents[0].id == "a");
  assert(agents[0].type == "explore");
  assert(agents[0].description == "scan");
  assert(agents[0].prompt == "do it");
  assert(agents[0].read_scope == std::vector<std::string>{"src"});
  assert(agents[0].write_scope.empty());
  assert(agents[1].id == "b");
  assert(agents[1].type == "sub-coding");
  assert(agents[1].write_scope == std::vector<std::string>{"src/b.cpp"});
  assert(agents[1].dependencies == std::vector<std::string>{"a"});

  // Every malformed shape discards the whole list, like the legacy resolver.
  assert(ParsePipelineAgents("").empty());
  assert(ParsePipelineAgents("not json").empty());
  assert(ParsePipelineAgents("{}").empty());
  assert(ParsePipelineAgents("[]").empty());
  assert(ParsePipelineAgents(R"([1,2])").empty());
  assert(ParsePipelineAgents(R"([{"id":"a"},42])").empty());
  assert(ParsePipelineAgents(R"([{"type":"explore"}])").empty());
  assert(ParsePipelineAgents(R"([{"id":"  "}])").empty());
  assert(ParsePipelineAgents(R"([{"id":"a"},{"id":"a"}])").empty());

  // The parsed agents feed the domain planner directly.
  const auto plan = PlanPipeline(ParsePipelineAgents(R"([
      {"id":"root","type":"explore","description":"d","prompt":"p"},
      {"id":"left","type":"sub-coding","description":"d","prompt":"p",
       "write_scope":["src/left"],"depends_on":["root"]},
      {"id":"right","type":"sub-coding","description":"d","prompt":"p",
       "write_scope":["src/right"],"depends_on":["root"]},
      {"id":"join","type":"explore","description":"d","prompt":"p",
       "depends_on":["left","right"]}
    ])"));
  assert(plan.ok());
  assert(plan.levels.size() == 3U);
  assert(plan.levels[0].size() == 1U);
  assert(plan.levels[0][0].id == "root");
  assert(plan.levels[1].size() == 2U);
  assert(plan.levels[1][0].id == "left");
  assert(plan.levels[1][1].id == "right");
  assert(plan.levels[2].size() == 1U);
  assert(plan.levels[2][0].id == "join");

  // Unknown dependencies and cycles are planner errors, not parse errors.
  const auto unknown = PlanPipeline(
      ParsePipelineAgents(R"([{"id":"a","type":"explore","description":"d",
                               "prompt":"p","depends_on":["ghost"]}])"));
  assert(!unknown.ok());
  assert(unknown.error.code == PipelinePlanErrorCode::unknown_dependency);
  assert(unknown.error.agent_id == "ghost");

  const auto cycle = PlanPipeline(ParsePipelineAgents(R"([
      {"id":"a","type":"explore","description":"d","prompt":"p",
       "depends_on":["b"]},
      {"id":"b","type":"explore","description":"d","prompt":"p",
       "depends_on":["a"]}
    ])"));
  assert(!cycle.ok());
  assert(cycle.error.code == PipelinePlanErrorCode::cycle);
}

// ---------------------------------------------------------------------------
// Tool registry probe
// ---------------------------------------------------------------------------

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      using application::ToolRegistryErrorCode;
      using application::ToolInvocationResult;
      using application::AgentResultRecord;

      // 1. Declarations: three tools, legacy order, legacy flags.
      auto refreshed = co_await scenario->tools->Refresh();
      assert(refreshed);
      assert(scenario->tools->Tools().size() == 3U);
      // Copies, not references: a later Refresh() replaces the whole catalog.
      const application::RegisteredTool agent = scenario->tools->Tools()[0];
      const application::RegisteredTool pipeline = scenario->tools->Tools()[1];
      const application::RegisteredTool agent_output =
          scenario->tools->Tools()[2];
      assert(agent.name == application::kAgentToolName);
      assert(agent.name == "agent");
      assert(pipeline.name == application::kAgentPipelineToolName);
      assert(pipeline.name == "agent_pipeline");
      assert(agent_output.name == application::kAgentOutputToolName);
      assert(agent_output.name == "agent_output");
      assert(agent.description == kLegacyAgentDescription);
      assert(pipeline.description == kLegacyPipelineDescription);
      assert(agent_output.description == kLegacyAgentOutputDescription);
      for (const auto *tool : {&agent, &pipeline, &agent_output}) {
        assert(tool->category == "agent");
        // All three override isAllowedInReadonlyMode() with true.
        assert(tool->allowed_in_read_only);
        assert(!tool->permanent_grant_supported);
        assert(tool->agent_selectable);
      }

      // 2. Schemas: byte-identical to the canonical serialization of the
      // legacy getParameters() result.
      assert(agent.parameters_json == kLegacyAgentSchema);
      assert(pipeline.parameters_json == kLegacyPipelineSchema);
      assert(agent_output.parameters_json == kLegacyAgentOutputSchema);
      // Re-serializing the parsed schema reproduces the literal, which proves
      // the canonical sorted-key form is what the descriptor carries.
      for (const auto *tool : {&agent, &pipeline, &agent_output}) {
        auto parsed = json::Parse(tool->parameters_json);
        assert(parsed);
        assert(json::Serialize(*parsed) == tool->parameters_json);
      }

      const auto agent_schema = MustParseObject(agent.parameters_json);
      assert(StringAt(&agent_schema, "type") == "object");
      assert(SchemaPropertyCount(agent_schema) == 6U);
      assert(RequiredNames(agent_schema) ==
             (std::vector<std::string>{"type", "description", "prompt"}));
      const auto *agent_properties = ObjectAt(&agent_schema, "properties");
      const auto *type_property = ObjectAt(agent_properties, "type");
      assert(StringAt(type_property, "type") == "string");
      assert(StringAt(type_property, "description") ==
             "Agent type: explore for read-only exploration, sub-coding for "
             "programming subtasks");
      const auto *type_enum = json::AsArray(json::Find(*type_property, "enum"));
      assert(type_enum != nullptr);
      assert(type_enum->size() == 2U);
      assert(StringAt(&type_enum->at(0)) == "explore");
      assert(StringAt(&type_enum->at(1)) == "sub-coding");
      assert(StringAt(ObjectAt(agent_properties, "description"), "description") ==
             "Task title of 3-8 words");
      assert(StringAt(ObjectAt(agent_properties, "async"), "type") == "boolean");
      for (const auto *name : {"read_scope", "write_scope"}) {
        const auto *property = ObjectAt(agent_properties, name);
        assert(StringAt(property, "type") == "array");
        assert(StringAt(ObjectAt(property, "items"), "type") == "string");
      }

      const auto pipeline_schema = MustParseObject(pipeline.parameters_json);
      assert(SchemaPropertyCount(pipeline_schema) == 1U);
      assert(RequiredNames(pipeline_schema) ==
             (std::vector<std::string>{"agents"}));
      const auto *agents_property =
          ObjectAt(ObjectAt(&pipeline_schema, "properties"), "agents");
      assert(StringAt(agents_property, "type") == "array");
      assert(StringAt(agents_property, "description") == "List of Agent tasks");
      const auto *item = ObjectAt(agents_property, "items");
      assert(StringAt(item, "type") == "object");
      assert(SchemaPropertyCount(*item) == 7U);
      assert(RequiredNames(*item) ==
             (std::vector<std::string>{"id", "type", "description", "prompt"}));

      const auto output_schema = MustParseObject(agent_output.parameters_json);
      assert(SchemaPropertyCount(output_schema) == 2U);
      assert(RequiredNames(output_schema) ==
             (std::vector<std::string>{"agent_id"}));
      const auto *include_property =
          ObjectAt(ObjectAt(&output_schema, "properties"), "include");
      const auto *include_enum =
          json::AsArray(json::Find(*include_property, "enum"));
      assert(include_enum != nullptr);
      assert(StringAt(&include_enum->at(0)) == "output");
      assert(StringAt(&include_enum->at(1)) == "meta");

      // 3. `agent` validation branches (AgentTool.java:87-115).

      // 3a. Type must be explore or sub-coding.
      auto invoked = co_await scenario->tools->Invoke(
          "agent",
          R"({"type":"worker","description":"d","prompt":"p"})");
      assert(invoked && invoked->error);
      assert(invoked->content == kInvalidType);
      assert(scenario->runner->agent_runs == 0);
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"","description":"d","prompt":"p"})");
      assert(invoked && invoked->error && invoked->content == kInvalidType);

      // 3b. explore may not declare a non-empty write scope.
      invoked = co_await scenario->tools->Invoke(
          "agent",
          R"({"type":"explore","description":"d","prompt":"p","write_scope":["src"]})");
      assert(invoked && invoked->error && invoked->content == kExploreNoWrite);
      // A whitespace-only or non-array scope is not a declared scope.
      invoked = co_await scenario->tools->Invoke(
          "agent",
          R"({"type":"explore","description":"d","prompt":"p","write_scope":["   "]})");
      assert(invoked && !invoked->error && invoked->content == "compact-agent-ref");
      invoked = co_await scenario->tools->Invoke(
          "agent",
          R"({"type":"explore","description":"d","prompt":"p","write_scope":"src"})");
      assert(invoked && !invoked->error);

      // 3c/3d. description and prompt must not be blank.
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"   ","prompt":"p"})");
      assert(invoked && invoked->error && invoked->content == kDescriptionEmpty);
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","prompt":"p"})");
      assert(invoked && invoked->error && invoked->content == kDescriptionEmpty);
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"d","prompt":"  "})");
      assert(invoked && invoked->error && invoked->content == kPromptEmpty);
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"d"})");
      assert(invoked && invoked->error && invoked->content == kPromptEmpty);

      // 3e. An unparseable argument body is the ported "parse failed" branch.
      invoked = co_await scenario->tools->Invoke("agent", "not json");
      assert(invoked && invoked->error);
      assert(invoked->content.starts_with("Agent parameter parsing failed: "));
      invoked = co_await scenario->tools->Invoke("agent", "[1,2,3]");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "Agent parameter parsing failed: arguments must be a JSON object");

      // 3f. Without an engine the tool reports the legacy message.
      scenario->tools->SetRunner(nullptr);
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"d","prompt":"p"})");
      assert(invoked && invoked->error && invoked->content == kRunnerUnavailable);
      scenario->tools->SetRunner(scenario->runner);

      // 3g. A valid call reaches the engine with the normalized request.
      scenario->runner->last_request.reset();
      invoked = co_await scenario->tools->Invoke(
          "agent",
          R"({"type":" Coding ","description":"  write the file  ","prompt":"  do it  ","read_scope":[" src "],"write_scope":[" src/a.cpp "],"async":true})");
      assert(invoked && !invoked->error);
      assert(invoked->content == "compact-agent-ref");
      assert(scenario->runner->agent_runs >= 1);
      const auto &request = *scenario->runner->last_request;
      assert(request.type == "sub-coding");
      assert(request.description == "write the file");
      assert(request.prompt == "do it");
      assert(request.read_scope == std::vector<std::string>{"src"});
      assert(request.write_scope == std::vector<std::string>{"src/a.cpp"});
      assert(request.async);
      assert(request.agent_id.empty());

      // 3h. An engine error is surfaced as a tool error with its body.
      scenario->runner->agent_error = true;
      scenario->runner->agent_output = "agent blew up";
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"d","prompt":"p"})");
      assert(invoked && invoked->error && invoked->content == "agent blew up");
      scenario->runner->agent_error = false;
      scenario->runner->agent_output = "compact-agent-ref";

      // 4. `agent_pipeline` validation branches
      // (AgentPipelineTool.java:85-153).

      // 4a. `agents` must be a non-empty array.
      invoked = co_await scenario->tools->Invoke("agent_pipeline", "{}");
      assert(invoked && invoked->error && invoked->content == kAgentsEmpty);
      invoked = co_await scenario->tools->Invoke("agent_pipeline",
                                                 R"({"agents":[]})");
      assert(invoked && invoked->error && invoked->content == kAgentsEmpty);
      invoked = co_await scenario->tools->Invoke("agent_pipeline",
                                                 R"({"agents":"nope"})");
      assert(invoked && invoked->error && invoked->content == kAgentsEmpty);

      // 4b. Every element must be an object.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"},42]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "agents[1] must be an object.");

      // 4c. The id must not be blank.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"  ","type":"explore","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "agents[0].id cannot be empty.");

      // 4d. Ids must be unique.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"},{"id":" a ","type":"explore","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "Agent id duplicate: a");

      // 4e. An agent may not depend on itself.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"scout","type":"explore","description":"d","prompt":"p","depends_on":[" scout "]}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "Agent cannot depend on itself: scout");

      // 4f. The type must be explore or sub-coding.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"worker","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == kPipelineInvalidType);

      // 4g. explore may not write; sub-coding must write.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p","write_scope":["src/a.cpp"]}]})");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "explore Agent cannot declare write_scope and cannot write files: a");
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "sub-coding Agent must declare write_scope to avoid multiple Agents writing the same file: a");

      // 4h. The same normalized scope twice inside one agent.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["./src/a.cpp","src//a.cpp"]}]})");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "Agent a has duplicate write_scope: src//a.cpp");

      // 4i. Two agents may not own equal or enclosing write scopes.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]}]})");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "Multiple Agents cannot write the same file or overlapping "
             "directories: a's src/a.cpp conflicts with b's src/a.cpp. Please "
             "merge into one Agent or split into different files/directories.");
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["./src/a.cpp"]}]})");
      assert(invoked && invoked->error);
      assert(invoked->content.starts_with(
          "Multiple Agents cannot write the same file or overlapping "
          "directories: a's src conflicts with b's ./src/a.cpp."));
      // Unrelated scopes are accepted.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/b.cpp"]}]})");
      assert(invoked && !invoked->error);
      assert(invoked->content == "compact-pipeline-ref");

      // 4j. description and prompt are checked after the scope rules.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"  ","prompt":"p"}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "agents[0].description cannot be empty.");
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":" "}]})");
      assert(invoked && invoked->error);
      assert(invoked->content == "agents[0].prompt cannot be empty.");
      // A broken scope of the same agent is reported first.
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"sub-coding","description":"","prompt":""}]})");
      assert(invoked && invoked->error);
      assert(invoked->content ==
             "sub-coding Agent must declare write_scope to avoid multiple Agents writing the same file: a");

      // 4k. Without an engine the tool reports the legacy message.
      scenario->tools->SetRunner(nullptr);
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error &&
             invoked->content == kPipelineRunnerUnavailable);
      scenario->tools->SetRunner(scenario->runner);

      // 4l. A valid pipeline reaches the engine with the parsed agents.
      scenario->runner->last_pipeline.reset();
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"root","type":"explore","description":"scan","prompt":"scan it","read_scope":[" src "]},{"id":"leaf","type":"coding","description":"write","prompt":"write it","write_scope":[" src/leaf.cpp "],"depends_on":["root"]}]})");
      assert(invoked && !invoked->error);
      assert(invoked->content == "compact-pipeline-ref");
      const auto &agents = scenario->runner->last_pipeline->agents;
      assert(agents.size() == 2U);
      assert(agents[0].id == "root");
      assert(agents[0].type == "explore");
      assert(agents[0].read_scope == std::vector<std::string>{"src"});
      assert(agents[1].id == "leaf");
      assert(agents[1].type == "sub-coding");
      assert(agents[1].write_scope == std::vector<std::string>{"src/leaf.cpp"});
      assert(agents[1].dependencies == std::vector<std::string>{"root"});

      // 4m. An engine error is surfaced as a tool error with its body.
      scenario->runner->pipeline_error = true;
      scenario->runner->pipeline_output = "pipeline blew up";
      invoked = co_await scenario->tools->Invoke(
          "agent_pipeline",
          R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"}]})");
      assert(invoked && invoked->error && invoked->content == "pipeline blew up");
      scenario->runner->pipeline_error = false;
      scenario->runner->pipeline_output = "compact-pipeline-ref";

      // 5. `agent_output` through the tool, both include modes.
      auto stored = scenario->results->GetRecord("ag_tool_done");
      assert(!stored.has_value());
      scenario->results->Put(
          AgentResultRecord::Running("ag_tool_done", "call_1", "agent",
                                     "explore", "scan the tree", false, 1)
              .WithFullOutput("the full transcript", "", "", 3, false));

      invoked =
          co_await scenario->tools->Invoke("agent_output", R"({"agent_id":" "})");
      assert(invoked && invoked->error && invoked->content == kIdMissing);
      invoked = co_await scenario->tools->Invoke("agent_output", "{}");
      assert(invoked && invoked->error && invoked->content == kIdMissing);
      invoked = co_await scenario->tools->Invoke(
          "agent_output", R"({"agent_id":"ghost"})");
      assert(invoked && invoked->error && invoked->content == kNotFound);

      invoked = co_await scenario->tools->Invoke(
          "agent_output", R"({"agent_id":"ag_tool_done"})");
      assert(invoked && !invoked->error);
      assert(invoked->content == "the full transcript");

      invoked = co_await scenario->tools->Invoke(
          "agent_output", R"({"agent_id":"ag_tool_done","include":"meta"})");
      assert(invoked && !invoked->error);
      const auto tool_meta = MustParseObject(invoked->content);
      assert(StringAt(&tool_meta, "agent_id") == "ag_tool_done");
      assert(StringAt(&tool_meta, "status") == "done");
      assert(StringAt(&tool_meta, "preview") == "the full transcript");
      assert(json::Find(tool_meta, "message") == nullptr);

      // A null store reproduces the legacy "store is not available" branch.
      auto orphan = std::make_shared<application::AgentToolRegistry>(
          scenario->mcp, nullptr, scenario->runner);
      auto orphan_refresh = co_await orphan->Refresh();
      assert(orphan_refresh);
      auto orphan_invoked = co_await orphan->Invoke(
          "agent_output", R"({"agent_id":"ag_tool_done"})");
      assert(orphan_invoked && orphan_invoked->error &&
             orphan_invoked->content == kStoreMissing);

      // 6. Group gating.
      Group(scenario->mcp, "agent").enabled = false;
      auto disabled = co_await scenario->tools->Refresh();
      assert(disabled);
      assert(scenario->tools->Tools().empty());
      invoked = co_await scenario->tools->Invoke(
          "agent", R"({"type":"explore","description":"d","prompt":"p"})");
      assert(!invoked);
      assert(invoked.error().code == ToolRegistryErrorCode::unavailable);
      // A tool that is not part of this registry is always unknown.
      invoked = co_await scenario->tools->Invoke("todo_update", "{}");
      assert(!invoked);
      assert(invoked.error().code == ToolRegistryErrorCode::unknown_tool);

      // The agent group supports every execution mode; a narrowed mask hides
      // the whole group.
      Group(scenario->mcp, "agent").enabled = true;
      Group(scenario->mcp, "agent").supported_modes =
          domain::McpExecutionModeMask::local;
      scenario->mcp->value.mode = domain::McpExecutionMode::ssh;
      auto unsupported = co_await scenario->tools->Refresh();
      assert(unsupported);
      assert(scenario->tools->Tools().empty());
      scenario->mcp->value.mode = domain::McpExecutionMode::local;
      Group(scenario->mcp, "agent").supported_modes =
          domain::McpExecutionModeMask::all;
      auto supported = co_await scenario->tools->Refresh();
      assert(supported);
      assert(scenario->tools->Tools().size() == 3U);

      // 7. Settings failures propagate.
      scenario->mcp->fail_load = true;
      auto failed = co_await scenario->tools->Refresh();
      assert(!failed);
      assert(failed.error().code == ToolRegistryErrorCode::load_failed);
      assert(failed.error().message.contains("injected"));
      scenario->mcp->fail_load = false;

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("agent-tool-registry-probe");
}

} // namespace

int main() {
  // Pure contract helpers run before the UI harness starts.
  NormalizeTypeChecks();
  NormalizeScopeChecks();
  ScopesOverlapChecks();
  PreviewChecks();
  RecordChecks();
  RegistryChecks();
  CompactRefChecks();
  PipelineParseChecks();

  // The settings service is mandatory; a null one fails fast. A null result
  // registry or runner is allowed and surfaces as the legacy tool error.
  active = std::make_shared<Scenario>();
  bool rejected_settings = false;
  try {
    application::AgentToolRegistry invalid(nullptr, active->results,
                                           active->runner);
  } catch (const std::invalid_argument &) {
    rejected_settings = true;
  }
  assert(rejected_settings);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  assert(active->done);
  active.reset();

  std::cout << "agent_tool_tests passed\n";
  return 0;
}
