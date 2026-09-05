#pragma once

#include <cstdint>
#include <string>

#include <huxerui/view.h>

#include "domain/app_state.h"

namespace linecode::presentation {

struct AboutAppInfo final {
  std::string app_name = "LineCode Pro";
  std::string version_name = "1.2.8-max";
  std::int64_t version_code = 32;
};

// licenses_route remains a typed route value so the integration layer can add
// the destination without introducing stringly typed navigation here.
[[huxerui::composable]] huxerui::View
AboutScreen(domain::AppRoute licenses_route, AboutAppInfo app_info = {});

} // namespace linecode::presentation
