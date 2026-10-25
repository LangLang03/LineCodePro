#include "gtest_support.h"

#include <chrono>
#include <cstdlib>
#include <memory>
#include <optional>
#include <string>
#include <string_view>
#include <thread>
#include <utility>

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
                    scenario->real_text += delta->text;
                  }
                },
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
  }
}
