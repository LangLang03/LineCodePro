#pragma once

#include <string_view>

namespace linecode::application {

enum class ExtensionBackendReadiness { available, unavailable };

struct ExtensionBackendSource final {
  std::string_view capability;
  ExtensionBackendReadiness readiness;
  std::string_view upstream_catalog;
  std::string_view audited_on;
};

// Public HuxerUI organization repositories were audited on this date. None of
// the published SDK libraries provides the old LineCode LIP runtime/backend, so
// the UI must expose the legacy unavailable state until an actual backend is
// selected and integrated.
inline constexpr ExtensionBackendSource kLineCodeLipBackend{
    .capability = "LineCode LIP extension runtime",
    .readiness = ExtensionBackendReadiness::unavailable,
    .upstream_catalog = "https://api.github.com/orgs/HuxerUI/repos",
    .audited_on = "2026-09-11",
};

static_assert(kLineCodeLipBackend.readiness ==
              ExtensionBackendReadiness::unavailable);

} // namespace linecode::application
