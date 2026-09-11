#include "presentation/skill_hub_presentation.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <concepts>
#include <stdexcept>
#include <variant>

#include <app_resources.h>
#include <huxerui/data.h>

namespace linecode::presentation {
namespace {

using D = domain::SkillHubDestination;

const std::array kDestinations{
    SkillHubDestinationPresentation{D::account, app::strings::skillhub_entry_profile,
                                    app::strings::skillhub_account,
                                    app::strings::skillhub_entry_profile_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard"},
    SkillHubDestinationPresentation{D::stars, app::strings::skillhub_entry_my_stars,
                                    app::strings::skillhub_stars,
                                    app::strings::skillhub_entry_my_stars_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard/stars"},
    SkillHubDestinationPresentation{D::following, app::strings::skillhub_entry_following,
                                    app::strings::skillhub_following,
                                    app::strings::skillhub_entry_following_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard/following"},
    SkillHubDestinationPresentation{D::notifications, app::strings::skillhub_entry_notifications,
                                    app::strings::skillhub_notifications,
                                    app::strings::skillhub_entry_notifications_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/notifications"},
    SkillHubDestinationPresentation{D::settings, app::strings::skillhub_entry_account_settings,
                                    app::strings::skillhub_settings,
                                    app::strings::skillhub_entry_account_settings_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard/settings"},
    SkillHubDestinationPresentation{D::verify, app::strings::skillhub_entry_verify_identity,
                                    app::strings::skillhub_verify,
                                    app::strings::skillhub_entry_verify_identity_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard/verify"},
    SkillHubDestinationPresentation{D::tokens, app::strings::skillhub_entry_api_token,
                                    app::strings::skillhub_entry_api_token,
                                    app::strings::skillhub_entry_api_token_desc,
                                    app::strings::skillhub_section_account_social,
                                    "/dashboard/keys"},
    SkillHubDestinationPresentation{D::creator, app::strings::skillhub_entry_creator_center,
                                    app::strings::skillhub_creator,
                                    app::strings::skillhub_entry_creator_center_desc,
                                    app::strings::skillhub_section_creator,
                                    "/dashboard"},
    SkillHubDestinationPresentation{D::publish, app::strings::skillhub_entry_publish_workbench,
                                    app::strings::skillhub_publish,
                                    app::strings::skillhub_entry_publish_workbench_desc,
                                    app::strings::skillhub_section_creator,
                                    "/dashboard/publish"},
    SkillHubDestinationPresentation{D::skillsets, app::strings::skillhub_entry_skillset,
                                    app::strings::skillhub_entry_skillset,
                                    app::strings::skillhub_entry_skillset_desc,
                                    app::strings::skillhub_section_discover,
                                    "/skillspackage"},
    SkillHubDestinationPresentation{D::mcp, app::strings::skillhub_entry_mcp_server,
                                    app::strings::skillhub_entry_mcp_server,
                                    app::strings::skillhub_entry_mcp_server_desc,
                                    app::strings::skillhub_section_discover, "/mcp"},
    SkillHubDestinationPresentation{D::skill_hunt, app::strings::skillhub_entry_skill_hunt,
                                    app::strings::skillhub_entry_skill_hunt,
                                    app::strings::skillhub_entry_skill_hunt_desc,
                                    app::strings::skillhub_section_discover,
                                    "/skill-hunt"},
    SkillHubDestinationPresentation{D::contest, app::strings::skillhub_entry_contest,
                                    app::strings::skillhub_contest,
                                    app::strings::skillhub_entry_contest_desc,
                                    app::strings::skillhub_section_discover,
                                    "/contest"},
    SkillHubDestinationPresentation{D::enterprises, app::strings::skillhub_entry_enterprise_square,
                                    app::strings::skillhub_enterprises,
                                    app::strings::skillhub_entry_enterprise_square_desc,
                                    app::strings::skillhub_section_discover,
                                    "/enterprise-zone"},
    SkillHubDestinationPresentation{D::enterprise_dashboard,
                                    app::strings::skillhub_entry_enterprise_dashboard,
                                    app::strings::skillhub_enterprise_dashboard,
                                    app::strings::skillhub_entry_enterprise_dashboard_desc,
                                    app::strings::skillhub_section_enterprise_platform,
                                    "/enterprise/dashboard"},
    SkillHubDestinationPresentation{D::enterprise_publish,
                                    app::strings::skillhub_entry_enterprise_publish,
                                    app::strings::skillhub_enterprise_publish,
                                    app::strings::skillhub_entry_enterprise_publish_desc,
                                    app::strings::skillhub_section_enterprise_platform,
                                    "/enterprise/dashboard/publish"},
    SkillHubDestinationPresentation{D::merchant, app::strings::skillhub_entry_merchant,
                                    app::strings::skillhub_merchant,
                                    app::strings::skillhub_entry_merchant_desc,
                                    app::strings::skillhub_section_enterprise_platform,
                                    "/admin/merchant"},
    SkillHubDestinationPresentation{D::admin, app::strings::skillhub_entry_admin,
                                    app::strings::skillhub_admin,
                                    app::strings::skillhub_entry_admin_desc,
                                    app::strings::skillhub_section_enterprise_platform,
                                    "/admin"},
    SkillHubDestinationPresentation{D::admin_reviews,
                                    app::strings::skillhub_entry_admin_reviews,
                                    app::strings::skillhub_admin_reviews,
                                    app::strings::skillhub_entry_admin_reviews_desc,
                                    app::strings::skillhub_section_enterprise_platform,
                                    "/admin/skill-reviews"},
};

std::string Lower(std::string_view value) {
  std::string result(value);
  std::ranges::transform(result, result.begin(), [](unsigned char character) {
    return static_cast<char>(std::tolower(character));
  });
  return result;
}

bool HasSuffix(std::string_view path, std::span<const std::string_view> suffixes) {
  const std::string lower = Lower(path);
  return std::ranges::any_of(suffixes,
                            [&](std::string_view value) { return lower.ends_with(value); });
}

struct FileKindRule final {
  std::span<const std::string_view> suffixes;
  SkillHubFileKind kind;
};

constexpr std::array kTextSuffixes{std::string_view{".md"},
                                   std::string_view{".txt"}};
constexpr std::array kCodeSuffixes{
    std::string_view{".json"}, std::string_view{".js"},
    std::string_view{".java"}, std::string_view{".py"},
    std::string_view{".sh"}, std::string_view{".xml"},
    std::string_view{".yml"}, std::string_view{".yaml"}};
constexpr std::array kFileKindRules{
    FileKindRule{kTextSuffixes, SkillHubFileKind::text},
    FileKindRule{kCodeSuffixes, SkillHubFileKind::code},
};

} // namespace

std::span<const SkillHubDestinationPresentation>
SkillHubDestinations() noexcept {
  return kDestinations;
}

const SkillHubDestinationPresentation &
SkillHubDestinationFor(const domain::SkillHubDestination destination) {
  const auto found = std::ranges::find(kDestinations, destination,
                                      &SkillHubDestinationPresentation::destination);
  if (found == kDestinations.end())
    throw std::invalid_argument("unknown SkillHub destination");
  return *found;
}

bool IsSafeSkillHubSegment(const std::string_view value) noexcept {
  if (value.empty() || value.size() > 128 ||
      std::isalnum(static_cast<unsigned char>(value.front())) == 0)
    return false;
  return std::ranges::all_of(value, [](const unsigned char character) {
    return std::isalnum(character) != 0 || character == '.' ||
           character == '_' || character == '-';
  });
}

std::string SkillHubSitePath(const domain::SkillHubSiteRoute &route) {
  return std::visit(
      [](const auto &target) -> std::string {
        using T = std::remove_cvref_t<decltype(target)>;
        if constexpr (std::same_as<T, domain::SkillHubSiteRoute::Destination>) {
          return std::string{SkillHubDestinationFor(target.value).path};
        } else {
          if (!IsSafeSkillHubSegment(target.name_space) ||
              !IsSafeSkillHubSegment(target.slug))
            throw std::invalid_argument("invalid SkillHub skill route");
          return "/skills/" + target.name_space + "/" + target.slug;
        }
      },
      route.target);
}

bool IsAllowedSkillHubUrl(const std::string_view value) noexcept {
  const auto uri = huxerui::Uri::Parse(value);
  if (!uri || Lower(uri->Scheme()) != "https")
    return false;
  auto authority = uri->Authority();
  if (!authority)
    return false;
  std::string host = Lower(*authority);
  if (const auto at = host.rfind('@'); at != std::string::npos)
    host.erase(0, at + 1);
  if (const auto colon = host.rfind(':'); colon != std::string::npos)
    host.erase(colon);
  constexpr std::array allowed{"skillhub.cn", "www.skillhub.cn",
                               "api.skillhub.cn", "workspace.tencent.com",
                               "account.tencent.com"};
  return std::ranges::find(allowed, host) != allowed.end();
}

SkillHubFileKind SkillHubFileKindForPath(const std::string_view path) noexcept {
  const auto rule = std::ranges::find_if(kFileKindRules, [&](const auto &entry) {
    return HasSuffix(path, entry.suffixes);
  });
  return rule == kFileKindRules.end() ? SkillHubFileKind::other : rule->kind;
}

bool IsSkillHubMarkdownPath(const std::string_view path) noexcept {
  constexpr std::array suffixes{std::string_view{".md"}};
  return HasSuffix(path, suffixes);
}

bool IsSkillHubTextPreviewable(const std::string_view path) noexcept {
  constexpr std::array suffixes{
      std::string_view{".md"}, std::string_view{".txt"},
      std::string_view{".json"}, std::string_view{".js"},
      std::string_view{".ts"}, std::string_view{".java"},
      std::string_view{".py"}, std::string_view{".sh"},
      std::string_view{".xml"}, std::string_view{".yml"},
      std::string_view{".yaml"}, std::string_view{".toml"},
      std::string_view{".ini"}, std::string_view{".properties"},
      std::string_view{".csv"}};
  return HasSuffix(path, suffixes);
}

} // namespace linecode::presentation
