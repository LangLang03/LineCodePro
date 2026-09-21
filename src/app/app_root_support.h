#pragma once

#include <cstdint>
#include <string>

#include <huxerui/huxerui.h>

#include "application/project_workspace_service.h"
#include "application/theme_settings.h"
#include "presentation/line_theme.h"

namespace linecode::app {

class HostSystemThemeSource final : public application::SystemThemeSource {
public:
  explicit HostSystemThemeSource(bool dark);

  [[nodiscard]] bool IsDarkModeEnabled() const override;
  void Update(bool dark);

private:
  bool dark_{};
};

[[nodiscard]] huxerui::File
DatabaseFileFor(const huxerui::File &data_directory);

class SystemWorkspaceClock final : public application::WorkspaceClock {
public:
  [[nodiscard]] std::int64_t NowMilliseconds() const noexcept override;
};

[[nodiscard]] bool IsDark(const huxerui::Color &color);

enum class BootstrapPhase : std::uint8_t { loading, ready, failed };

struct BootstrapStatus final {
  BootstrapPhase phase{BootstrapPhase::loading};
  std::string error;

  bool operator==(const BootstrapStatus &) const = default;
};

[[nodiscard]] huxerui::View DatabaseFailureView(
    huxerui::StringVariant title, huxerui::StringVariant message,
    const presentation::LineColors &colors);

[[nodiscard]] huxerui::View PlatformServicesHost();

} // namespace linecode::app
