#pragma once

#include <cstdint>
#include <memory>

#include <huxerui/task.h>

#include "application/ports/settings_store.h"

namespace linecode::application {

// Owns the versioned consent policy and persistence keys. Presentation only
// decides how to render the required agreement.
class UserAgreement final {
public:
  static constexpr std::int64_t current_version = 1;

  explicit UserAgreement(std::shared_ptr<AsyncSettingsStore> settings);

  [[nodiscard]] huxerui::Task<bool> ShouldShow() const;
  [[nodiscard]] huxerui::Task<bool> Accept() const;

private:
  std::shared_ptr<AsyncSettingsStore> settings_;
};

} // namespace linecode::application
