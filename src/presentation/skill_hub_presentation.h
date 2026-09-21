#pragma once

#include <span>
#include <string>
#include <string_view>

#include <huxerui/resource.h>

#include "domain/skill_hub_route.h"

namespace linecode::presentation {

struct SkillHubDestinationPresentation final {
  domain::SkillHubDestination destination;
  huxerui::StringResource entry_title;
  huxerui::StringResource web_title;
  huxerui::StringResource description;
  huxerui::StringResource section;
  std::string_view path;
};

[[nodiscard]] std::span<const SkillHubDestinationPresentation>
SkillHubDestinations() noexcept;
[[nodiscard]] const SkillHubDestinationPresentation &
SkillHubDestinationFor(domain::SkillHubDestination destination);
[[nodiscard]] bool IsSafeSkillHubSegment(std::string_view value) noexcept;
[[nodiscard]] std::string SkillHubSitePath(const domain::SkillHubSiteRoute &route);
[[nodiscard]] bool IsAllowedSkillHubUrl(std::string_view value) noexcept;

enum class SkillHubFileKind : std::uint8_t { text, code, other };
[[nodiscard]] SkillHubFileKind
SkillHubFileKindForPath(std::string_view path) noexcept;
[[nodiscard]] bool IsSkillHubMarkdownPath(std::string_view path) noexcept;
[[nodiscard]] bool IsSkillHubTextPreviewable(std::string_view path) noexcept;

} // namespace linecode::presentation
