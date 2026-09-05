#pragma once

#include <array>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

namespace linecode::domain {

enum class ModelProtocol : std::uint8_t {
  openai_compatible,
  codex_responses,
  anthropic_messages,
  local_gguf,
};

[[nodiscard]] constexpr bool
SupportsDedicatedCompression(ModelProtocol protocol) noexcept {
  return protocol == ModelProtocol::openai_compatible ||
         protocol == ModelProtocol::codex_responses;
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
  std::string_view id;
  ModelProtocol protocol;
  std::string_view base_url;
  std::string_view placeholder;

  bool operator==(const ModelProviderPreset &) const = default;
};

[[nodiscard]] const std::array<ModelProviderPreset, 17> &
ModelProviderPresets() noexcept;
[[nodiscard]] std::optional<ModelProviderPreset>
FindModelProviderPreset(std::string_view id) noexcept;

} // namespace linecode::domain
