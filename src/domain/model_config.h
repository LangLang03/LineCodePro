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
