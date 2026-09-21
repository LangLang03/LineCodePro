#pragma once

#include <cstdint>
#include <expected>
#include <optional>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include <huxerui/data.h>

#include "application/ports/skill_services.h"
#include "domain/skill_hub.h"

namespace linecode::infrastructure {

enum class SkillHubMethod { get, post, delete_ };

struct SkillHubHttpRequest final {
  std::string url;
  SkillHubMethod method{SkillHubMethod::get};
  std::vector<std::pair<std::string, std::string>> headers;
  huxerui::Bytes body;

  bool operator==(const SkillHubHttpRequest &) const = default;
};

struct SkillHubCodecError final {
  std::string message;

  bool operator==(const SkillHubCodecError &) const = default;
};

template <class Value>
using SkillHubCodecResult = std::expected<Value, SkillHubCodecError>;

[[nodiscard]] SkillHubCodecResult<std::string>
ValidateSkillHubSlug(std::string_view value);
[[nodiscard]] SkillHubCodecResult<std::string>
ValidateSkillHubVersion(std::string_view value);
[[nodiscard]] SkillHubCodecResult<std::string>
ValidateSkillHubFilePath(std::string_view value);
[[nodiscard]] SkillHubCodecResult<std::string>
ValidateSkillHubIconUrl(std::string_view value);
[[nodiscard]] SkillHubCodecResult<std::string>
ValidateSkillHubCookie(std::string_view value);

[[nodiscard]] SkillHubHttpRequest
BuildSkillHubListRequest(application::SkillHubListQuery query);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubDetailRequest(std::string_view slug);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubFilesRequest(std::string_view slug, std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubFileRequest(std::string_view slug, std::string_view version,
                         std::string_view path, std::string_view name_space = {});
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubCommentsRequest(std::string_view slug,
                             std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubRepliesRequest(std::string_view slug, std::int64_t comment_id,
                            std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubVersionsRequest(std::string_view slug,
                             std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubEvaluationRequest(std::string_view slug,
                               std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubTestCasesRequest(std::string_view slug,
                              std::string_view name_space);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubDownloadRequest(std::string_view slug, std::string_view version);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubIconRequest(std::string_view url);
[[nodiscard]] SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubSessionRequest(SkillHubMethod method, std::string path,
                            std::string_view cookie,
                            std::string_view json_body = {});

[[nodiscard]] SkillHubCodecResult<domain::SkillHubPage>
DecodeSkillHubPage(std::string_view json);
[[nodiscard]] SkillHubCodecResult<domain::SkillHubDetail>
DecodeSkillHubDetail(std::string_view json,
                     std::string_view fallback_slug = {});
[[nodiscard]] SkillHubCodecResult<std::vector<domain::SkillHubFileEntry>>
DecodeSkillHubFiles(std::string_view json);
[[nodiscard]] SkillHubCodecResult<std::vector<domain::SkillHubComment>>
DecodeSkillHubComments(std::string_view json);
[[nodiscard]] SkillHubCodecResult<std::vector<domain::SkillHubVersion>>
DecodeSkillHubVersions(std::string_view json);
[[nodiscard]] SkillHubCodecResult<domain::SkillHubEvaluation>
DecodeSkillHubEvaluation(std::string_view json);
[[nodiscard]] SkillHubCodecResult<std::vector<domain::SkillHubTestCase>>
DecodeSkillHubTestCases(std::string_view json);
[[nodiscard]] SkillHubCodecResult<domain::SkillHubSession>
DecodeSkillHubSession(std::string_view json);
[[nodiscard]] SkillHubCodecResult<domain::SkillHubComment>
DecodeSkillHubComment(std::string_view json);
[[nodiscard]] SkillHubCodecResult<bool>
DecodeSkillHubStarred(std::string_view json);

[[nodiscard]] SkillHubCodecResult<application::GitHubSkillSource>
ResolveGitHubSkillSource(std::string_view url);

} // namespace linecode::infrastructure
