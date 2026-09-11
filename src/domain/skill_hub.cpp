#include "domain/skill_hub.h"

#include <algorithm>
#include <string_view>

namespace linecode::domain {

bool SkillHubDetail::HasScripts() const noexcept {
  return std::ranges::any_of(files, [](const SkillHubFileEntry &file) {
    const std::string_view path{file.path};
    return path.starts_with("scripts/") || path.ends_with(".sh");
  });
}

} // namespace linecode::domain
