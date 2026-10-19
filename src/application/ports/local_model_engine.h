#pragma once

#include <cstdint>
#include <expected>
#include <functional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/completion_gateway.h"
#include "domain/model_config.h"

namespace linecode::application {

// One compute device the in-process runtime can place a model on.
struct LocalModelDevice final {
  // Stable machine-readable identifier, as reported by the runtime.
  std::string name;
  // Human-readable description for diagnostics and the settings screen.
  std::string description;
  // Which preference this device satisfies.
  domain::LocalAcceleration acceleration{domain::LocalAcceleration::cpu};

  bool operator==(const LocalModelDevice &) const = default;
};

// What the linked runtime can do right now, as opposed to what the build
// requested. The distinction matters because a linked backend can still be
// unusable: a Vulkan build on a device whose driver is absent reports the CPU
// backend only.
struct LocalModelRuntimeInfo final {
  bool available{};
  bool packaged{};
  std::string engine;
  std::string revision;
  // Backend identifiers this build linked, in catalog order.
  std::vector<std::string> backends;
  // Devices the runtime enumerated, CPU first.
  std::vector<LocalModelDevice> devices;

  bool operator==(const LocalModelRuntimeInfo &) const = default;
};

// One turn handed to the runtime. The runtime applies the model's own chat
// template, so the application never has to know the prompt format of an
// arbitrary GGUF file.
struct LocalChatTurn final {
  std::string role;
  std::string content;

  bool operator==(const LocalChatTurn &) const = default;
};

// A generation request. The runtime is handed turns, not conversation
// semantics: prompt rendering, tool-instruction injection, tool-call extraction
// and stream projection all stay in the application layer, which keeps the
// adapter thin and the behaviour testable without a model file.
struct LocalGenerationRequest final {
  // Absolute path of the GGUF weights.
  std::string model_path;
  std::vector<LocalChatTurn> messages;
  // Context window to reserve. Zero asks the runtime for its own default.
  int context_size{};
  // Which accelerator the caller prefers. The engine downgrades this to what
  // the enumerated devices actually support.
  domain::LocalAcceleration acceleration{domain::LocalAcceleration::automatic};
  // Upper bound on generated tokens; zero means "until the model emits an end
  // of generation token or the context is exhausted".
  int max_tokens{};
  // Replaces the template stored in the GGUF when non-empty.
  std::string chat_template;
  // Reports weight-loading progress as a percentage while the runtime reads the
  // GGUF file. The runtime calls it from its loading thread, so an observer must
  // be thread-safe; the load finishes before the first piece is emitted.
  std::function<void(int)> on_load_progress{};
};

struct LocalGenerationResult final {
  std::int32_t prompt_tokens{};
  std::int32_t generated_tokens{};
  // The accelerator the engine actually used, after downgrading. Surfaced so a
  // caller can tell the user their NPU request fell back to the CPU.
  domain::LocalAcceleration effective_acceleration{
      domain::LocalAcceleration::cpu};

  bool operator==(const LocalGenerationResult &) const = default;
};

// Runs GGUF inference on the calling process's device.
//
// Implementations must invoke the piece callback on the task's runtime thread.
// The native decode loop runs in bounded worker slices and the callback fires
// between slices, so an observer may touch application state without a lock.
class LocalModelEngine {
public:
  virtual ~LocalModelEngine() = default;

  [[nodiscard]] virtual bool Available() const noexcept = 0;

  [[nodiscard]] virtual LocalModelRuntimeInfo RuntimeInfo() const = 0;

  // Streams generated text. `on_piece` receives each decoded piece in order; a
  // piece may be empty, which callers must treat as "nothing to append".
  [[nodiscard]] virtual huxerui::Task<
      std::expected<LocalGenerationResult, CompletionError>>
  Generate(LocalGenerationRequest request,
           std::function<void(std::string_view)> on_piece) = 0;
};

} // namespace linecode::application
