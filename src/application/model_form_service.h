#pragma once

#include <expected>
#include <optional>
#include <string>
#include <string_view>

#include "domain/model_config.h"

namespace linecode::application {

struct ModelDraft final {
  std::string id;
  std::string name;
  domain::ModelProtocol protocol{domain::ModelProtocol::openai_compatible};
  std::string provider_label;
  std::string base_url;
  std::string api_key;
  std::string model_id;
  std::string tool_call_limit{"200"};
  bool compression_enabled{};
  bool compression_auto{true};
  std::string compression_model_id;
  std::string context_size;
  bool local{};

  bool operator==(const ModelDraft &) const = default;
};

enum class ModelValidationCode : std::uint8_t {
  local_backend_unavailable,
  missing_name_or_model_id,
  missing_api_key,
  invalid_tool_call_limit,
  missing_compression_model_id,
};

struct ModelValidationError final {
  ModelValidationCode code;
};

class ModelFormService final {
public:
  [[nodiscard]] static ModelDraft
  New(std::optional<domain::ModelProviderPreset> preset, bool local);
  [[nodiscard]] static ModelDraft Edit(const domain::ModelConfig &model);
  [[nodiscard]] static std::expected<domain::ModelConfig, ModelValidationError>
  Build(const ModelDraft &draft);
  [[nodiscard]] static std::expected<domain::ModelConfig, ModelValidationError>
  BuildForProbe(const ModelDraft &draft);
  [[nodiscard]] static bool CanSave(const ModelDraft &draft);
  [[nodiscard]] static bool CanQuery(const ModelDraft &draft);
  [[nodiscard]] static int ParseContextSize(std::string_view text) noexcept;
  [[nodiscard]] static std::string FormatContextSize(int tokens);
  [[nodiscard]] static std::string EffectiveBaseUrl(const ModelDraft &draft);
};

} // namespace linecode::application
