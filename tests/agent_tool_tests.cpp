// Contract tests for the agent tool group: declarations, argument validation,
// pipeline dependency parsing and the agent result registry.

#include "gtest_support.h"
#include <algorithm>
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
    "Dispatch a sub-Agent to handle a task. explore is read-only; sub-coding "
    "must have a clear and unique write scope. "
    "Returns a compact ref with agent_id (not full transcript). Use "
    "agent_output(agent_id) to fetch full output when needed. "
    "Optional async=true returns immediately for explore agents.";

constexpr std::string_view kLegacyPipelineDescription =
    "Create a pipeline of Agent tasks with dependencies. sub-coding must "
    "declare a unique write_scope; multiple Agents cannot write the same file "
    "or overlapping directories.";

constexpr std::string_view kLegacyAgentOutputDescription =
    "Fetch a previously started agent result by agent_id. "
    "The agent / agent_pipeline tools return a compact ref with agent_id; "
    "call this tool when you need the full output (or while async agents are "
    "still running). "
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
  EXPECT_EXPRESSION(found != settings->value.groups.end());
  return *found;
}

// ---------------------------------------------------------------------------
// JSON helpers
// ---------------------------------------------------------------------------

const json::Object *ObjectAt(const json::Object *object, std::string_view key) {
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
  EXPECT_EXPRESSION(parsed);
  const auto *object = json::AsObject(&*parsed);
  EXPECT_EXPRESSION(object != nullptr);
  return *object;
}

std::vector<std::string> RequiredNames(const json::Object &schema) {
  std::vector<std::string> names;
  const auto *required = json::AsArray(json::Find(schema, "required"));
  EXPECT_EXPRESSION(required != nullptr);
  for (const auto &value : *required)
    names.push_back(StringAt(&value));
  return names;
}

std::size_t SchemaPropertyCount(const json::Object &schema) {
  const auto *properties = ObjectAt(&schema, "properties");
  EXPECT_EXPRESSION(properties != nullptr);
  return properties->size();
}

// ---------------------------------------------------------------------------
// Pure functions
// ---------------------------------------------------------------------------

void NormalizeTypeChecks() {
  using application::NormalizeAgentType;
  // The three legacy aliases map onto "sub-coding" (AgentTool.java:119-121).
  EXPECT_EXPRESSION(NormalizeAgentType("sub_coding") == "sub-coding");
  EXPECT_EXPRESSION(NormalizeAgentType("subcoding") == "sub-coding");
  EXPECT_EXPRESSION(NormalizeAgentType("coding") == "sub-coding");
  // Trim + Locale.US lowering first, then the value passes through unchanged.
  EXPECT_EXPRESSION(NormalizeAgentType(" SUB_CODING ") == "sub-coding");
  EXPECT_EXPRESSION(NormalizeAgentType("Explore") == "explore");
  EXPECT_EXPRESSION(NormalizeAgentType("sub-coding") == "sub-coding");
  EXPECT_EXPRESSION(NormalizeAgentType("weird") == "weird");
  EXPECT_EXPRESSION(NormalizeAgentType("") == "");
}

void NormalizeScopeChecks() {
  using application::NormalizeAgentScope;
  // 1. backslashes become forward slashes.
  EXPECT_EXPRESSION(NormalizeAgentScope("src\\main\\a.cpp") ==
                    "src/main/a.cpp");
  // 2. every leading "./" is stripped.
  EXPECT_EXPRESSION(NormalizeAgentScope("./src/a.cpp") == "src/a.cpp");
  EXPECT_EXPRESSION(NormalizeAgentScope("././src") == "src");
  // 3. "//" is collapsed.
  EXPECT_EXPRESSION(NormalizeAgentScope("src//main//a.cpp") ==
                    "src/main/a.cpp");
  EXPECT_EXPRESSION(NormalizeAgentScope("a///b") == "a/b");
  // 4. a trailing slash is dropped unless the value is a bare "/".
  EXPECT_EXPRESSION(NormalizeAgentScope("src/main/") == "src/main");
  EXPECT_EXPRESSION(NormalizeAgentScope("/") == "/");
  EXPECT_EXPRESSION(NormalizeAgentScope("//") == "/");
  // Surrounding whitespace is trimmed first; a blank scope stays blank.
  EXPECT_EXPRESSION(NormalizeAgentScope("  src  ") == "src");
  EXPECT_EXPRESSION(NormalizeAgentScope("") == "");
  EXPECT_EXPRESSION(NormalizeAgentScope("   ") == "");
}

void ScopesOverlapChecks() {
  using application::AgentScopesOverlap;
  // 1. "." and "/" overlap every non-empty scope.
  EXPECT_EXPRESSION(AgentScopesOverlap(".", "src/a.cpp"));
  EXPECT_EXPRESSION(AgentScopesOverlap("/", "src"));
  EXPECT_EXPRESSION(AgentScopesOverlap("src", "."));
  // 2. equal scopes overlap.
  EXPECT_EXPRESSION(AgentScopesOverlap("src/a.cpp", "src/a.cpp"));
  // 3. prefix containment overlaps in both directions.
  EXPECT_EXPRESSION(AgentScopesOverlap("src", "src/a.cpp"));
  EXPECT_EXPRESSION(AgentScopesOverlap("src/a.cpp", "src"));
  // 4. unrelated paths do not overlap.
  EXPECT_EXPRESSION(!AgentScopesOverlap("src/a.cpp", "src/b.cpp"));
  EXPECT_EXPRESSION(!AgentScopesOverlap("src", "src2"));
  // Empty scopes never overlap anything.
  EXPECT_EXPRESSION(!AgentScopesOverlap("", "src"));
  EXPECT_EXPRESSION(!AgentScopesOverlap("src", ""));
  EXPECT_EXPRESSION(!AgentScopesOverlap("", ""));
}

void PreviewChecks() {
  using application::AgentPreviewFrom;
  using application::ClampAgentPreview;
  // AgentResultRecord.PREVIEW_MAX_CHARS.
  static_assert(application::kAgentPreviewMaxChars == 240U);
  // Blank input yields an empty preview rather than whitespace.
  EXPECT_EXPRESSION(ClampAgentPreview("") == "");
  EXPECT_EXPRESSION(ClampAgentPreview("   \n\t ") == "");
  EXPECT_EXPRESSION(ClampAgentPreview("  hello  ") == "hello");
  // At the limit the trimmed text is returned unchanged.
  EXPECT_EXPRESSION(ClampAgentPreview(std::string(240, 'a')) ==
                    std::string(240, 'a'));
  // Over the limit it is cut to exactly PREVIEW_MAX_CHARS characters.
  EXPECT_EXPRESSION(ClampAgentPreview(std::string(300, 'a')) ==
                    std::string(240, 'a'));
  // Multi-byte text is counted in UTF-16 code units, not bytes: 300 BMP
  // characters are 900 bytes but only 300 units.
  std::string cjk;
  for (int index = 0; index < 300; ++index)
    cjk += "中";
  EXPECT_EXPRESSION(cjk.size() == 900U);
  EXPECT_EXPRESSION(ClampAgentPreview(cjk).size() == 720U);
  // A non-BMP code point costs two units and is never split in half.
  std::string emoji;
  for (int index = 0; index < 200; ++index)
    emoji += "\xF0\x9F\x98\x80";
  const auto emoji_preview = ClampAgentPreview(emoji);
  EXPECT_EXPRESSION(emoji_preview.size() == 480U);
  EXPECT_EXPRESSION(emoji_preview == emoji.substr(0, 480U));
  // previewFrom delegates to clampPreview.
  EXPECT_EXPRESSION(AgentPreviewFrom("") == "");
  EXPECT_EXPRESSION(AgentPreviewFrom("  body  ") == "body");
  EXPECT_EXPRESSION(AgentPreviewFrom(std::string(400, 'x')).size() == 240U);
}

void RecordChecks() {
  using application::AgentResultRecord;
  using application::CreateAgentResultRecord;

  // The normalizing constructor: empty status, negative count, missing
  // timestamp and an over-long preview are all repaired.
  auto record = CreateAgentResultRecord(
      "ag_9", "call_9", "agent", "", "explore", "desc", std::string(300, 'p'),
      "body", "thinking", R"({"status":"running"})", -5, true, true, 4, 0);
  EXPECT_EXPRESSION(record.agent_id == "ag_9");
  EXPECT_EXPRESSION(record.tool_call_id == "call_9");
  EXPECT_EXPRESSION(record.tool_name == "agent");
  EXPECT_EXPRESSION(record.status == "running");
  EXPECT_EXPRESSION(record.preview.size() == 240U);
  EXPECT_EXPRESSION(record.full_output == "body");
  EXPECT_EXPRESSION(record.thinking == "thinking");
  EXPECT_EXPRESSION(record.progress_json == R"({"status":"running"})");
  EXPECT_EXPRESSION(record.tool_call_count == 0);
  EXPECT_EXPRESSION(record.error);
  EXPECT_EXPRESSION(record.async);
  EXPECT_EXPRESSION(record.generation_id == 4);
  EXPECT_EXPRESSION(record.updated_at_ms > 0);

  // Running(): the legacy factory used for a dispatched agent.
  auto running = AgentResultRecord::Running(
      "ag_1", "call_1", "agent", "explore", "scout the tree", true, 7);
  EXPECT_EXPRESSION(running.status == "running");
  EXPECT_EXPRESSION(running.IsRunning());
  EXPECT_EXPRESSION(running.preview.empty());
  EXPECT_EXPRESSION(running.full_output.empty());
  EXPECT_EXPRESSION(running.tool_call_count == 0);
  EXPECT_EXPRESSION(!running.error);
  EXPECT_EXPRESSION(running.async);
  EXPECT_EXPRESSION(running.generation_id == 7);

  // isRunning() covers the three legacy in-flight statuses.
  const auto with_status = [](std::string status) {
    auto record = AgentResultRecord::Running("ag_s", "", "agent", "explore",
                                             "d", false, 0);
    record.status = std::move(status);
    return record;
  };
  EXPECT_EXPRESSION(with_status("pending").IsRunning());
  EXPECT_EXPRESSION(with_status("waiting_unlock").IsRunning());
  EXPECT_EXPRESSION(!with_status("done").IsRunning());
  EXPECT_EXPRESSION(!with_status("error").IsRunning());

  // withStatus() replaces status/error/preview and stamps a new time.
  auto updated = running.WithStatus("done", false, "next preview");
  EXPECT_EXPRESSION(updated.status == "done");
  EXPECT_EXPRESSION(updated.preview == "next preview");
  EXPECT_EXPRESSION(updated.agent_id == "ag_1");
  EXPECT_EXPRESSION(updated.async);

  // withFullOutput() promotes the status and re-derives the preview.
  auto finished =
      running.WithFullOutput("the full body", "why", "{}", 3, false);
  EXPECT_EXPRESSION(finished.status == "done");
  EXPECT_EXPRESSION(finished.full_output == "the full body");
  EXPECT_EXPRESSION(finished.thinking == "why");
  EXPECT_EXPRESSION(finished.progress_json == "{}");
  EXPECT_EXPRESSION(finished.tool_call_count == 3);
  EXPECT_EXPRESSION(!finished.error);
  EXPECT_EXPRESSION(finished.preview == "the full body");
  auto failed = running.WithFullOutput("boom", "", "", 1, true);
  EXPECT_EXPRESSION(failed.status == "error");
  EXPECT_EXPRESSION(failed.error);

  // withPreview() only swaps the preview.
  EXPECT_EXPRESSION(running.WithPreview("  partial  ").preview == "partial");
  EXPECT_EXPRESSION(running.WithPreview("  partial  ").status == "running");
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
  EXPECT_EXPRESSION(first.starts_with("ag_"));
  EXPECT_EXPRESSION(second.starts_with("ag_"));
  EXPECT_EXPRESSION(first != second);
  EXPECT_EXPRESSION(first.ends_with("_1"));
  EXPECT_EXPRESSION(second.ends_with("_2"));

  // A record without an agent id is silently dropped.
  registry.Put(AgentResultRecord{});
  EXPECT_EXPRESSION(registry.Size() == 0U);
  EXPECT_EXPRESSION(!registry.Contains("ag_missing"));
  EXPECT_EXPRESSION(!registry.GetRecord("").has_value());
  EXPECT_EXPRESSION(!registry.GetRecord("ag_missing").has_value());

  registry.Put(AgentResultRecord::Running("ag_1", "call_1", "agent", "explore",
                                          "scout", true, 7));
  registry.Put(AgentResultRecord::Running("ag_2", "call_2", "agent_pipeline",
                                          "pipeline", "2 agents", false, 7));
  registry.Put(AgentResultRecord::Running("ag_3", "call_3", "agent", "explore",
                                          "old generation", false, 8));
  EXPECT_EXPRESSION(registry.Size() == 3U);
  EXPECT_EXPRESSION(registry.Contains("ag_1"));
  // Insertion order, like the legacy LinkedHashMap.
  EXPECT_EXPRESSION(registry.AgentIds() ==
                    (std::vector<std::string>{"ag_1", "ag_2", "ag_3"}));

  // Updating an unknown id is a no-op instead of inserting a partial row.
  registry.UpdateStatus("ag_missing", "done", false, "x");
  registry.UpdateFullOutput("ag_missing", "x", "", "", 1);
  EXPECT_EXPRESSION(registry.Size() == 3U);

  registry.UpdateStatus("ag_1", "done", false, "partial preview");
  const auto updated = registry.GetRecord("ag_1");
  EXPECT_EXPRESSION(updated.has_value());
  EXPECT_EXPRESSION(updated->status == "done");
  EXPECT_EXPRESSION(updated->preview == "partial preview");
  EXPECT_EXPRESSION(updated->async);

  registry.UpdateFullOutput("ag_2", "pipeline summary", "think", "{}", 5, true);
  const auto finished = registry.GetRecord("ag_2");
  EXPECT_EXPRESSION(finished.has_value());
  EXPECT_EXPRESSION(finished->status == "error");
  EXPECT_EXPRESSION(finished->full_output == "pipeline summary");
  EXPECT_EXPRESSION(finished->tool_call_count == 5);
  EXPECT_EXPRESSION(finished->preview == "pipeline summary");

  // Re-putting an existing id replaces the row in place.
  registry.Put(AgentResultRecord::Running("ag_1", "call_1", "agent", "explore",
                                          "replacement", false, 7));
  EXPECT_EXPRESSION(registry.AgentIds() ==
                    (std::vector<std::string>{"ag_1", "ag_2", "ag_3"}));
  EXPECT_EXPRESSION(registry.GetRecord("ag_1")->description == "replacement");

  // clearGeneration() drops only the matching generation.
  registry.ClearGeneration(8);
  EXPECT_EXPRESSION(registry.Size() == 2U);
  EXPECT_EXPRESSION(!registry.Contains("ag_3"));
  registry.Clear();
  EXPECT_EXPRESSION(registry.Size() == 0U);
  EXPECT_EXPRESSION(registry.AgentIds().empty());

  // ---- agent_output ------------------------------------------------------
  AgentResultRegistry store;
  // An unknown id is reported with the legacy message.
  auto missing = store.Fetch("ghost", "output");
  EXPECT_EXPRESSION(missing.error);
  EXPECT_EXPRESSION(missing.content == kNotFound);
  // The Chinese catalog resolves the same key.
  EXPECT_EXPRESSION(
      store.Fetch("ghost", "output", ToolTextLanguage::chinese).content ==
      "未知 agent_id: ghost");

  // A running agent answers `include=meta` with the status fields, and the
  // default output mode with the "still running" envelope plus the message.
  store.Put(AgentResultRecord::Running("ag_run", "call_run", "agent", "explore",
                                       "scan the tree", true, 3));
  auto meta = store.Fetch("ag_run", "meta");
  EXPECT_EXPRESSION(!meta.error);
  const auto meta_object = MustParseObject(meta.content);
  EXPECT_EXPRESSION(meta_object.size() == 8U);
  EXPECT_EXPRESSION(StringAt(&meta_object, "agent_id") == "ag_run");
  EXPECT_EXPRESSION(StringAt(&meta_object, "status") == "running");
  EXPECT_EXPRESSION(StringAt(&meta_object, "type") == "explore");
  EXPECT_EXPRESSION(StringAt(&meta_object, "description") == "scan the tree");
  EXPECT_EXPRESSION(StringAt(&meta_object, "preview").empty());
  EXPECT_EXPRESSION(!BoolAt(&meta_object, "error"));
  EXPECT_EXPRESSION(BoolAt(&meta_object, "async"));
  EXPECT_EXPRESSION(json::Find(meta_object, "tool_call_count") != nullptr);
  // `include` is trimmed before the comparison, and meta wins over running.
  EXPECT_EXPRESSION(store.Fetch("ag_run", "  meta  ").content == meta.content);

  auto running_body = store.Fetch("ag_run", "output");
  EXPECT_EXPRESSION(!running_body.error);
  const auto running_object = MustParseObject(running_body.content);
  EXPECT_EXPRESSION(running_object.size() == 7U);
  EXPECT_EXPRESSION(StringAt(&running_object, "agent_id") == "ag_run");
  EXPECT_EXPRESSION(StringAt(&running_object, "message") == kStillRunning);
  EXPECT_EXPRESSION(json::Find(running_object, "error") == nullptr);
  // An absent include defaults to "output".
  EXPECT_EXPRESSION(store.Fetch("ag_run", "").content == running_body.content);

  // A finished agent returns the full output, and falls back to the preview
  // when the full output is blank.
  auto done = AgentResultRecord::Running("ag_done", "call_done", "agent",
                                         "sub-coding", "write it", false, 3)
                  .WithFullOutput("the full body", "", "", 4, false);
  store.Put(done);
  const auto output = store.Fetch("ag_done", "output");
  EXPECT_EXPRESSION(!output.error);
  EXPECT_EXPRESSION(output.content == "the full body");
  EXPECT_EXPRESSION(store.Fetch("ag_done", "meta").content != output.content);

  auto preview_only = application::CreateAgentResultRecord(
      "ag_preview", "call", "agent", "done", "explore", "desc",
      "  the preview  ", "", "", "", 1, false, false, 3, 0);
  store.Put(preview_only);
  const auto preview_body = store.Fetch("ag_preview", "output");
  EXPECT_EXPRESSION(!preview_body.error);
  EXPECT_EXPRESSION(preview_body.content == "the preview");

  // An errored agent returns its body flagged as an error, or the legacy
  // "finished with error" text when there is no body at all.
  auto errored = AgentResultRecord::Running("ag_error", "call_error", "agent",
                                            "explore", "boom", false, 3)
                     .WithFullOutput("partial failure text", "", "", 2, true);
  store.Put(errored);
  const auto error_body = store.Fetch("ag_error", "output");
  EXPECT_EXPRESSION(error_body.error);
  EXPECT_EXPRESSION(error_body.content == "partial failure text");

  auto empty_error = application::CreateAgentResultRecord(
      "ag_empty_error", "call", "agent", "done", "explore", "desc", "", "", "",
      "", 0, true, false, 3, 0);
  store.Put(empty_error);
  const auto empty_error_body = store.Fetch("ag_empty_error", "output");
  EXPECT_EXPRESSION(empty_error_body.error);
  EXPECT_EXPRESSION(empty_error_body.content == kOutputFailed);
  // meta never fails, even for an errored record.
  const auto empty_error_meta = store.Fetch("ag_empty_error", "meta");
  EXPECT_EXPRESSION(!empty_error_meta.error);
  const auto empty_error_meta_object =
      MustParseObject(empty_error_meta.content);
  EXPECT_EXPRESSION(BoolAt(&empty_error_meta_object, "error"));

  auto empty_done = application::CreateAgentResultRecord(
      "ag_empty", "call", "agent", "done", "explore", "desc", "", "", "", "", 0,
      false, false, 3, 0);
  store.Put(empty_done);
  const auto empty_body = store.Fetch("ag_empty", "output");
  EXPECT_EXPRESSION(!empty_body.error);
  EXPECT_EXPRESSION(empty_body.content == kOutputEmpty);

  // A body over the 50KB single-result limit is middle truncated.
  store.Put(application::CreateAgentResultRecord(
      "ag_big", "call", "agent", "done", "explore", "desc", "",
      std::string(60000, 'x'), "", "", 0, false, false, 3, 0));
  const auto big = store.Fetch("ag_big", "output");
  EXPECT_EXPRESSION(!big.error);
  EXPECT_EXPRESSION(big.content.contains("(8800 chars truncated)"));
  EXPECT_EXPRESSION(big.content.size() > 51200U);
  EXPECT_EXPRESSION(big.content.starts_with(std::string(25600, 'x')));
  EXPECT_EXPRESSION(big.content.ends_with(std::string(25600, 'x')));
}

void CompactRefChecks() {
  using application::AgentResultRecord;
  using application::AgentResultRegistry;

  EXPECT_EXPRESSION(AgentResultRegistry::kCompactMarker ==
                    "linecode_agent_ref");

  auto record = AgentResultRecord::Running("ag_7", "call_7", "agent", "explore",
                                           "scan the tree", true, 3)
                    .WithFullOutput("the full transcript", "", "", 6, false);
  const auto compact = AgentResultRegistry::ToCompactJson(record);
  const auto object = MustParseObject(compact);
  EXPECT_EXPRESSION(object.size() == 10U);
  EXPECT_EXPRESSION(BoolAt(&object, "linecode_agent_ref"));
  EXPECT_EXPRESSION(StringAt(&object, "agent_id") == "ag_7");
  EXPECT_EXPRESSION(StringAt(&object, "status") == "done");
  EXPECT_EXPRESSION(StringAt(&object, "type") == "explore");
  EXPECT_EXPRESSION(StringAt(&object, "description") == "scan the tree");
  EXPECT_EXPRESSION(StringAt(&object, "preview") == "the full transcript");
  EXPECT_EXPRESSION(StringAt(&object, "tool_call_id") == "call_7");
  EXPECT_EXPRESSION(BoolAt(&object, "async"));
  EXPECT_EXPRESSION(!BoolAt(&object, "error"));
  // The compact ref never carries the transcript or the progress payload.
  EXPECT_EXPRESSION(json::Find(object, "full_output") == nullptr);
  EXPECT_EXPRESSION(json::Find(object, "thinking") == nullptr);
  EXPECT_EXPRESSION(json::Find(object, "progress_json") == nullptr);

  // The originating tool call id is omitted when there is none.
  auto anonymous = AgentResultRecord::Running("ag_8", "", "agent", "explore",
                                              "scan", false, 3);
  const auto anonymous_object =
      MustParseObject(AgentResultRegistry::ToCompactJson(anonymous));
  EXPECT_EXPRESSION(anonymous_object.size() == 9U);
  EXPECT_EXPRESSION(json::Find(anonymous_object, "tool_call_id") == nullptr);

  // parseCompact() recognizes its own output.
  const auto round_trip = AgentResultRegistry::ParseCompact(compact);
  EXPECT_EXPRESSION(round_trip.has_value());
  EXPECT_EXPRESSION(round_trip->agent_id == "ag_7");
  EXPECT_EXPRESSION(round_trip->tool_call_id == "call_7");
  EXPECT_EXPRESSION(round_trip->status == "done");
  EXPECT_EXPRESSION(round_trip->type == "explore");
  EXPECT_EXPRESSION(round_trip->description == "scan the tree");
  EXPECT_EXPRESSION(round_trip->preview == "the full transcript");
  EXPECT_EXPRESSION(round_trip->tool_call_count == 6);
  EXPECT_EXPRESSION(round_trip->async);
  EXPECT_EXPRESSION(!round_trip->error);
  EXPECT_EXPRESSION(round_trip->full_output.empty());

  // Anything without the marker (or without an id) is not a compact ref.
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact("").has_value());
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact("   ").has_value());
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact("not json").has_value());
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact("[1,2,3]").has_value());
  EXPECT_EXPRESSION(
      !AgentResultRegistry::ParseCompact(R"({"agent_id":"ag_1"})").has_value());
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact(
                         R"({"linecode_agent_ref":false,"agent_id":"ag_1"})")
                         .has_value());
  EXPECT_EXPRESSION(!AgentResultRegistry::ParseCompact(
                         R"({"linecode_agent_ref":true,"agent_id":"  "})")
                         .has_value());
  // Status defaults to "running", like the legacy optString fallback.
  const auto minimal = AgentResultRegistry::ParseCompact(
      R"({"linecode_agent_ref":true,"agent_id":" ag_1 "})");
  EXPECT_EXPRESSION(minimal.has_value());
  EXPECT_EXPRESSION(minimal->agent_id == "ag_1");
  EXPECT_EXPRESSION(minimal->status == "running");
  EXPECT_EXPRESSION(minimal->IsRunning());
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
  EXPECT_EXPRESSION(agents.size() == 2U);
  EXPECT_EXPRESSION(agents[0].id == "a");
  EXPECT_EXPRESSION(agents[0].type == "explore");
  EXPECT_EXPRESSION(agents[0].description == "scan");
  EXPECT_EXPRESSION(agents[0].prompt == "do it");
  EXPECT_EXPRESSION(agents[0].read_scope == std::vector<std::string>{"src"});
  EXPECT_EXPRESSION(agents[0].write_scope.empty());
  EXPECT_EXPRESSION(agents[1].id == "b");
  EXPECT_EXPRESSION(agents[1].type == "sub-coding");
  EXPECT_EXPRESSION(agents[1].write_scope ==
                    std::vector<std::string>{"src/b.cpp"});
  EXPECT_EXPRESSION(agents[1].dependencies == std::vector<std::string>{"a"});

  // Every malformed shape discards the whole list, like the legacy resolver.
  EXPECT_EXPRESSION(ParsePipelineAgents("").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents("not json").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents("{}").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents("[]").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents(R"([1,2])").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents(R"([{"id":"a"},42])").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents(R"([{"type":"explore"}])").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents(R"([{"id":"  "}])").empty());
  EXPECT_EXPRESSION(ParsePipelineAgents(R"([{"id":"a"},{"id":"a"}])").empty());

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
  EXPECT_EXPRESSION(plan.ok());
  EXPECT_EXPRESSION(plan.levels.size() == 3U);
  EXPECT_EXPRESSION(plan.levels[0].size() == 1U);
  EXPECT_EXPRESSION(plan.levels[0][0].id == "root");
  EXPECT_EXPRESSION(plan.levels[1].size() == 2U);
  EXPECT_EXPRESSION(plan.levels[1][0].id == "left");
  EXPECT_EXPRESSION(plan.levels[1][1].id == "right");
  EXPECT_EXPRESSION(plan.levels[2].size() == 1U);
  EXPECT_EXPRESSION(plan.levels[2][0].id == "join");

  // Unknown dependencies and cycles are planner errors, not parse errors.
  const auto unknown = PlanPipeline(
      ParsePipelineAgents(R"([{"id":"a","type":"explore","description":"d",
                               "prompt":"p","depends_on":["ghost"]}])"));
  EXPECT_EXPRESSION(!unknown.ok());
  EXPECT_EXPRESSION(unknown.error.code ==
                    PipelinePlanErrorCode::unknown_dependency);
  EXPECT_EXPRESSION(unknown.error.agent_id == "ghost");

  const auto cycle = PlanPipeline(ParsePipelineAgents(R"([
      {"id":"a","type":"explore","description":"d","prompt":"p",
       "depends_on":["b"]},
      {"id":"b","type":"explore","description":"d","prompt":"p",
       "depends_on":["a"]}
    ])"));
  EXPECT_EXPRESSION(!cycle.ok());
  EXPECT_EXPRESSION(cycle.error.code == PipelinePlanErrorCode::cycle);
}

// ---------------------------------------------------------------------------
// Tool registry probe
// ---------------------------------------------------------------------------

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::
      Lifecycle(
          [scenario, tasks] {
            const auto
                handle = tasks.Launch([scenario]() -> huxerui::
                                                       Task<void> {
                                                         using application::
                                                             ToolRegistryErrorCode;
                                                         using application::
                                                             ToolInvocationResult;
                                                         using application::
                                                             AgentResultRecord;

                                                         // 1. Declarations:
                                                         // three tools, legacy
                                                         // order, legacy flags.
                                                         auto refreshed =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             refreshed);
                                                         EXPECT_EXPRESSION(
                                                             scenario->tools
                                                                 ->Tools()
                                                                 .size() == 3U);
                                                         // Copies, not
                                                         // references: a later
                                                         // Refresh() replaces
                                                         // the whole catalog.
                                                         const application::
                                                             RegisteredTool agent =
                                                                 scenario->tools
                                                                     ->Tools()
                                                                         [0];
                                                         const application::
                                                             RegisteredTool
                                                                 pipeline =
                                                                     scenario
                                                                         ->tools
                                                                         ->Tools()
                                                                             [1];
                                                         const application::
                                                             RegisteredTool
                                                                 agent_output =
                                                                     scenario
                                                                         ->tools
                                                                         ->Tools()
                                                                             [2];
                                                         EXPECT_EXPRESSION(
                                                             agent.name ==
                                                             application::
                                                                 kAgentToolName);
                                                         EXPECT_EXPRESSION(
                                                             agent.name ==
                                                             "agent");
                                                         EXPECT_EXPRESSION(
                                                             pipeline.name ==
                                                             application::
                                                                 kAgentPipelineToolName);
                                                         EXPECT_EXPRESSION(
                                                             pipeline.name ==
                                                             "agent_pipeline");
                                                         EXPECT_EXPRESSION(
                                                             agent_output
                                                                 .name ==
                                                             application::
                                                                 kAgentOutputToolName);
                                                         EXPECT_EXPRESSION(
                                                             agent_output
                                                                 .name ==
                                                             "agent_output");
                                                         EXPECT_EXPRESSION(
                                                             agent
                                                                 .description ==
                                                             kLegacyAgentDescription);
                                                         EXPECT_EXPRESSION(
                                                             pipeline
                                                                 .description ==
                                                             kLegacyPipelineDescription);
                                                         EXPECT_EXPRESSION(
                                                             agent_output
                                                                 .description ==
                                                             kLegacyAgentOutputDescription);
                                                         for (const auto *tool :
                                                              {&agent,
                                                               &pipeline,
                                                               &agent_output}) {
                                                           EXPECT_EXPRESSION(
                                                               tool->category ==
                                                               "agent");
                                                           // All three override
                                                           // isAllowedInReadonlyMode()
                                                           // with true.
                                                           EXPECT_EXPRESSION(
                                                               tool->allowed_in_read_only);
                                                           EXPECT_EXPRESSION(
                                                               !tool->SupportsPermanentGrant());
                                                           EXPECT_EXPRESSION(
                                                               tool->agent_category ==
                                                               application::
                                                                   AgentToolCategory::
                                                                       system);
                                                           EXPECT_EXPRESSION(
                                                               tool->agent_selectable);
                                                         }

                                                         // 2. Schemas:
                                                         // byte-identical to
                                                         // the canonical
                                                         // serialization of the
                                                         // legacy
                                                         // getParameters()
                                                         // result.
                                                         EXPECT_EXPRESSION(
                                                             agent
                                                                 .parameters_json ==
                                                             kLegacyAgentSchema);
                                                         EXPECT_EXPRESSION(
                                                             pipeline
                                                                 .parameters_json ==
                                                             kLegacyPipelineSchema);
                                                         EXPECT_EXPRESSION(
                                                             agent_output
                                                                 .parameters_json ==
                                                             kLegacyAgentOutputSchema);
                                                         // Re-serializing the
                                                         // parsed schema
                                                         // reproduces the
                                                         // literal, which
                                                         // proves the canonical
                                                         // sorted-key form is
                                                         // what the descriptor
                                                         // carries.
                                                         for (const auto *tool :
                                                              {&agent,
                                                               &pipeline,
                                                               &agent_output}) {
                                                           auto parsed = json::Parse(
                                                               tool->parameters_json);
                                                           EXPECT_EXPRESSION(
                                                               parsed);
                                                           EXPECT_EXPRESSION(
                                                               json::Serialize(
                                                                   *parsed) ==
                                                               tool->parameters_json);
                                                         }

                                                         const auto agent_schema =
                                                             MustParseObject(
                                                                 agent
                                                                     .parameters_json);
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &agent_schema,
                                                                 "type") ==
                                                             "object");
                                                         EXPECT_EXPRESSION(
                                                             SchemaPropertyCount(
                                                                 agent_schema) ==
                                                             6U);
                                                         EXPECT_EXPRESSION(
                                                             RequiredNames(
                                                                 agent_schema) ==
                                                             (std::vector<
                                                                 std::string>{
                                                                 "type",
                                                                 "description",
                                                                 "prompt"}));
                                                         const auto
                                                             *agent_properties =
                                                                 ObjectAt(
                                                                     &agent_schema,
                                                                     "propertie"
                                                                     "s");
                                                         const auto *type_property =
                                                             ObjectAt(
                                                                 agent_properties,
                                                                 "type");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 type_property,
                                                                 "type") ==
                                                             "string");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 type_property,
                                                                 "descriptio"
                                                                 "n") ==
                                                             "Agent type: "
                                                             "explore for "
                                                             "read-only "
                                                             "exploration, "
                                                             "sub-coding for "
                                                             "programming "
                                                             "subtasks");
                                                         const auto *type_enum =
                                                             json::AsArray(
                                                                 json::Find(
                                                                     *type_property,
                                                                     "enum"));
                                                         EXPECT_EXPRESSION(
                                                             type_enum !=
                                                             nullptr);
                                                         EXPECT_EXPRESSION(
                                                             type_enum
                                                                 ->size() ==
                                                             2U);
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &type_enum->at(
                                                                     0)) ==
                                                             "explore");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &type_enum->at(
                                                                     1)) ==
                                                             "sub-coding");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 ObjectAt(
                                                                     agent_properties,
                                                                     "descripti"
                                                                     "on"),
                                                                 "descriptio"
                                                                 "n") ==
                                                             "Task title of "
                                                             "3-8 words");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 ObjectAt(
                                                                     agent_properties,
                                                                     "async"),
                                                                 "type") ==
                                                             "boolean");
                                                         for (const auto *name :
                                                              {"read_scope",
                                                               "write_scope"}) {
                                                           const auto *property =
                                                               ObjectAt(
                                                                   agent_properties,
                                                                   name);
                                                           EXPECT_EXPRESSION(
                                                               StringAt(
                                                                   property,
                                                                   "type") ==
                                                               "array");
                                                           EXPECT_EXPRESSION(
                                                               StringAt(
                                                                   ObjectAt(
                                                                       property,
                                                                       "items"),
                                                                   "type") ==
                                                               "string");
                                                         }

                                                         const auto pipeline_schema =
                                                             MustParseObject(
                                                                 pipeline
                                                                     .parameters_json);
                                                         EXPECT_EXPRESSION(
                                                             SchemaPropertyCount(
                                                                 pipeline_schema) ==
                                                             1U);
                                                         EXPECT_EXPRESSION(
                                                             RequiredNames(
                                                                 pipeline_schema) ==
                                                             (std::vector<
                                                                 std::string>{
                                                                 "agents"}));
                                                         const auto
                                                             *agents_property =
                                                                 ObjectAt(
                                                                     ObjectAt(
                                                                         &pipeline_schema,
                                                                         "prope"
                                                                         "rtie"
                                                                         "s"),
                                                                     "agents");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 agents_property,
                                                                 "type") ==
                                                             "array");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 agents_property,
                                                                 "descriptio"
                                                                 "n") ==
                                                             "List of Agent "
                                                             "tasks");
                                                         const auto *item =
                                                             ObjectAt(
                                                                 agents_property,
                                                                 "items");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(item,
                                                                      "type") ==
                                                             "object");
                                                         EXPECT_EXPRESSION(
                                                             SchemaPropertyCount(
                                                                 *item) == 7U);
                                                         EXPECT_EXPRESSION(
                                                             RequiredNames(
                                                                 *item) ==
                                                             (std::vector<
                                                                 std::string>{
                                                                 "id", "type",
                                                                 "description",
                                                                 "prompt"}));

                                                         const auto output_schema =
                                                             MustParseObject(
                                                                 agent_output
                                                                     .parameters_json);
                                                         EXPECT_EXPRESSION(
                                                             SchemaPropertyCount(
                                                                 output_schema) ==
                                                             2U);
                                                         EXPECT_EXPRESSION(
                                                             RequiredNames(
                                                                 output_schema) ==
                                                             (std::vector<
                                                                 std::string>{
                                                                 "agent_id"}));
                                                         const auto
                                                             *include_property =
                                                                 ObjectAt(
                                                                     ObjectAt(
                                                                         &output_schema,
                                                                         "prope"
                                                                         "rtie"
                                                                         "s"),
                                                                     "include");
                                                         const auto *include_enum =
                                                             json::AsArray(
                                                                 json::Find(
                                                                     *include_property,
                                                                     "enum"));
                                                         EXPECT_EXPRESSION(
                                                             include_enum !=
                                                             nullptr);
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &include_enum
                                                                      ->at(
                                                                          0)) ==
                                                             "output");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &include_enum
                                                                      ->at(
                                                                          1)) ==
                                                             "meta");

                                                         // 3. `agent`
                                                         // validation branches
                                                         // (AgentTool.java:87-115).

                                                         // 3a. Type must be
                                                         // explore or
                                                         // sub-coding.
                                                         auto invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"worker","description":"d","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             kInvalidType);
                                                         EXPECT_EXPRESSION(
                                                             scenario->runner
                                                                 ->agent_runs ==
                                                             0);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"","description":"d","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kInvalidType);

                                                         // 3b. explore may not
                                                         // declare a non-empty
                                                         // write scope.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p","write_scope":["src"]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kExploreNoWrite);
                                                         // A whitespace-only or
                                                         // non-array scope is
                                                         // not a declared
                                                         // scope.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p","write_scope":["   "]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error &&
                                                             invoked->content ==
                                                                 "compact-"
                                                                 "agent-ref");
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p","write_scope":"src"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);

                                                         // 3c/3d. description
                                                         // and prompt must not
                                                         // be blank.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"   ","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kDescriptionEmpty);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kDescriptionEmpty);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"  "})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kPromptEmpty);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kPromptEmpty);

                                                         // 3e. An unparseable
                                                         // argument body is the
                                                         // ported "parse
                                                         // failed" branch.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     "not "
                                                                     "json");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content
                                                                 .starts_with(
                                                                     "Agent "
                                                                     "parameter"
                                                                     " parsing "
                                                                     "failed:"
                                                                     " "));
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     "[1,2,3]");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "Agent parameter "
                                                             "parsing failed: "
                                                             "arguments must "
                                                             "be a JSON "
                                                             "object");

                                                         // 3f. Without an
                                                         // engine the tool
                                                         // reports the legacy
                                                         // message.
                                                         scenario->tools
                                                             ->SetRunner(
                                                                 nullptr);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kRunnerUnavailable);
                                                         scenario->tools
                                                             ->SetRunner(
                                                                 scenario
                                                                     ->runner);

                                                         // 3g. A valid call
                                                         // reaches the engine
                                                         // with the normalized
                                                         // request.
                                                         scenario->runner
                                                             ->last_request
                                                             .reset();
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":" Coding ","description":"  write the file  ","prompt":"  do it  ","read_scope":[" src "],"write_scope":[" src/a.cpp "],"async":true})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "compact-agent-"
                                                             "ref");
                                                         EXPECT_EXPRESSION(
                                                             scenario->runner
                                                                 ->agent_runs >=
                                                             1);
                                                         const auto &request =
                                                             *scenario->runner
                                                                  ->last_request;
                                                         EXPECT_EXPRESSION(
                                                             request.type ==
                                                             "sub-coding");
                                                         EXPECT_EXPRESSION(
                                                             request
                                                                 .description ==
                                                             "write the file");
                                                         EXPECT_EXPRESSION(
                                                             request.prompt ==
                                                             "do it");
                                                         EXPECT_EXPRESSION(
                                                             request
                                                                 .read_scope ==
                                                             std::vector<
                                                                 std::string>{
                                                                 "src"});
                                                         EXPECT_EXPRESSION(
                                                             request
                                                                 .write_scope ==
                                                             std::vector<
                                                                 std::string>{
                                                                 "src/a.cpp"});
                                                         EXPECT_EXPRESSION(
                                                             request.async);
                                                         EXPECT_EXPRESSION(
                                                             request.agent_id
                                                                 .empty());

                                                         // 3h. An engine error
                                                         // is surfaced as a
                                                         // tool error with its
                                                         // body.
                                                         scenario->runner
                                                             ->agent_error =
                                                             true;
                                                         scenario->runner
                                                             ->agent_output =
                                                             "agent blew up";
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 "agent blew "
                                                                 "up");
                                                         scenario->runner
                                                             ->agent_error =
                                                             false;
                                                         scenario->runner
                                                             ->agent_output =
                                                             "compact-agent-"
                                                             "ref";

                                                         // 4. `agent_pipeline`
                                                         // validation branches
                                                         // (AgentPipelineTool.java:85-153).

                                                         // 4a. `agents` must be
                                                         // a non-empty array.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     "{}");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kAgentsEmpty);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kAgentsEmpty);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":"nope"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kAgentsEmpty);

                                                         // 4b. Every element
                                                         // must be an object.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"},42]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "agents[1] must "
                                                             "be an object.");

                                                         // 4c. The id must not
                                                         // be blank.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"  ","type":"explore","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "agents[0].id "
                                                             "cannot be "
                                                             "empty.");

                                                         // 4d. Ids must be
                                                         // unique.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"},{"id":" a ","type":"explore","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "Agent id "
                                                             "duplicate: a");

                                                         // 4e. An agent may not
                                                         // depend on itself.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"scout","type":"explore","description":"d","prompt":"p","depends_on":[" scout "]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "Agent cannot "
                                                             "depend on "
                                                             "itself: scout");

                                                         // 4f. The type must be
                                                         // explore or
                                                         // sub-coding.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"worker","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             kPipelineInvalidType);

                                                         // 4g. explore may not
                                                         // write; sub-coding
                                                         // must write.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p","write_scope":["src/a.cpp"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "explore Agent "
                                                             "cannot declare "
                                                             "write_scope and "
                                                             "cannot write "
                                                             "files: a");
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "sub-coding Agent "
                                                             "must declare "
                                                             "write_scope to "
                                                             "avoid multiple "
                                                             "Agents writing "
                                                             "the same file: "
                                                             "a");

                                                         // 4h. The same
                                                         // normalized scope
                                                         // twice inside one
                                                         // agent.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["./src/a.cpp","src//a.cpp"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "Agent a has "
                                                             "duplicate "
                                                             "write_scope: "
                                                             "src//a.cpp");

                                                         // 4i. Two agents may
                                                         // not own equal or
                                                         // enclosing write
                                                         // scopes.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "Multiple Agents "
                                                             "cannot write the "
                                                             "same file or "
                                                             "overlapping "
                                                             "directories: a's "
                                                             "src/a.cpp "
                                                             "conflicts with "
                                                             "b's src/a.cpp. "
                                                             "Please "
                                                             "merge into one "
                                                             "Agent or split "
                                                             "into different "
                                                             "files/"
                                                             "directories.");
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["./src/a.cpp"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content
                                                                 .starts_with(
                                                                     "Multiple "
                                                                     "Agents "
                                                                     "cannot "
                                                                     "write "
                                                                     "the same "
                                                                     "file or "
                                                                     "overlappi"
                                                                     "ng "
                                                                     "directori"
                                                                     "es: a's "
                                                                     "src "
                                                                     "conflicts"
                                                                     " with "
                                                                     "b's "
                                                                     "./src/"
                                                                     "a.cpp."));
                                                         // Unrelated scopes are
                                                         // accepted.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/a.cpp"]},{"id":"b","type":"sub-coding","description":"d","prompt":"p","write_scope":["src/b.cpp"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "compact-pipeline-"
                                                             "ref");

                                                         // 4j. description and
                                                         // prompt are checked
                                                         // after the scope
                                                         // rules.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"  ","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "agents[0]."
                                                             "description "
                                                             "cannot be "
                                                             "empty.");
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":" "}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "agents[0].prompt "
                                                             "cannot be "
                                                             "empty.");
                                                         // A broken scope of
                                                         // the same agent is
                                                         // reported first.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"sub-coding","description":"","prompt":""}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "sub-coding Agent "
                                                             "must declare "
                                                             "write_scope to "
                                                             "avoid multiple "
                                                             "Agents writing "
                                                             "the same file: "
                                                             "a");

                                                         // 4k. Without an
                                                         // engine the tool
                                                         // reports the legacy
                                                         // message.
                                                         scenario->tools
                                                             ->SetRunner(
                                                                 nullptr);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kPipelineRunnerUnavailable);
                                                         scenario->tools
                                                             ->SetRunner(
                                                                 scenario
                                                                     ->runner);

                                                         // 4l. A valid pipeline
                                                         // reaches the engine
                                                         // with the parsed
                                                         // agents.
                                                         scenario->runner
                                                             ->last_pipeline
                                                             .reset();
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"root","type":"explore","description":"scan","prompt":"scan it","read_scope":[" src "]},{"id":"leaf","type":"coding","description":"write","prompt":"write it","write_scope":[" src/leaf.cpp "],"depends_on":["root"]}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "compact-pipeline-"
                                                             "ref");
                                                         const auto &agents =
                                                             scenario->runner
                                                                 ->last_pipeline
                                                                 ->agents;
                                                         EXPECT_EXPRESSION(
                                                             agents.size() ==
                                                             2U);
                                                         EXPECT_EXPRESSION(
                                                             agents[0].id ==
                                                             "root");
                                                         EXPECT_EXPRESSION(
                                                             agents[0].type ==
                                                             "explore");
                                                         EXPECT_EXPRESSION(
                                                             agents[0]
                                                                 .read_scope ==
                                                             std::vector<
                                                                 std::string>{
                                                                 "src"});
                                                         EXPECT_EXPRESSION(
                                                             agents[1].id ==
                                                             "leaf");
                                                         EXPECT_EXPRESSION(
                                                             agents[1].type ==
                                                             "sub-coding");
                                                         EXPECT_EXPRESSION(
                                                             agents[1]
                                                                 .write_scope ==
                                                             std::vector<
                                                                 std::string>{
                                                                 "src/"
                                                                 "leaf.cpp"});
                                                         EXPECT_EXPRESSION(
                                                             agents[1]
                                                                 .dependencies ==
                                                             std::vector<
                                                                 std::string>{
                                                                 "root"});

                                                         // 4m. An engine error
                                                         // is surfaced as a
                                                         // tool error with its
                                                         // body.
                                                         scenario->runner
                                                             ->pipeline_error =
                                                             true;
                                                         scenario->runner
                                                             ->pipeline_output =
                                                             "pipeline blew up";
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "pipeline",
                                                                     R"({"agents":[{"id":"a","type":"explore","description":"d","prompt":"p"}]})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 "pipeline "
                                                                 "blew up");
                                                         scenario->runner
                                                             ->pipeline_error =
                                                             false;
                                                         scenario->runner
                                                             ->pipeline_output =
                                                             "compact-pipeline-"
                                                             "ref";

                                                         // 5. `agent_output`
                                                         // through the tool,
                                                         // both include modes.
                                                         auto stored =
                                                             scenario->results
                                                                 ->GetRecord(
                                                                     "ag_tool_"
                                                                     "done");
                                                         EXPECT_EXPRESSION(
                                                             !stored
                                                                  .has_value());
                                                         scenario->results->Put(
                                                             AgentResultRecord::
                                                                 Running(
                                                                     "ag_tool_"
                                                                     "done",
                                                                     "call_1",
                                                                     "agent",
                                                                     "explore",
                                                                     "scan the "
                                                                     "tree",
                                                                     false, 1)
                                                                     .WithFullOutput(
                                                                         "the "
                                                                         "full "
                                                                         "trans"
                                                                         "crip"
                                                                         "t",
                                                                         "", "",
                                                                         3,
                                                                         false));

                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "output",
                                                                     R"({"agent_id":" "})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kIdMissing);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "output",
                                                                     "{}");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kIdMissing);
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "output",
                                                                     R"({"agent_id":"ghost"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             invoked->error &&
                                                             invoked->content ==
                                                                 kNotFound);

                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "output",
                                                                     R"({"agent_id":"ag_tool_done"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);
                                                         EXPECT_EXPRESSION(
                                                             invoked->content ==
                                                             "the full "
                                                             "transcript");

                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent_"
                                                                     "output",
                                                                     R"({"agent_id":"ag_tool_done","include":"meta"})");
                                                         EXPECT_EXPRESSION(
                                                             invoked &&
                                                             !invoked->error);
                                                         const auto tool_meta =
                                                             MustParseObject(
                                                                 invoked
                                                                     ->content);
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &tool_meta,
                                                                 "agent_id") ==
                                                             "ag_tool_done");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &tool_meta,
                                                                 "status") ==
                                                             "done");
                                                         EXPECT_EXPRESSION(
                                                             StringAt(
                                                                 &tool_meta,
                                                                 "preview") ==
                                                             "the full "
                                                             "transcript");
                                                         EXPECT_EXPRESSION(
                                                             json::Find(
                                                                 tool_meta,
                                                                 "message") ==
                                                             nullptr);

                                                         // A null store
                                                         // reproduces the
                                                         // legacy "store is not
                                                         // available" branch.
                                                         auto orphan =
                                                             std::make_shared<
                                                                 application::
                                                                     AgentToolRegistry>(
                                                                 scenario->mcp,
                                                                 nullptr,
                                                                 scenario
                                                                     ->runner);
                                                         auto orphan_refresh =
                                                             co_await orphan
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             orphan_refresh);
                                                         auto orphan_invoked =
                                                             co_await orphan->Invoke(
                                                                 "agent_output",
                                                                 R"({"agent_id":"ag_tool_done"})");
                                                         EXPECT_EXPRESSION(
                                                             orphan_invoked &&
                                                             orphan_invoked
                                                                 ->error &&
                                                             orphan_invoked
                                                                     ->content ==
                                                                 kStoreMissing);

                                                         // 6. Group gating.
                                                         Group(scenario->mcp,
                                                               "agent")
                                                             .enabled = false;
                                                         auto disabled =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             disabled);
                                                         EXPECT_EXPRESSION(
                                                             scenario->tools
                                                                 ->Tools()
                                                                 .empty());
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "agent",
                                                                     R"({"type":"explore","description":"d","prompt":"p"})");
                                                         EXPECT_EXPRESSION(
                                                             !invoked);
                                                         EXPECT_EXPRESSION(
                                                             invoked.error()
                                                                 .code ==
                                                             ToolRegistryErrorCode::
                                                                 unavailable);
                                                         // A tool that is not
                                                         // part of this
                                                         // registry is always
                                                         // unknown.
                                                         invoked =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Invoke(
                                                                     "todo_"
                                                                     "update",
                                                                     "{}");
                                                         EXPECT_EXPRESSION(
                                                             !invoked);
                                                         EXPECT_EXPRESSION(
                                                             invoked.error()
                                                                 .code ==
                                                             ToolRegistryErrorCode::
                                                                 unknown_tool);

                                                         // The agent group
                                                         // supports every
                                                         // execution mode; a
                                                         // narrowed mask hides
                                                         // the whole group.
                                                         Group(scenario->mcp,
                                                               "agent")
                                                             .enabled = true;
                                                         Group(scenario->mcp,
                                                               "agent")
                                                             .supported_modes =
                                                             domain::
                                                                 McpExecutionModeMask::
                                                                     local;
                                                         scenario->mcp->value
                                                             .mode = domain::
                                                             McpExecutionMode::
                                                                 ssh;
                                                         auto unsupported =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             unsupported);
                                                         EXPECT_EXPRESSION(
                                                             scenario->tools
                                                                 ->Tools()
                                                                 .empty());
                                                         scenario->mcp->value
                                                             .mode = domain::
                                                             McpExecutionMode::
                                                                 local;
                                                         Group(scenario->mcp,
                                                               "agent")
                                                             .supported_modes =
                                                             domain::
                                                                 McpExecutionModeMask::
                                                                     all;
                                                         auto supported =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             supported);
                                                         EXPECT_EXPRESSION(
                                                             scenario->tools
                                                                 ->Tools()
                                                                 .size() == 3U);

                                                         // 7. Settings failures
                                                         // propagate.
                                                         scenario->mcp
                                                             ->fail_load = true;
                                                         auto failed =
                                                             co_await scenario
                                                                 ->tools
                                                                 ->Refresh();
                                                         EXPECT_EXPRESSION(
                                                             !failed);
                                                         EXPECT_EXPRESSION(
                                                             failed.error()
                                                                 .code ==
                                                             ToolRegistryErrorCode::
                                                                 load_failed);
                                                         EXPECT_EXPRESSION(
                                                             failed.error()
                                                                 .message
                                                                 .contains(
                                                                     "injecte"
                                                                     "d"));
                                                         scenario->mcp
                                                             ->fail_load =
                                                             false;

                                                         scenario->done = true;
                                                       });
            return [handle] { handle.Cancel(); };
          });
  return huxerui::Text("agent-tool-registry-probe");
}

} // namespace

TEST(agent_tool_tests, LegacySuite) {
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
  EXPECT_EXPRESSION(rejected_settings);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  ui.PumpUntil([] { return active->done; });
  EXPECT_EXPRESSION(active->done);
  active.reset();

  std::cout << "agent_tool_tests passed\n";
  return;
}
