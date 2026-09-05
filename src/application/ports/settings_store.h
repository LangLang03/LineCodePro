#pragma once

#include <optional>
#include <string>
#include <string_view>

namespace linecode::application {

class SettingsStore {
public:
  virtual ~SettingsStore() = default;

  [[nodiscard]] virtual std::optional<std::string> Read(std::string_view key) const = 0;
  virtual void Write(std::string_view key, std::string value) = 0;
};

} // namespace linecode::application
