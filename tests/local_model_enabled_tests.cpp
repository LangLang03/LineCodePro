#include "gtest_support.h"

#include <string>
#include <vector>

#include "application/local_model_device.h"
#include "application/local_model_prompt.h"
#include "application/local_model_support.h"
#include "application/model_form_service.h"
#include "domain/model_config.h"
#include "presentation/model_form_presentation.h"

namespace {

using linecode::application::BuildLocalModelTurns;
using linecode::application::BuildLocalToolInstruction;
using linecode::application::CompletionMessage;
using linecode::application::CompletionRequest;
using linecode::application::CompletionRole;
using linecode::application::CompletionTool;
using linecode::application::CompletionToolCall;
using linecode::application::CompletionToolResult;
using linecode::application::ExtractLocalToolCalls;
using linecode::application::ModelDraft;
using linecode::application::ModelFormService;
using linecode::application::ModelValidationCode;
using linecode::domain::LocalAcceleration;
using linecode::domain::ModelProtocol;
using linecode::presentation::LocalModelProtocolSelectable;
using linecode::presentation::ProtocolTabAction;
using linecode::presentation::ResolveProtocolTabDecision;

CompletionTool EchoTool() {
  return CompletionTool{.name = "read_file",
                        .description = "Read a file",
                        .parameters_json = R"({"type":"object"})",
                        .allowed_in_read_only = true};
}

} // namespace

// This target is compiled with the same definitions a `-DLINCODE_ENABLE_LOCAL_MODEL=ON`
// build uses, so the two branches of the feature switch are both covered by the
// suite rather than only the default one.
TEST(local_model_enabled_tests, BuildWithTheBackendReportsWhatItLinked) {
  EXPECT_EXPRESSION(linecode::application::local_model_enabled);
  EXPECT_EXPRESSION(linecode::application::LocalModelEngineName() ==
                    "llama.cpp");

  const auto ids = linecode::application::EnabledLocalModelBackendIds();
  const std::vector<std::string_view> expected{"vulkan", "opencl"};
  EXPECT_EXPRESSION(ids == expected);
  // No NPU backend exists in this build, and the shipped decision says so.
  EXPECT_EXPRESSION(!linecode::application::local_model_npu_supported);
  EXPECT_EXPRESSION(linecode::application::local_model_backend_count == 4U);

  const auto availability =
      linecode::application::BuildLocalAccelerationAvailability();
  EXPECT_EXPRESSION(availability.gpu);
  EXPECT_EXPRESSION(linecode::application::LocalModelAccelerationCompiled(
      LocalAcceleration::cpu));
  EXPECT_EXPRESSION(linecode::application::LocalModelAccelerationCompiled(
      LocalAcceleration::gpu));
  EXPECT_EXPRESSION(linecode::application::LocalModelAccelerationCompiled(
      LocalAcceleration::automatic));
  // Backends this build did not link are still reported accurately rather than
  // assumed present.
  EXPECT_EXPRESSION(!linecode::application::LocalModelBackendCompiled(
      linecode::application::LocalModelBackend::cuda));
  EXPECT_EXPRESSION(!linecode::application::LocalModelBackendCompiled(
      linecode::application::LocalModelBackend::metal));

  const auto selectable = linecode::application::SelectableLocalAccelerations();
  const std::vector<LocalAcceleration> expected_accelerations{
      LocalAcceleration::automatic, LocalAcceleration::cpu,
      LocalAcceleration::gpu};
  EXPECT_EXPRESSION(selectable == expected_accelerations);
}

TEST(local_model_enabled_tests, LocalEntryPointsAreAvailable) {
  EXPECT_EXPRESSION(LocalModelProtocolSelectable());
  const auto enter = ResolveProtocolTabDecision(ModelProtocol::openai_compatible,
                                               ModelProtocol::local_gguf, false);
  EXPECT_EXPRESSION(enter.enabled);
  EXPECT_EXPRESSION(enter.action == ProtocolTabAction::prompt_local_entry);
  const auto leave = ResolveProtocolTabDecision(ModelProtocol::local_gguf,
                                                ModelProtocol::openai_compatible,
                                                false);
  EXPECT_EXPRESSION(leave.enabled);
  EXPECT_EXPRESSION(leave.action == ProtocolTabAction::prompt_custom_entry);
}

TEST(local_model_enabled_tests, LocalDraftRequiresAnImportedFile) {
  auto draft = ModelFormService::New(std::nullopt, true);
  draft.context_size = "8192";
  draft.local_acceleration = LocalAcceleration::gpu;

  const auto incomplete = ModelFormService::Build(draft);
  EXPECT_EXPRESSION(!incomplete.has_value());
  EXPECT_EXPRESSION(incomplete.error().code ==
                    ModelValidationCode::missing_local_model_path);

  draft.local_model_path = "/models/Qwen2.5-7B-Instruct-Q4_K_M.gguf";
  const auto built = ModelFormService::Build(draft);
  EXPECT_EXPRESSION(built.has_value());
  if (built.has_value()) {
    EXPECT_EXPRESSION(built->protocol == ModelProtocol::local_gguf);
    EXPECT_EXPRESSION(built->local_model_path ==
                      "/models/Qwen2.5-7B-Instruct-Q4_K_M.gguf");
    EXPECT_EXPRESSION(built->local_acceleration == LocalAcceleration::gpu);
    EXPECT_EXPRESSION(built->context_size == 8192);
    // A local model needs no API key and has no endpoint.
    EXPECT_EXPRESSION(built->api_key.empty());
    EXPECT_EXPRESSION(built->base_url.empty());
    // The model ID is derived from the file name so the saved model can be
    // referenced by the conversation history.
    EXPECT_EXPRESSION(built->model_id == "Qwen2.5-7B-Instruct-Q4_K_M");
    // A local GGUF model never drives a second compression model.
    EXPECT_EXPRESSION(!built->compression_model_enabled);
  }
  EXPECT_EXPRESSION(ModelFormService::CanSave(draft));

  // Editing returns the persisted local state, which is what makes the
  // acceleration choice survive a restart.
  const auto edited = ModelFormService::Edit(*built);
  EXPECT_EXPRESSION(edited.local);
  EXPECT_EXPRESSION(edited.local_model_path == built->local_model_path);
  EXPECT_EXPRESSION(edited.local_acceleration == LocalAcceleration::gpu);
}

TEST(local_model_enabled_tests, ToolResultsAndCallsAreRenderedAsKnownRoles) {
  CompletionRequest request;
  request.tools = {EchoTool()};
  request.messages = {
      CompletionMessage{.role = CompletionRole::system,
                        .content = "You are a coding agent."},
      CompletionMessage{.role = CompletionRole::user, .content = "read it"},
      CompletionMessage{
          .role = CompletionRole::assistant,
          .content = "Sure.",
          .tool_calls = {CompletionToolCall{
              .id = "call-1",
              .name = "read_file",
              .arguments_json = R"({"path":"/a.txt"})"}},
      },
      CompletionMessage{.role = CompletionRole::tool,
                        .content = {},
                        .tool_result = CompletionToolResult{.call_id = "call-1",
                                                            .name = "read_file",
                                                            .content = "hello"}},
  };

  const auto turns = BuildLocalModelTurns(request);
  EXPECT_EXPRESSION(turns.size() == 4U);
  EXPECT_EXPRESSION(turns[0].role == "system");
  EXPECT_EXPRESSION(turns[1].role == "user");
  EXPECT_EXPRESSION(turns[2].role == "assistant");
  // Local templates only understand system, user and assistant, so a tool
  // result has to arrive as a user turn that names the call it answers.
  EXPECT_EXPRESSION(turns[3].role == "user");
  EXPECT_EXPRESSION(turns[3].content.find("read_file") != std::string::npos);
  EXPECT_EXPRESSION(turns[3].content.find("hello") != std::string::npos);
  // The tool instruction is folded into the existing system turn instead of
  // adding a second one, which templates with a single system slot reject.
  EXPECT_EXPRESSION(turns[0].content.find("You are a coding agent.") == 0U);
  EXPECT_EXPRESSION(turns[0].content.find("tool_calls") != std::string::npos);
  EXPECT_EXPRESSION(turns[0].content.find("read_file") != std::string::npos);
  // The previous call is echoed back so the model does not re-request it.
  EXPECT_EXPRESSION(turns[2].content.find("tool_calls") != std::string::npos);
  EXPECT_EXPRESSION(turns[2].content.find("/a.txt") != std::string::npos);
}

TEST(local_model_enabled_tests, ToolInstructionIsInjectedWhenThereIsNoSystemTurn) {
  CompletionRequest request;
  request.tools = {EchoTool()};
  request.messages = {CompletionMessage{.role = CompletionRole::user,
                                        .content = "read it"}};
  const auto turns = BuildLocalModelTurns(request);
  EXPECT_EXPRESSION(turns.size() == 2U);
  EXPECT_EXPRESSION(turns.front().role == "system");
  EXPECT_EXPRESSION(turns.front().content.find("read_file") !=
                    std::string::npos);
  EXPECT_EXPRESSION(turns.back().role == "user");
}

TEST(local_model_enabled_tests, PlainChatGetsNoToolInstruction) {
  EXPECT_EXPRESSION(BuildLocalToolInstruction({}).empty());
  CompletionRequest request;
  request.messages = {CompletionMessage{.role = CompletionRole::user,
                                        .content = "hello"}};
  const auto turns = BuildLocalModelTurns(request);
  EXPECT_EXPRESSION(turns.size() == 1U);
  EXPECT_EXPRESSION(turns[0].role == "user");
}

TEST(local_model_enabled_tests, ToolEnvelopesAreExtractedFromBareJson) {
  const auto extraction = ExtractLocalToolCalls(
      R"({"tool_calls":[{"name":"read_file","arguments":{"path":"/a.txt"}}]})");
  EXPECT_EXPRESSION(extraction.calls.size() == 1U);
  EXPECT_EXPRESSION(extraction.text.empty());
  if (extraction.calls.size() == 1U) {
    EXPECT_EXPRESSION(extraction.calls[0].name == "read_file");
    EXPECT_EXPRESSION(extraction.calls[0].arguments_json.find("/a.txt") !=
                      std::string::npos);
    // The tool loop correlates results by call id, so it must be non-empty.
    EXPECT_EXPRESSION(!extraction.calls[0].id.empty());
  }
}

TEST(local_model_enabled_tests, ToolEnvelopesAreExtractedFromAFencedBlock) {
  const auto extraction = ExtractLocalToolCalls(
      "I will read that file.\n\n```json\n"
      R"({"tool_calls":[{"name":"read_file","arguments":"{\"path\":\"/b\"}"}]})"
      "\n```\n");
  EXPECT_EXPRESSION(extraction.calls.size() == 1U);
  // The prose around the call is kept, and the fence markers go with it.
  EXPECT_EXPRESSION(extraction.text == "I will read that file.");
  if (extraction.calls.size() == 1U) {
    EXPECT_EXPRESSION(extraction.calls[0].arguments_json == R"({"path":"/b"})");
  }
}

TEST(local_model_enabled_tests, AnEmptyEnvelopeIsConsumedAndLeavesNoProse) {
  const auto extraction =
      ExtractLocalToolCalls(R"(thinking... {"tool_calls":[]})");
  EXPECT_EXPRESSION(extraction.calls.empty());
  EXPECT_EXPRESSION(extraction.text == "thinking...");
}

// A reply that merely mentions braces must never be read as a tool request:
// acting on a guess would run a tool the model never asked for.
TEST(local_model_enabled_tests, ProseAndMalformedJsonAreNotToolCalls) {
  const auto prose = ExtractLocalToolCalls("Use {\"name\": \"x\"} shape.");
  EXPECT_EXPRESSION(prose.calls.empty());
  EXPECT_EXPRESSION(prose.text == "Use {\"name\": \"x\"} shape.");

  const auto truncated =
      ExtractLocalToolCalls(R"({"tool_calls":[{"name":"read_file")");
  EXPECT_EXPRESSION(truncated.calls.empty());

  const auto unnamed =
      ExtractLocalToolCalls(R"({"tool_calls":[{"arguments":{}}]})");
  EXPECT_EXPRESSION(unnamed.calls.empty());

  const auto other = ExtractLocalToolCalls(R"({"answer":"42"})");
  EXPECT_EXPRESSION(other.calls.empty());
  EXPECT_EXPRESSION(other.text == R"({"answer":"42"})");
}

TEST(local_model_enabled_tests, MultipleCallsKeepStableDistinctIds) {
  const auto extraction = ExtractLocalToolCalls(
      R"({"tool_calls":[{"name":"a","arguments":{}},{"name":"b","arguments":{}}]})");
  EXPECT_EXPRESSION(extraction.calls.size() == 2U);
  if (extraction.calls.size() == 2U) {
    EXPECT_EXPRESSION(extraction.calls[0].name == "a");
    EXPECT_EXPRESSION(extraction.calls[1].name == "b");
    EXPECT_EXPRESSION(extraction.calls[0].id != extraction.calls[1].id);
  }
}

TEST(local_model_enabled_tests, NoNpuBackendIsOffered) {
  // The enabled build offers every accelerator it can run, and no more: NPU is
  // parked, so it is absent from the selectable list rather than present and
  // failing.
  const auto selectable = linecode::application::SelectableLocalAccelerations();
  EXPECT_EXPRESSION(selectable.size() == 3U);
  for (const auto acceleration : selectable) {
    EXPECT_EXPRESSION(acceleration != LocalAcceleration::count);
  }
}
