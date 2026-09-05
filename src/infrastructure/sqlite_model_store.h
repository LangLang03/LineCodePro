#pragma once

#include <expected>
#include <optional>

#include <huxerui/file.h>
#include <huxerui/sqlite.h>

#include "application/ports/model_store.h"

namespace linecode::infrastructure {

class SqliteModelStore final : public application::ModelStore {
public:
  explicit SqliteModelStore(huxerui::File database_file);

  [[nodiscard]] huxerui::Task<std::expected<std::vector<domain::ModelConfig>,
                                            application::ModelStoreError>>
  List() override;
  [[nodiscard]] huxerui::Task<std::expected<std::optional<domain::ModelConfig>,
                                            application::ModelStoreError>>
  Find(std::string id) override;
  [[nodiscard]] huxerui::Task<
      std::expected<domain::ModelConfig, application::ModelStoreError>>
  Save(domain::ModelConfig model) override;
  [[nodiscard]] huxerui::Task<std::expected<void, application::ModelStoreError>>
  Delete(std::vector<std::string> ids) override;
  [[nodiscard]] huxerui::Task<std::expected<void, application::ModelStoreError>>
  Select(std::string id) override;
  [[nodiscard]] huxerui::Task<
      std::expected<std::string, application::ModelStoreError>>
  SelectedId() override;

private:
  [[nodiscard]] huxerui::Task<
      std::expected<huxerui::sqlite::Database, application::ModelStoreError>>
  Open();

  huxerui::File database_file_;
  std::optional<huxerui::sqlite::Database> database_;
};

} // namespace linecode::infrastructure
