#include "gtest_support.h"

#include <chrono>
#include <cstdlib>
#include <filesystem>
#include <fstream>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/local_model_support.h"
#include "application/ports/completion_gateway.h"
#include "domain/model_config.h"
#include "infrastructure/llama_local_model_engine.h"
#include "infrastructure/local_model_completion_gateway.h"

namespace {

using linecode::application::CompletionErrorCode;
using linecode::application::CompletionObserver;
using linecode::application::CompletionRequest;
using linecode::application::CompletionResponse;
using linecode::application::CompletionRole;
using linecode::application::LocalChatTurn;
using linecode::application::LocalGenerationRequest;
using linecode::application::LocalGenerationResult;
using linecode::domain::LocalAcceleration;
using linecode::domain::ModelProtocol;
using linecode::infrastructure::LlamaLocalModelEngine;
using linecode::infrastructure::LocalModelCompletionGateway;

// Stands in for the HTTP gateway so a test can tell which of the two the
// decorator picked without opening a socket.
class RecordingRemoteGateway final
    : public linecode::application::CompletionGateway {
public:
  std::size_t calls{};

  huxerui::Task<std::expected<CompletionResponse,
                              linecode::application::CompletionError>>
  Complete(CompletionRequest request, CompletionObserver) override {
    ++calls;
    co_return CompletionResponse{.text = "remote:" + request.model.model_id,
                                 .tool_calls = {}};
  }
};

struct Scenario final {
  std::shared_ptr<LlamaLocalModelEngine> engine;
  std::shared_ptr<RecordingRemoteGateway> remote;
  std::shared_ptr<LocalModelCompletionGateway> gateway;
  std::optional<std::expected<CompletionResponse,
                              linecode::application::CompletionError>>
      local_result;
  std::optional<std::expected<CompletionResponse,
                              linecode::application::CompletionError>>
      remote_result;
  std::optional<std::expected<CompletionResponse,
                              linecode::application::CompletionError>>
      real_result;
  std::string streamed;
  std::string real_text;
  bool load_finished{};
  bool piece_before_finish{};
  int load_finishes{};
  int activity_reports{};
  bool done{};
};

std::shared_ptr<Scenario> active;

CompletionRequest LocalRequest(std::string path) {
  CompletionRequest request;
  request.model.protocol = ModelProtocol::local_gguf;
  request.model.local_model_path = std::move(path);
  request.model.context_size = 512;
  request.model.local_acceleration = LocalAcceleration::automatic;
  request.messages.push_back({.role = CompletionRole::user,
                              .content = "hello"});
  return request;
}

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      // A local model with a path that does not exist exercises the whole
      // decorator path -- routing, engine call, error propagation -- without
      // needing weights or touching the network.
      scenario->local_result = co_await scenario->gateway->Complete(
          LocalRequest("/nonexistent/linecode-test-model.gguf"),
          CompletionObserver{
              .on_event = [scenario](const auto &event) {
                if (const auto *delta =
                        std::get_if<linecode::application::CompletionTextDelta>(
                            &event)) {
                  scenario->streamed += delta->text;
                }
              },
              .on_tool_review = {},
          });

      CompletionRequest remote_request;
      remote_request.model.protocol = ModelProtocol::openai_compatible;
      remote_request.model.base_url = "https://example.com/v1";
      remote_request.model.api_key = "secret";
      remote_request.model.model_id = "gpt-fixture";
      remote_request.messages.push_back(
          {.role = CompletionRole::user, .content = "hello"});
      scenario->remote_result = co_await scenario->gateway->Complete(
          std::move(remote_request), CompletionObserver{});

      // Real generation only runs when a model is supplied, because this suite
      // intentionally ships without weights. With the variable set the decode
      // loop, the chat template and the streaming projection are all exercised
      // for real.
      if (const auto *model = std::getenv("LINCODE_TEST_GGUF_MODEL");
          model != nullptr && *model != '\0') {
        scenario->real_result = co_await scenario->gateway->Complete(
            LocalRequest(model),
            CompletionObserver{
                .on_event = [scenario](const auto &event) {
                  if (const auto *delta = std::get_if<
                          linecode::application::CompletionTextDelta>(&event)) {
                    // A piece can only exist after the weights do; a piece that
                    // arrives before the load window closed would mean the
                    // status row is still claiming to load while text streams.
                    if (!scenario->load_finished) {
                      scenario->piece_before_finish = true;
                    }
                    scenario->real_text += delta->text;
                  }
                },
                .on_load_finished = [scenario] {
                  scenario->load_finished = true;
                  ++scenario->load_finishes;
                },
                .on_activity = [scenario] { ++scenario->activity_reports; },
                .on_tool_review = {},
            });
      }

      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("llama-local-model-probe");
}

} // namespace

TEST(llama_local_model_engine_tests, EnumeratesRealDevices) {
  LlamaLocalModelEngine engine;
  EXPECT_EXPRESSION(engine.Available());

  const auto runtime = engine.RuntimeInfo();
  EXPECT_EXPRESSION(runtime.engine == "llama.cpp");
  EXPECT_EXPRESSION(runtime.available);
  EXPECT_EXPRESSION(!runtime.devices.empty());

  bool has_cpu = false;
  for (const auto &device : runtime.devices) {
    if (device.acceleration == LocalAcceleration::cpu) {
      has_cpu = true;
    }
    // Every device is either a GPU or a CPU: with the NPU parked there is no
    // third mode to report, and a BLAS library must not be promoted either.
    EXPECT_EXPRESSION(device.acceleration == LocalAcceleration::cpu ||
                      device.acceleration == LocalAcceleration::gpu);
  }
  EXPECT_EXPRESSION(has_cpu);
}

TEST(llama_local_model_engine_tests, RoutesLocalModelsAndLeavesRemoteOnesAlone) {
  active = std::make_shared<Scenario>();
  active->engine = std::make_shared<LlamaLocalModelEngine>();
  active->remote = std::make_shared<RecordingRemoteGateway>();
  active->gateway = std::make_shared<LocalModelCompletionGateway>(
      active->remote, active->engine);

  const huxerui::Application application(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  const auto *model = std::getenv("LINCODE_TEST_GGUF_MODEL");
  const bool real_generation = model != nullptr && *model != '\0';
  if (real_generation) {
    // `PumpUntil` bounds *virtual* time and does not wait for external workers,
    // so a predicate that returns immediately would burn the frame budget while
    // the decode loop is still running on its worker. Sleeping in the predicate
    // is what gives that worker real time while the harness keeps draining the
    // runtime queue, which is the only way real inference can complete here.
    ui.PumpUntil(
        [] {
          std::this_thread::sleep_for(std::chrono::milliseconds(20));
          return active->done;
        },
        {.timeout = std::chrono::minutes(30),
         .step = std::chrono::milliseconds(16),
         .maximum_frames = 200000});
  } else {
    ui.PumpUntil([] { return active->done; });
  }

  // A local model is answered in process, so the HTTP gateway is never called
  // and the failure is the engine's own report about the missing file.
  EXPECT_EXPRESSION(active->local_result.has_value());
  EXPECT_EXPRESSION(!active->local_result->has_value());
  if (active->local_result.has_value() && !active->local_result->has_value()) {
    EXPECT_EXPRESSION(active->local_result->error().code ==
                      CompletionErrorCode::invalid_configuration);
    EXPECT_EXPRESSION(active->local_result->error().message.find(
                          "linecode-test-model.gguf") != std::string::npos);
  }

  // A remote protocol still goes to the HTTP gateway untouched.
  EXPECT_EXPRESSION(active->remote->calls == 1U);
  EXPECT_EXPRESSION(active->remote_result.has_value());
  if (active->remote_result.has_value() &&
      active->remote_result->has_value()) {
    EXPECT_EXPRESSION(active->remote_result->value().text ==
                      "remote:gpt-fixture");
  }
  // Without LINCODE_TEST_GGUF_MODEL there is nothing to generate from, so the
  // absence of a result is the expected outcome rather than a skipped
  // assertion.
  if (!real_generation) {
    EXPECT_EXPRESSION(!active->real_result.has_value());
    return;
  }
  EXPECT_EXPRESSION(active->real_result.has_value());
  if (active->real_result.has_value() && active->real_result->has_value()) {
    const auto &response = active->real_result->value();
    EXPECT_EXPRESSION(response.output_tokens > 0);
    EXPECT_EXPRESSION(response.input_tokens > 0);
    EXPECT_EXPRESSION(!response.text.empty());
    // Streaming and the final response have to agree, because the caller
    // replaces the streamed bubble with `response.text`.
    EXPECT_EXPRESSION(active->real_text == response.text);
    // The load window closed exactly once, before any text existed, and the
    // decode loop kept the turn alive while it worked.
    EXPECT_EXPRESSION(active->load_finishes == 1);
    EXPECT_EXPRESSION(!active->piece_before_finish);
    EXPECT_EXPRESSION(active->activity_reports > 0);
  }
}

namespace {

// Observes the window a local generation opens while it reads weights and
// closes once they are in memory. A file that exists but is not a GGUF reaches
// the load without needing weights, which is the only failure path where the
// engine itself -- not the caller's fallback -- has to close the window.
struct LoadWindowScenario final {
  std::shared_ptr<LlamaLocalModelEngine> engine;
  std::string path;
  std::vector<int> percentages;
  int finished{};
  int activity_reports{};
  bool done{};
  std::optional<std::expected<LocalGenerationResult,
                              linecode::application::CompletionError>>
      result;
};

std::shared_ptr<LoadWindowScenario> load_window;

huxerui::View LoadWindowProbe() {
  const auto scenario = load_window;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    const auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      LocalGenerationRequest request{
          .model_path = scenario->path,
          .messages = {LocalChatTurn{.role = "user", .content = "hello"}},
          .context_size = 512,
          .acceleration = LocalAcceleration::automatic,
          .max_tokens = 0,
          .chat_template = {},
          .on_load_progress =
              [scenario](int percent) {
                scenario->percentages.push_back(percent);
              },
          .on_load_finished = [scenario] { scenario->finished += 1; },
          .on_activity = [scenario] { scenario->activity_reports += 1; },
      };
      scenario->result =
          co_await scenario->engine->Generate(std::move(request), {});
      scenario->done = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("llama-load-window-probe");
}

} // namespace

TEST(llama_local_model_engine_tests, ClosesTheLoadWindowWhenLoadingFails) {
  const auto path =
      std::filesystem::temp_directory_path() / "linecode-not-a-model.gguf";
  {
    std::ofstream file(path, std::ios::binary);
    file << "not a gguf file";
  }

  auto scenario = std::make_shared<LoadWindowScenario>();
  scenario->engine = std::make_shared<LlamaLocalModelEngine>();
  scenario->path = path.string();
  load_window = scenario;

  const huxerui::Application application(LoadWindowProbe,
                                        {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(application);
  // PumpUntil bounds virtual time only, so the predicate has to yield real time
  // to the worker that is failing the load.
  ui.PumpUntil(
      [] {
        std::this_thread::sleep_for(std::chrono::milliseconds(5));
        return load_window->done;
      },
      {.timeout = std::chrono::seconds(60),
       .step = std::chrono::milliseconds(16),
       .maximum_frames = 5000});

  EXPECT_EXPRESSION(scenario->result.has_value());
  if (scenario->result.has_value()) {
    EXPECT_EXPRESSION(!scenario->result->has_value());
  }
  // The load window closed exactly once, even though the weights never arrived:
  // a status row keyed on this callback goes back to "working" instead of
  // sitting on "loading the model" for the rest of the turn.
  EXPECT_EXPRESSION(scenario->finished == 1);
  // The failed load never decoded anything, so there is no slice to report.
  EXPECT_EXPRESSION(scenario->activity_reports == 0);

  std::error_code ignored;
  std::filesystem::remove(path, ignored);
}
