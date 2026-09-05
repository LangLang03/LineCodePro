#pragma once

#include <cstdint>
#include <expected>
#include <optional>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "domain/model_config.h"

namespace linecode::application {

struct ModelStoreError final {
  std::string message;

  bool operator==(const ModelStoreError &) const = default;
};

class ModelStore {
public:
  virtual ~ModelStore() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<std::vector<domain::ModelConfig>, ModelStoreError>>
  List() = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<std::optional<domain::ModelConfig>, ModelStoreError>>
  Find(std::string id) = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<domain::ModelConfig, ModelStoreError>>
  Save(domain::ModelConfig model) = 0;
  [[nodiscard]] virtual huxerui::Task<std::expected<void, ModelStoreError>>
  Delete(std::vector<std::string> ids) = 0;
  [[nodiscard]] virtual huxerui::Task<std::expected<void, ModelStoreError>>
  Select(std::string id) = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<std::string, ModelStoreError>>
  SelectedId() = 0;
};

struct ModelProbeResult final {
  std::string response;
  std::int64_t elapsed_milliseconds{};

  bool operator==(const ModelProbeResult &) const = default;
};

class ModelCatalogGateway {
public:
  virtual ~ModelCatalogGateway() = default;

  [[nodiscard]] virtual huxerui::Task<
      std::expected<std::vector<std::string>, ModelStoreError>>
  Fetch(domain::ModelProtocol protocol, std::string base_url,
        std::string api_key) = 0;
  [[nodiscard]] virtual huxerui::Task<
      std::expected<ModelProbeResult, ModelStoreError>>
  Probe(domain::ModelConfig model) = 0;
};

} // namespace linecode::application
