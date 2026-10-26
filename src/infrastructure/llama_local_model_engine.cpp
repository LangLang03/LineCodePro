#include "infrastructure/llama_local_model_engine.h"

#include "infrastructure/app_logger.h"

#if LINCODE_LOCAL_MODEL_ENABLED

#include <algorithm>
#include <cstddef>
#include <cstdint>
#include <cstdio>
#include <filesystem>
#include <functional>
#include <memory>
#include <mutex>
#include <string>
#include <thread>
#include <utility>
#include <vector>

#include <ggml-backend.h>
#include <llama.h>

#include "application/local_model_device.h"

namespace linecode::infrastructure {
namespace {

using application::CompletionError;
using application::CompletionErrorCode;
using application::LocalChatTurn;
using application::LocalDeviceKind;
using application::LocalGenerationRequest;
using application::LocalGenerationResult;
using application::LocalModelDevice;
using application::LocalModelRuntimeInfo;
using domain::LocalAcceleration;
using domain::LocalAccelerationAvailability;

// Tokens one worker slice decodes before control returns to the runtime thread.
// Eight tokens is a few hundred milliseconds on a phone: enough that per-slice
// overhead stays invisible, small enough that a streamed answer keeps moving.
constexpr int kTokensPerSlice = 8;
// Prompt tokens primed per slice. Larger than the generation slice because
// priming produces nothing to deliver.
constexpr int kPromptTokensPerSlice = 256;
constexpr unsigned int kMaximumThreads = 8;
constexpr int kDefaultContextSize = 4096;

// llama.cpp logs to stderr by default, which on Android becomes logcat noise
// proportional to model size. Errors are still forwarded; anything below them is
// dropped because the same failure is already reported through the value this
// adapter returns.
void QuietLog(ggml_log_level level, const char *text, void *) {
  if (level >= GGML_LOG_LEVEL_ERROR && text != nullptr) {
    std::fputs(text, stderr);
  }
}

// Bridges llama.cpp's float progress into the percentage the application
// displays. The pointer is only used for the duration of one load call, which
// is what keeps the sink on the worker's stack.
struct LoadProgressSink final {
  const std::function<void(int)> *report{};
};

bool ReportLoadProgress(float progress, void *user_data) {
  const auto *sink = static_cast<LoadProgressSink *>(user_data);
  if (sink == nullptr || sink->report == nullptr || !*sink->report)
    return true;
  const int percent = std::clamp(
      static_cast<int>(progress * 100.0F + 0.5F), 0, 100);
  (*sink->report)(percent);
  return true;
}

// `llama_backend_init` registers the ggml backends and has to run before any
// device is enumerated or model loaded. The singleton is never destroyed: the
// model cache below holds live models, and tearing the backend down during
// static destruction would be worse than leaving it to process exit.
void EnsureBackend() {
  static const bool initialized = [] {
    llama_backend_init();
    llama_log_set(&QuietLog, nullptr);
    return true;
  }();
  (void)initialized;
}

// `ggml_backend_dev_type` names both the enum and the accessor function, so the
// elaborated type specifier is required to name the enum in a parameter list.
LocalDeviceKind ToDeviceKind(enum ggml_backend_dev_type type) {
  switch (type) {
  case GGML_BACKEND_DEVICE_TYPE_CPU:
    return LocalDeviceKind::cpu;
  case GGML_BACKEND_DEVICE_TYPE_GPU:
    return LocalDeviceKind::gpu;
  case GGML_BACKEND_DEVICE_TYPE_IGPU:
    return LocalDeviceKind::integrated_gpu;
  case GGML_BACKEND_DEVICE_TYPE_ACCEL:
    return LocalDeviceKind::accelerator;
  case GGML_BACKEND_DEVICE_TYPE_META:
    return LocalDeviceKind::meta;
  }
  return LocalDeviceKind::cpu;
}

std::string SafeDeviceText(const char *value) {
  return value == nullptr ? std::string{} : std::string{value};
}

struct EnumeratedDevices final {
  std::vector<LocalModelDevice> devices;
  LocalAccelerationAvailability availability;
};

EnumeratedDevices EnumerateDevices() {
  EnsureBackend();
  EnumeratedDevices result;
  const auto count = ggml_backend_dev_count();
  result.devices.reserve(count);
  for (std::size_t index = 0; index < count; ++index) {
    auto device = ggml_backend_dev_get(index);
    if (device == nullptr) {
      continue;
    }
    const auto name = SafeDeviceText(ggml_backend_dev_name(device));
    const auto acceleration = application::ClassifyLocalModelDevice(
        ToDeviceKind(ggml_backend_dev_type(device)), name);
    result.availability.gpu =
        result.availability.gpu || acceleration == LocalAcceleration::gpu;
    result.devices.push_back(LocalModelDevice{
        .name = name,
        .description = SafeDeviceText(ggml_backend_dev_description(device)),
        .acceleration = acceleration,
    });
  }
  return result;
}

// The device list handed to llama.cpp.
//
// `llama_model_params::devices` is a NULL-terminated array, so a vector's data
// pointer is not a valid value for it: without the sentinel the runtime reads
// past the end of the allocation. The requested kind is listed first so
// offloading prefers it, and the CPU device is always included because layers
// that do not fit still have to run somewhere.
std::vector<ggml_backend_dev_t> BuildDeviceList(LocalAcceleration acceleration) {
  const auto count = ggml_backend_dev_count();
  std::vector<ggml_backend_dev_t> devices;
  for (std::size_t pass = 0; pass < 2; ++pass) {
    const auto wanted =
        pass == 0 ? acceleration : LocalAcceleration::cpu;
    for (std::size_t index = 0; index < count; ++index) {
      auto device = ggml_backend_dev_get(index);
      if (device == nullptr) {
        continue;
      }
      const auto classified = application::ClassifyLocalModelDevice(
          ToDeviceKind(ggml_backend_dev_type(device)),
          SafeDeviceText(ggml_backend_dev_name(device)));
      if (classified == wanted &&
          std::ranges::find(devices, device) == devices.end()) {
        devices.push_back(device);
      }
    }
  }
  devices.push_back(nullptr);
  return devices;
}

struct CachedModel final {
  std::string path;
  LocalAcceleration acceleration{LocalAcceleration::automatic};
  int gpu_layers{};
  llama_model *model{};
};

// Process-wide model cache. One entry is a fully loaded GGUF whose weights
// dominate the process's memory, so entries are keyed by the placement that
// changes them and are never evicted: a phone normally holds one or two local
// models, and evicting under memory pressure would turn the next message into a
// multi-second reload.
class ModelCache final {
public:
  static ModelCache &Instance() {
    static ModelCache cache;
    return cache;
  }

  std::pair<llama_model *, std::string>
  Acquire(const std::string &path, LocalAcceleration acceleration,
          int gpu_layers, const std::function<void(int)> &on_load_progress) {
    std::lock_guard<std::mutex> guard(mutex_);
    for (const auto &entry : models_) {
      if (entry.path == path && entry.acceleration == acceleration &&
          entry.gpu_layers == gpu_layers) {
        return {entry.model, {}};
      }
    }

    auto devices = BuildDeviceList(acceleration);
    auto params = llama_model_default_params();
    params.n_gpu_layers = gpu_layers;
    // The list is always NULL terminated by BuildDeviceList, so it is only
    // passed when it holds a real device besides that terminator.
    params.devices = devices.size() > 1 ? devices.data() : nullptr;

    LoadProgressSink progress_sink{&on_load_progress};
    if (on_load_progress) {
      params.progress_callback = &ReportLoadProgress;
      params.progress_callback_user_data = &progress_sink;
    }
    llama_model *model = llama_model_load_from_file(path.c_str(), params);
    if (model == nullptr) {
      // The runtime's own error text never reaches the application's error log,
      // so record the failure where the log screen can show it.
      LogAppError("local_model_load_failed");
      return {nullptr, "llama.cpp could not load " + path};
    }
    models_.push_back(CachedModel{.path = path,
                                  .acceleration = acceleration,
                                  .gpu_layers = gpu_layers,
                                  .model = model});
    return {model, {}};
  }

private:
  std::mutex mutex_;
  std::vector<CachedModel> models_;
};

// Longest prefix of `bytes` that ends on a complete UTF-8 sequence. A token
// piece can split a multi-byte character, and emitting the partial bytes would
// put invalid UTF-8 into the conversation text.
std::size_t Utf8CompleteLength(std::string_view bytes) {
  if (bytes.empty()) {
    return 0;
  }
  // Scan back from the end over at most three continuation bytes to find the
  // last lead byte.
  std::size_t lead = bytes.size();
  const std::size_t maximum_back = std::min<std::size_t>(4, bytes.size());
  for (std::size_t back = 1; back <= maximum_back; ++back) {
    const auto candidate = bytes.size() - back;
    const auto value = static_cast<unsigned char>(bytes[candidate]);
    if ((value & 0xC0U) != 0x80U) {
      lead = candidate;
      break;
    }
  }
  if (lead == bytes.size()) {
    // Every byte examined was a continuation byte, so no lead byte is present
    // in the tail; the whole buffer is a fragment of a longer sequence.
    return 0;
  }
  const auto lead_value = static_cast<unsigned char>(bytes[lead]);
  std::size_t expected = 1;
  if ((lead_value & 0x80U) == 0x00U) {
    expected = 1;
  } else if ((lead_value & 0xE0U) == 0xC0U) {
    expected = 2;
  } else if ((lead_value & 0xF0U) == 0xE0U) {
    expected = 3;
  } else if ((lead_value & 0xF8U) == 0xF0U) {
    expected = 4;
  } else {
    // An invalid lead byte can never be completed; dropping it beats stalling
    // the stream forever.
    return bytes.size();
  }
  return lead + expected <= bytes.size() ? bytes.size() : lead;
}

// A prompt rendered without any chat template. Only reached when the model
// carries neither its own template nor one of the templates llama.cpp knows,
// which happens for raw base models.
std::string RenderPlainPrompt(const std::vector<LocalChatTurn> &turns) {
  std::string prompt;
  for (const auto &turn : turns) {
    prompt += turn.role;
    prompt += "\n";
    prompt += turn.content;
    prompt += "\n";
  }
  prompt += "assistant\n";
  return prompt;
}

std::string ApplyChatTemplate(const llama_model *model,
                              const std::string &override_template,
                              const std::vector<LocalChatTurn> &turns) {
  const char *tmpl = nullptr;
  if (!override_template.empty()) {
    tmpl = override_template.c_str();
  } else {
    const char *stored = llama_model_chat_template(model, nullptr);
    if (stored != nullptr) {
      tmpl = stored;
    }
  }

  std::vector<llama_chat_message> messages;
  messages.reserve(turns.size());
  for (const auto &turn : turns) {
    messages.push_back(llama_chat_message{.role = turn.role.c_str(),
                                          .content = turn.content.c_str()});
  }

  // The documented recommendation is twice the total character count, and the
  // call returns the length it needs, so a short buffer is grown and retried
  // rather than silently truncated.
  std::size_t capacity = 256;
  for (const auto &turn : turns) {
    capacity += 2 * (turn.role.size() + turn.content.size());
  }
  for (int attempt = 0; attempt < 4; ++attempt) {
    std::vector<char> buffer(capacity);
    const auto written = llama_chat_apply_template(
        tmpl, messages.data(), messages.size(), true, buffer.data(),
        static_cast<int32_t>(buffer.size()));
    if (written < 0) {
      break;
    }
    if (static_cast<std::size_t>(written) < buffer.size()) {
      return std::string{buffer.data(), static_cast<std::size_t>(written)};
    }
    capacity = static_cast<std::size_t>(written) + 1;
  }
  return RenderPlainPrompt(turns);
}

struct Session final {
  llama_model *model{};
  llama_context *context{};
  llama_sampler *sampler{};
  std::vector<llama_token> tokens;
  std::size_t prompt_tokens{};
  std::size_t fed_tokens{};
  int generated{};
  int maximum_tokens{};
  bool prompt_primed{};
  bool finished{};

  ~Session() {
    if (sampler != nullptr) {
      llama_sampler_free(sampler);
    }
    if (context != nullptr) {
      llama_free(context);
    }
  }

  Session() = default;
  Session(const Session &) = delete;
  Session &operator=(const Session &) = delete;
};

// Everything the worker prepares, returned by value so the decode slices never
// touch the coroutine frame from a worker thread.
struct Preparation final {
  std::string failure;
};

Preparation Prepare(const std::shared_ptr<Session> &session,
                    const LocalGenerationRequest &request,
                    LocalAcceleration acceleration, int gpu_layers) {
  auto [model, error] = ModelCache::Instance().Acquire(
      request.model_path, acceleration, gpu_layers,
      request.on_load_progress);
  if (model == nullptr) {
    return {.failure = error.empty() ? std::string{"Failed to load the GGUF model"}
                                    : std::move(error)};
  }
  session->model = model;
  const auto *vocab = llama_model_get_vocab(model);

  const auto prompt =
      ApplyChatTemplate(model, request.chat_template, request.messages);
  auto token_count = llama_tokenize(vocab, prompt.data(),
                                    static_cast<int32_t>(prompt.size()), nullptr,
                                    0, true, true);
  if (token_count < 0) {
    // A negative result is the required capacity.
    token_count = -token_count;
  }
  session->tokens.resize(static_cast<std::size_t>(token_count));
  const auto written = llama_tokenize(
      vocab, prompt.data(), static_cast<int32_t>(prompt.size()),
      session->tokens.data(), token_count, true, true);
  if (written < 0) {
    return {.failure = "The prompt could not be tokenized"};
  }
  session->tokens.resize(static_cast<std::size_t>(written));
  session->prompt_tokens = session->tokens.size();

  auto context_size = request.context_size > 0 ? request.context_size
                                               : kDefaultContextSize;
  const auto trained = llama_model_n_ctx_train(model);
  if (trained > 0 && context_size > trained) {
    context_size = trained;
  }
  if (static_cast<std::size_t>(context_size) <= session->prompt_tokens + 1) {
    return {.failure = "The prompt needs " +
                           std::to_string(session->prompt_tokens) +
                           " tokens but the configured context is " +
                           std::to_string(context_size)};
  }

  auto context_params = llama_context_default_params();
  context_params.n_ctx = static_cast<std::uint32_t>(context_size);
  context_params.n_batch =
      static_cast<std::uint32_t>(kPromptTokensPerSlice);
  const auto hardware = std::thread::hardware_concurrency();
  const auto threads = std::clamp(hardware == 0U ? 2U : hardware, 1U,
                                  kMaximumThreads);
  context_params.n_threads = static_cast<int32_t>(threads);
  context_params.n_threads_batch = context_params.n_threads;

  session->context = llama_init_from_model(model, context_params);
  if (session->context == nullptr) {
    return {.failure = "llama.cpp could not create a context for the model"};
  }

  session->sampler =
      llama_sampler_chain_init(llama_sampler_chain_default_params());
  llama_sampler_chain_add(session->sampler, llama_sampler_init_top_k(40));
  llama_sampler_chain_add(session->sampler, llama_sampler_init_top_p(0.95F, 1));
  llama_sampler_chain_add(session->sampler, llama_sampler_init_min_p(0.05F, 1));
  llama_sampler_chain_add(session->sampler, llama_sampler_init_temp(0.8F));
  llama_sampler_chain_add(session->sampler,
                          llama_sampler_init_dist(LLAMA_DEFAULT_SEED));

  session->maximum_tokens =
      request.max_tokens > 0
          ? request.max_tokens
          : context_size - static_cast<int>(session->prompt_tokens);
  return {};
}

// One decode slice. The text is returned instead of being emitted, so the piece
// callback only ever runs on the runtime thread.
struct SliceResult final {
  std::string produced;
  std::string failure;
};

SliceResult RunSlice(const std::shared_ptr<Session> &session) {
  SliceResult result;
  const auto *vocab = llama_model_get_vocab(session->model);

  // Priming feeds exactly one chunk per slice and yields no output, which keeps
  // a long prompt from monopolizing a worker for its whole length.
  if (!session->prompt_primed) {
    const auto remaining = session->tokens.size() - session->fed_tokens;
    if (remaining == 0) {
      session->prompt_primed = true;
      return result;
    }
    const auto chunk = std::min<std::size_t>(
        remaining, static_cast<std::size_t>(kPromptTokensPerSlice));
    auto batch = llama_batch_get_one(session->tokens.data() + session->fed_tokens,
                                     static_cast<int32_t>(chunk));
    if (llama_decode(session->context, batch) != 0) {
      result.failure = "llama.cpp failed to evaluate the prompt";
      session->finished = true;
      return result;
    }
    session->fed_tokens += chunk;
    if (session->fed_tokens >= session->tokens.size()) {
      session->prompt_primed = true;
    }
    return result;
  }

  for (int step = 0; step < kTokensPerSlice; ++step) {
    if (session->generated >= session->maximum_tokens) {
      session->finished = true;
      return result;
    }
    llama_token token =
        llama_sampler_sample(session->sampler, session->context, -1);
    if (llama_vocab_is_eog(vocab, token)) {
      session->finished = true;
      return result;
    }
    char buffer[256];
    const auto written = llama_token_to_piece(
        vocab, token, buffer, static_cast<int32_t>(sizeof(buffer)), 0, false);
    if (written > 0) {
      result.produced.append(buffer, static_cast<std::size_t>(written));
    }
    ++session->generated;

    auto batch = llama_batch_get_one(&token, 1);
    if (llama_decode(session->context, batch) != 0) {
      result.failure = "llama.cpp failed to evaluate a generated token";
      session->finished = true;
      return result;
    }
  }
  return result;
}

} // namespace

struct LlamaLocalModelEngine::Impl final {
  std::vector<LocalModelDevice> devices;
  std::string load_failure;
  bool available{};
};

LlamaLocalModelEngine::LlamaLocalModelEngine()
    : impl_(std::make_shared<Impl>()) {
  const auto enumerated = EnumerateDevices();
  impl_->devices = enumerated.devices;
  // A build whose backend registry is empty cannot run a model, and reporting
  // that here is what keeps the UI from offering local models that would fail
  // on the first message.
  impl_->available = !enumerated.devices.empty();
  if (!impl_->available) {
    impl_->load_failure = "llama.cpp reported no compute device";
  }
}

LlamaLocalModelEngine::~LlamaLocalModelEngine() = default;

bool LlamaLocalModelEngine::Available() const noexcept {
  return impl_ != nullptr && impl_->available;
}

LocalModelRuntimeInfo LlamaLocalModelEngine::RuntimeInfo() const {
  return LocalModelRuntimeInfo{
      .available = Available(),
      .packaged = application::local_model_packaged,
      .engine = std::string{application::LocalModelEngineName()},
      .revision = std::string{application::LocalModelEngineRevision()},
      .backends = application::EnabledLocalModelBackendNames(),
      .devices =
          impl_ == nullptr ? std::vector<LocalModelDevice>{} : impl_->devices,
  };
}

huxerui::Task<std::expected<LocalGenerationResult, CompletionError>>
LlamaLocalModelEngine::Generate(LocalGenerationRequest request,
                                std::function<void(std::string_view)> on_piece) {
  if (!Available()) {
    LogAppError("local_model_backend_unavailable");
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::unsupported_protocol,
        .message = impl_->load_failure.empty()
                       ? "The local model backend is not available"
                       : impl_->load_failure,
    });
  }
  if (request.model_path.empty()) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = "The local model has no GGUF file",
    });
  }
  std::error_code file_error;
  if (!std::filesystem::is_regular_file(request.model_path, file_error)) {
    LogAppError("local_model_file_missing");
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = "The GGUF file is missing: " + request.model_path,
    });
  }

  const auto enumerated = EnumerateDevices();
  const auto resolved = domain::ResolveLocalAcceleration(
      request.acceleration, enumerated.availability);
  const auto gpu_layers = domain::LocalGpuLayerCount(resolved);

  auto session = std::make_shared<Session>();
  // The values the worker needs are captured by value: a cancelled task must not
  // leave a running worker reading a destroyed coroutine frame.
  const auto preparation = co_await huxerui::RunWorker(
      [session, request, resolved, gpu_layers]() -> Preparation {
        return Prepare(session, request, resolved, gpu_layers);
      });
  // The load window closes here, not when the turn does, and on the runtime
  // thread so an observer can touch UI state without a lock. With mmap the
  // weights are mapped without being read, so the first decode is what actually
  // pulls them off disk: holding the label until the first token would keep
  // "loading the model" on screen for the whole answer.
  if (request.on_load_finished) {
    request.on_load_finished();
  }
  if (!preparation.failure.empty() || session->context == nullptr) {
    co_return std::unexpected(CompletionError{
        .code = CompletionErrorCode::invalid_configuration,
        .message = preparation.failure.empty()
                       ? "The local model could not be prepared"
                       : preparation.failure,
    });
  }

  std::string pending_bytes;
  while (!session->finished) {
    const auto slice = co_await huxerui::RunWorker(
        [session]() -> SliceResult { return RunSlice(session); });
    if (!slice.failure.empty()) {
      co_return std::unexpected(CompletionError{
          .code = CompletionErrorCode::transport,
          .message = slice.failure,
      });
    }
    // One slice is done: the prompt got shorter or a few tokens were decoded.
    // Nothing may have been produced either way, which is why the turn reports
    // the slice itself as its sign of life.
    if (request.on_activity) {
      request.on_activity();
    }
    if (slice.produced.empty()) {
      continue;
    }
    pending_bytes += slice.produced;
    const auto complete = Utf8CompleteLength(pending_bytes);
    if (complete == 0) {
      continue;
    }
    // The callback runs here, on the task's runtime thread, never in a worker.
    if (on_piece) {
      on_piece(std::string_view{pending_bytes}.substr(0, complete));
    }
    pending_bytes.erase(0, complete);
  }

  if (!pending_bytes.empty() && on_piece) {
    // A truncated multi-byte sequence at the very end of a reply is dropped
    // rather than emitted as invalid UTF-8.
    on_piece(pending_bytes);
  }

  co_return LocalGenerationResult{
      .prompt_tokens = static_cast<std::int32_t>(session->prompt_tokens),
      .generated_tokens = static_cast<std::int32_t>(session->generated),
      .effective_acceleration = resolved,
  };
}

} // namespace linecode::infrastructure

#endif // LINCODE_LOCAL_MODEL_ENABLED
