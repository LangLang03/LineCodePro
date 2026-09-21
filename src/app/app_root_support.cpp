#include "app/app_root_support.h"

#include <chrono>
#include <utility>

#if defined(__ANDROID__)
#include "application/ports/keep_alive.h"
#endif

namespace linecode::app {

HostSystemThemeSource::HostSystemThemeSource(bool dark) : dark_(dark) {}

bool HostSystemThemeSource::IsDarkModeEnabled() const { return dark_; }

void HostSystemThemeSource::Update(bool dark) { dark_ = dark; }

huxerui::File DatabaseFileFor(const huxerui::File &data_directory) {
#if defined(__ANDROID__)
  if (const auto package_directory = data_directory.Parent()) {
    const auto legacy_database =
        package_directory->Child("databases").Child("linecode.db");
    if (legacy_database.IsFile())
      return legacy_database;
  }
#endif
  return data_directory.Child("linecode.db");
}

std::int64_t SystemWorkspaceClock::NowMilliseconds() const noexcept {
  return std::chrono::duration_cast<std::chrono::milliseconds>(
             std::chrono::system_clock::now().time_since_epoch())
      .count();
}

bool IsDark(const huxerui::Color &color) {
  const float luminance =
      color.red * 0.2126F + color.green * 0.7152F + color.blue * 0.0722F;
  return luminance < 0.5F;
}

huxerui::View DatabaseFailureView(
    huxerui::StringVariant title, huxerui::StringVariant message,
    const presentation::LineColors &colors) {
  return huxerui::Stack {
    huxerui::Column {
      huxerui::Text(std::move(title))
          .Style(huxerui::TextStyle {
            huxerui::Font::System(20.0F).WithWeight(
                huxerui::FontWeight::Bold),
            colors.danger,
          }),
      huxerui::Text(std::move(message))
          .Style(huxerui::TextStyle {
            huxerui::Font::System(13.0F),
            colors.secondary,
          })
          .Align(huxerui::TextAlign::Center),
    }.With(
        huxerui::Frame{.max_width = 520.0F}, huxerui::Spacing(12.0F),
        huxerui::Padding(24.0F),
        huxerui::CrossAlign(huxerui::CrossAxisAlignment::Center)),
  }.With(
      huxerui::Align(huxerui::HorizontalAlignment::Center,
                     huxerui::VerticalAlignment::Center),
      huxerui::Background(colors.background));
}

#if defined(__ANDROID__)
[[huxerui::composable]] huxerui::View PlatformServicesHost() {
  auto keep_alive = huxerui::UseService<application::KeepAliveService>();
  huxerui::Lifecycle(
      [keep_alive] { keep_alive->RefreshPreferences([](auto) {}); });
  return huxerui::Stack {}.With(
      huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#else
huxerui::View PlatformServicesHost() {
  return huxerui::Stack {}.With(
      huxerui::Frame{.width = 0.0F, .height = 0.0F});
}
#endif

} // namespace linecode::app
