#pragma once

#include <expected>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "domain/model_config.h"

namespace linecode::infrastructure {

struct ModelCatalogCodecError final {
  std::string message;
  std::size_t offset{};

  bool operator==(const ModelCatalogCodecError &) const = default;
};

struct ModelHttpRequestDescriptor final {
  std::string url;
  std::vector<std::pair<std::string, std::string>> headers;
  std::string body;

  bool operator==(const ModelHttpRequestDescriptor &) const = default;
};

inline constexpr std::string_view kCodexProtocolVersion = "0.120.0";
inline constexpr std::string_view kCodexOriginator = "codex_cli_rs";
inline constexpr std::string_view kModelProbePrompt =
    "Calculate 1+1 and reply with any result.";

[[nodiscard]] std::expected<ModelHttpRequestDescriptor,
                            ModelCatalogCodecError>
BuildModelCatalogRequest(domain::ModelProtocol protocol,
                         std::string_view base_url,
                         std::string_view api_key);

[[nodiscard]] std::expected<ModelHttpRequestDescriptor,
                            ModelCatalogCodecError>
BuildModelProbeRequest(const domain::ModelConfig &model);

[[nodiscard]] std::expected<std::vector<std::string>,
                            ModelCatalogCodecError>
DecodeModelCatalogResponse(std::string_view json);

[[nodiscard]] std::expected<std::string, ModelCatalogCodecError>
DecodeModelProbeResponse(domain::ModelProtocol protocol,
                         std::string_view json);

} // namespace linecode::infrastructure
