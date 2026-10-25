#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::domain {

enum class ModelProtocol : std::uint8_t {
  openai_compatible,
  codex_responses,
  anthropic_messages,
  local_gguf,
};

enum class ModelProviderPresetKind : std::uint8_t {
  deepseek,
  glm,
  mimo,
  mimo_token_plan,
  kimi,
  qwen,
  openai,
  claude,
  gemini,
  openrouter,
  groq,
  together,
  siliconflow,
  minimax,
  ollama,
  lmstudio,
  codex,
  count,
};

inline constexpr std::size_t model_provider_preset_count =
    static_cast<std::size_t>(std::to_underlying(ModelProviderPresetKind::count));

[[nodiscard]] constexpr std::size_t
ModelProviderPresetIndex(ModelProviderPresetKind kind) noexcept {
  return static_cast<std::size_t>(std::to_underlying(kind));
}

struct ModelProtocolDescriptor final {
  ModelProtocol protocol;
  std::string_view label;
  std::string_view default_base_url;
  bool supports_dedicated_compression;

  bool operator==(const ModelProtocolDescriptor &) const = default;
};

inline constexpr std::array model_protocol_catalog{
    ModelProtocolDescriptor{ModelProtocol::openai_compatible, "OpenAI",
                            "https://api.openai.com/v1", true},
    ModelProtocolDescriptor{ModelProtocol::codex_responses, "Codex",
                            "https://api.openai.com/v1", true},
    ModelProtocolDescriptor{ModelProtocol::anthropic_messages, "Anthropic",
                            "https://api.anthropic.com", false},
    ModelProtocolDescriptor{ModelProtocol::local_gguf, "Local", "", false},
};

[[nodiscard]] constexpr const ModelProtocolDescriptor &
ModelProtocolInfo(ModelProtocol protocol) noexcept {
  for (const auto &descriptor : model_protocol_catalog) {
    if (descriptor.protocol == protocol)
      return descriptor;
  }
  std::unreachable();
}

[[nodiscard]] constexpr bool
SupportsDedicatedCompression(ModelProtocol protocol) noexcept {
  return ModelProtocolInfo(protocol).supports_dedicated_compression;
}

[[nodiscard]] constexpr std::string_view
DefaultModelBaseUrl(ModelProtocol protocol) noexcept {
  return ModelProtocolInfo(protocol).default_base_url;
}

[[nodiscard]] std::string_view
ModelProtocolStorageName(ModelProtocol protocol) noexcept;
[[nodiscard]] ModelProtocol ParseModelProtocol(std::string_view value) noexcept;
[[nodiscard]] std::string_view
ModelProtocolLabel(ModelProtocol protocol) noexcept;

// Which accelerator an in-process GGUF model asks for. The value is a
// preference, not a guarantee: `ResolveLocalAcceleration` downgrades it to what
// the loaded engine actually exposes at run time.
//
// There is deliberately no `npu` member. Upstream llama.cpp's only NPU backend
// is the Qualcomm Hexagon one, and building it requires the Qualcomm Hexagon
// SDK (its HTP skels are compiled with the Hexagon toolchain). This project
// does not require that SDK, so NPU acceleration is not supported yet -- the
// acceleration list, the strings and the README all say so explicitly rather
// than offering an option that could only degrade. `ParseLocalAcceleration`
// still maps an NPU-era value onto the CPU so an outside archive that carries
// one keeps loading.
enum class LocalAcceleration : std::uint8_t {
  automatic,
  cpu,
  gpu,
  count,
};

inline constexpr std::size_t local_acceleration_count =
    static_cast<std::size_t>(std::to_underlying(LocalAcceleration::count));

[[nodiscard]] constexpr std::size_t
LocalAccelerationIndex(LocalAcceleration acceleration) noexcept {
  return static_cast<std::size_t>(std::to_underlying(acceleration));
}

struct LocalAccelerationDescriptor final {
  LocalAcceleration acceleration;
  std::string_view label;
  std::string_view storage_name;
  // Whether the mode runs the network on a dedicated device instead of the CPU
  // backend, which is what decides the layer-offload request sent to llama.cpp.
  bool offloads_to_device;

  bool operator==(const LocalAccelerationDescriptor &) const = default;
};

inline constexpr std::array local_acceleration_catalog{
    LocalAccelerationDescriptor{LocalAcceleration::automatic, "Auto",
                                "AUTOMATIC", true},
    LocalAccelerationDescriptor{LocalAcceleration::cpu, "CPU", "CPU", false},
    LocalAccelerationDescriptor{LocalAcceleration::gpu, "GPU", "GPU", true},
};

[[nodiscard]] constexpr const LocalAccelerationDescriptor &
LocalAccelerationInfo(LocalAcceleration acceleration) noexcept {
  for (const auto &descriptor : local_acceleration_catalog) {
    if (descriptor.acceleration == acceleration)
      return descriptor;
  }
  std::unreachable();
}

[[nodiscard]] constexpr std::string_view
LocalAccelerationLabel(LocalAcceleration acceleration) noexcept {
  return LocalAccelerationInfo(acceleration).label;
}

[[nodiscard]] constexpr std::string_view
LocalAccelerationStorageName(LocalAcceleration acceleration) noexcept {
  return LocalAccelerationInfo(acceleration).storage_name;
}

[[nodiscard]] LocalAcceleration
ParseLocalAcceleration(std::string_view value) noexcept;

// Which of the accelerators the running engine can actually use. The CPU
// backend is always part of a build that contains the local model support, so
// only the device backends are tracked here.
struct LocalAccelerationAvailability final {
  bool gpu{};

  bool operator==(const LocalAccelerationAvailability &) const = default;
};

// Resolution is deliberately total: an unavailable request degrades to the CPU
// backend instead of failing, which keeps a saved model loadable on a device
// whose accelerator driver was removed or whose build does not include it.
[[nodiscard]] constexpr LocalAcceleration ResolveLocalAcceleration(
    LocalAcceleration requested,
    LocalAccelerationAvailability available) noexcept {
  switch (requested) {
  case LocalAcceleration::automatic:
    return available.gpu ? LocalAcceleration::gpu : LocalAcceleration::cpu;
  case LocalAcceleration::gpu:
    return available.gpu ? LocalAcceleration::gpu : LocalAcceleration::cpu;
  case LocalAcceleration::cpu:
  case LocalAcceleration::count:
    return LocalAcceleration::cpu;
  }
  return LocalAcceleration::cpu;
}

// llama.cpp treats every non-negative value as an explicit layer count and a
// negative value as "every layer"; the engine uses this to translate the mode.
[[nodiscard]] constexpr int LocalGpuLayerCount(
    LocalAcceleration resolved) noexcept {
  return LocalAccelerationInfo(resolved).offloads_to_device ? -1 : 0;
}

struct ModelConfig final {
  static constexpr int default_tool_call_limit = 200;
  static constexpr int unlimited_tool_calls = -1;
  static constexpr int context_size_unset = 0;

  std::string id;
  std::string name;
  ModelProtocol protocol{ModelProtocol::openai_compatible};
  std::string provider_label{"OpenAI"};
  std::string base_url;
  std::string api_key;
  std::string model_id;
  int tool_call_limit{default_tool_call_limit};
  bool compression_model_enabled{};
  bool compression_model_auto{true};
  std::string compression_model_id;
  int context_size{context_size_unset};
  // Absolute path of the GGUF weights the user imported, empty for every
  // remote protocol. Kept on the model rather than in a side table so the
  // existing model list, selection and archive paths keep working unchanged.
  // Defaulted so a designated initializer written before this field existed
  // still describes a complete remote model.
  std::string local_model_path{};
  LocalAcceleration local_acceleration{LocalAcceleration::automatic};

  [[nodiscard]] bool IsLocal() const noexcept {
    return protocol == ModelProtocol::local_gguf;
  }

  [[nodiscard]] std::string EffectiveCompressionModelId() const;
  void Normalize();

  bool operator==(const ModelConfig &) const = default;
};

struct ModelProviderPreset final {
  ModelProviderPresetKind kind;
  std::string_view id;
  std::string_view provider_label;
  ModelProtocol protocol;
  std::string_view base_url;
  std::string_view placeholder;

  bool operator==(const ModelProviderPreset &) const = default;
};

[[nodiscard]] const std::array<ModelProviderPreset,
                               model_provider_preset_count> &
ModelProviderPresets() noexcept;
[[nodiscard]] const ModelProviderPreset &
ModelProviderPresetFor(ModelProviderPresetKind kind);
[[nodiscard]] std::optional<ModelProviderPreset>
FindModelProviderPreset(std::string_view id) noexcept;

} // namespace linecode::domain
