#include "infrastructure/skill_hub_codec.h"

#include <algorithm>
#include <array>
#include <charconv>
#include <cctype>
#include <cmath>
#include <cstddef>
#include <cstdint>
#include <limits>
#include <ranges>
#include <string>
#include <utility>

#include <huxerui/data.h>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;
constexpr std::string_view kApiRoot = "https://api.skillhub.cn";
constexpr std::size_t kMaximumCookieCharacters = 16U * 1024U;
constexpr std::size_t kMaximumSessionBodyBytes = 16U * 1024U;

SkillHubCodecError Error(std::string message) {
  return {.message = std::move(message)};
}

std::string Trim(std::string_view value) {
  while (!value.empty() &&
         std::isspace(static_cast<unsigned char>(value.front())) != 0)
    value.remove_prefix(1);
  while (!value.empty() &&
         std::isspace(static_cast<unsigned char>(value.back())) != 0)
    value.remove_suffix(1);
  return std::string{value};
}

std::string Encode(std::string_view value) {
  constexpr std::string_view hexadecimal = "0123456789ABCDEF";
  std::string output;
  output.reserve(value.size());
  for (const unsigned char byte : value) {
    if (std::isalnum(byte) != 0 || byte == '-' || byte == '_' || byte == '.' ||
        byte == '~') {
      output.push_back(static_cast<char>(byte));
    } else {
      output.push_back('%');
      output.push_back(hexadecimal[byte >> 4U]);
      output.push_back(hexadecimal[byte & 0x0FU]);
    }
  }
  return output;
}

huxerui::Bytes BytesFromString(const std::string_view value) {
  const auto *first = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(first, first + value.size());
}

std::string NamespaceQuery(const std::string_view raw,
                           const bool has_query = false) {
  const auto value = Trim(raw);
  return value.empty() ? std::string{}
                       : std::string{has_query ? "&namespace=" : "?namespace="} +
                             Encode(value);
}

void AppendQuery(std::string &url, const std::string_view name,
                 const std::string_view raw_value) {
  const auto value = Trim(raw_value);
  if (!value.empty())
    url += "&" + std::string{name} + "=" + Encode(value);
}

const json::Object *Object(const json::Object &parent,
                           const std::string_view key) {
  return json::AsObject(json::Find(parent, key));
}

const json::Array *Array(const json::Object &parent,
                         const std::string_view key) {
  return json::AsArray(json::Find(parent, key));
}

std::string String(const json::Object &object, const std::string_view key) {
  const auto *value = json::AsString(json::Find(object, key));
  return value ? *value : std::string{};
}

std::string Prefer(std::string first, std::string second) {
  first = Trim(first);
  return first.empty() ? Trim(second) : first;
}

bool AsciiEqualsIgnoreCase(const std::string_view left,
                           const std::string_view right) {
  return left.size() == right.size() &&
         std::ranges::equal(
             left, right, [](const unsigned char a, const unsigned char b) {
               return std::tolower(a) == std::tolower(b);
             });
}

std::int64_t Integer(const json::Object &object, const std::string_view key) {
  const auto *value = json::Find(object, key);
  if (!value)
    return 0;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    return std::max<std::int64_t>(0, *integer);
  if (const auto *number = std::get_if<double>(value)) {
    if (std::isfinite(*number) && *number > 0 &&
        *number <= static_cast<double>(std::numeric_limits<std::int64_t>::max()))
      return static_cast<std::int64_t>(*number);
  }
  return 0;
}

std::int64_t SignedInteger(const json::Object &object,
                           const std::string_view key) {
  const auto *value = json::Find(object, key);
  if (!value)
    return 0;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    return *integer;
  if (const auto *number = std::get_if<double>(value)) {
    if (std::isfinite(*number) &&
        *number >= static_cast<double>(std::numeric_limits<std::int64_t>::min()) &&
        *number <= static_cast<double>(std::numeric_limits<std::int64_t>::max()))
      return static_cast<std::int64_t>(*number);
  }
  return 0;
}

double Number(const json::Object &object, const std::string_view key) {
  const auto *value = json::Find(object, key);
  if (!value)
    return 0;
  if (const auto *number = std::get_if<double>(value))
    return std::isfinite(*number) ? std::max(0.0, *number) : 0;
  if (const auto *integer = std::get_if<std::int64_t>(value))
    return static_cast<double>(std::max<std::int64_t>(0, *integer));
  return 0;
}

bool Boolean(const json::Object &object, const std::string_view key) {
  const auto *value = json::Find(object, key);
  if (const auto *boolean = value ? std::get_if<bool>(value) : nullptr)
    return *boolean;
  return false;
}

std::vector<std::string> Strings(const json::Array *array) {
  std::vector<std::string> output;
  if (!array)
    return output;
  for (const auto &value : *array) {
    const auto *text = json::AsString(&value);
    if (text && !Trim(*text).empty())
      output.push_back(Trim(*text));
  }
  return output;
}

std::vector<std::string> Subcategories(const json::Array *array) {
  std::vector<std::string> output;
  if (!array)
    return output;
  for (const auto &value : *array) {
    const auto *object = json::AsObject(&value);
    if (!object)
      continue;
    auto name = Trim(String(*object, "name"));
    if (!name.empty())
      output.push_back(std::move(name));
  }
  return output;
}

domain::SkillHubSummary Summary(const json::Object &value) {
  const auto *name_space = Object(value, "namespace");
  const auto *labels = Object(value, "labels");
  return {
      .slug = Trim(String(value, "slug")),
      .name = Prefer(String(value, "name"), String(value, "slug")),
      .description =
          Prefer(String(value, "description_zh"), String(value, "description")),
      .owner = Prefer(String(value, "ownerName"),
                      name_space ? String(*name_space, "displayName")
                                 : std::string{}),
      .category = String(value, "category"),
      .source = String(value, "source"),
      .version = String(value, "version"),
      .icon_url = String(value, "iconUrl"),
      .downloads = Integer(value, "downloads"),
      .stars = Integer(value, "stars"),
      .updated_at = Integer(value, "updated_at"),
      .verified = Boolean(value, "verified"),
      .requires_api_key = labels &&
                          AsciiEqualsIgnoreCase(
                              String(*labels, "requires_api_key"), "true"),
      .subcategories = Subcategories(Array(value, "subCategories")),
  };
}

const json::Object *PreferredSecurity(const json::Object *reports) {
  if (!reports)
    return nullptr;
  const json::Object *benign{};
  for (const std::string_view provider : {"keen", "sanbu"}) {
    const auto *report = Object(*reports, provider);
    if (!report)
      continue;
    if (!AsciiEqualsIgnoreCase(String(*report, "status"), "benign"))
      return report;
    benign = report;
  }
  return benign;
}

domain::SkillHubComment Comment(const json::Object &value) {
  const auto *user = Object(value, "user");
  const auto *replies = Object(value, "replies");
  std::vector<domain::SkillHubComment> children;
  if (replies) {
    if (const auto *preview = Array(*replies, "preview")) {
      for (const auto &child : *preview) {
        if (const auto *object = json::AsObject(&child))
          children.push_back(Comment(*object));
      }
    }
  }
  return {
      .id = Integer(value, "id"),
      .user_id = user ? std::max(Integer(*user, "id"), Integer(value, "userId"))
                      : Integer(value, "userId"),
      .parent_id = Integer(value, "parentId"),
      .author = Prefer(user ? String(*user, "displayName") : std::string{},
                       String(value, "authorName")),
      .handle = user ? String(*user, "handle") : std::string{},
      .avatar_url = user ? String(*user, "avatarUrl")
                         : String(value, "authorAvatar"),
      .content = String(value, "content"),
      .created_at = Integer(value, "createdAt"),
      .like_count = Integer(value, "likeCount"),
      .reply_count = replies ? Integer(*replies, "total")
                             : Integer(value, "replyCount"),
      .liked = Boolean(value, "liked"),
      .status = String(value, "status"),
      .image_urls = Strings(Array(value, "imageUrls")),
      .replies = std::move(children),
  };
}

template <class Value> struct DecodedValue {
  using type = Value;
  static constexpr bool is_result = false;
};

template <class Value>
struct DecodedValue<std::expected<Value, SkillHubCodecError>> {
  using type = Value;
  static constexpr bool is_result = true;
};

template <class Decoder>
auto DecodeObject(const std::string_view text, Decoder decoder)
    -> SkillHubCodecResult<typename DecodedValue<
        std::invoke_result_t<Decoder, const json::Object &>>::type> {
  using Decoded =
      std::invoke_result_t<Decoder, const json::Object &>;
  auto parsed = json::Parse(text);
  if (!parsed)
    return std::unexpected(Error("invalid SkillHub JSON: " +
                                 parsed.error().message));
  const auto *root = json::AsObject(&*parsed);
  if (!root)
    return std::unexpected(Error("SkillHub response must be a JSON object"));
  if constexpr (DecodedValue<Decoded>::is_result)
    return decoder(*root);
  else
    return SkillHubCodecResult<Decoded>{decoder(*root)};
}

SkillHubHttpRequest Get(std::string url) {
  return {.url = std::move(url),
          .method = SkillHubMethod::get,
          .headers = {{"Accept", "application/json"}},
          .body = {}};
}

} // namespace

SkillHubCodecResult<std::string>
ValidateSkillHubSlug(const std::string_view raw) {
  const auto value = Trim(raw);
  if (value.empty() || value.size() > 128 ||
      std::isalnum(static_cast<unsigned char>(value.front())) == 0 ||
      !std::ranges::all_of(value, [](const unsigned char c) {
        return std::isalnum(c) != 0 || c == '.' || c == '_' || c == '-';
      }))
    return std::unexpected(Error("invalid SkillHub slug"));
  return value;
}

SkillHubCodecResult<std::string>
ValidateSkillHubVersion(const std::string_view raw) {
  const auto value = Trim(raw);
  if (value.empty() || value.size() > 64 ||
      std::isalnum(static_cast<unsigned char>(value.front())) == 0 ||
      !std::ranges::all_of(value, [](const unsigned char c) {
        return std::isalnum(c) != 0 || c == '.' || c == '_' || c == '+' ||
               c == '-';
      }))
    return std::unexpected(Error("invalid SkillHub version"));
  return value;
}

SkillHubCodecResult<std::string>
ValidateSkillHubFilePath(const std::string_view raw) {
  const auto value = Trim(raw);
  if (value.empty() || value.size() > 512 || value.front() == '/' ||
      value.contains('\\') || value.contains('\0'))
    return std::unexpected(Error("invalid SkillHub file path"));
  for (const auto segment : value | std::views::split('/')) {
    const std::string_view part{segment.begin(), segment.end()};
    if (part.empty() || part == "." || part == "..")
      return std::unexpected(Error("invalid SkillHub file path"));
  }
  return value;
}

SkillHubCodecResult<std::string>
ValidateSkillHubIconUrl(const std::string_view raw) {
  const auto parsed = huxerui::Uri::Parse(Trim(raw));
  if (!parsed || parsed->Scheme() != "https" || !parsed->Authority())
    return std::unexpected(Error("invalid SkillHub icon URL"));
  std::string authority{*parsed->Authority()};
  const auto at = authority.rfind('@');
  if (at != std::string::npos)
    authority.erase(0, at + 1);
  if (!authority.empty() && authority.front() == '[') {
    const auto end = authority.find(']');
    authority = end == std::string::npos ? std::string{}
                                         : authority.substr(1, end - 1);
  } else if (const auto colon = authority.rfind(':'); colon != std::string::npos) {
    authority.resize(colon);
  }
  std::ranges::transform(authority, authority.begin(),
                         [](const unsigned char c) { return std::tolower(c); });
  constexpr std::array allowed{
      std::string_view{"skillhub.cn"}, std::string_view{"www.skillhub.cn"},
      std::string_view{"api.skillhub.cn"},
      std::string_view{"cloudcache.tencent-cloud.com"},
      std::string_view{"skillhub-1388575217.cos.accelerate.myqcloud.com"}};
  if (std::ranges::find(allowed, authority) == allowed.end())
    return std::unexpected(Error("invalid SkillHub icon URL"));
  return parsed->ToString();
}

SkillHubCodecResult<std::string>
ValidateSkillHubCookie(const std::string_view raw) {
  const auto value = Trim(raw);
  if (value.size() > kMaximumCookieCharacters || value.contains('\r') ||
      value.contains('\n') || value.contains('\0') ||
      std::ranges::any_of(value, [](const unsigned char byte) {
        return byte == 0x7FU || (byte < 0x20U && byte != '\t');
      }))
    return std::unexpected(Error("invalid SkillHub session cookie"));
  return value;
}

SkillHubHttpRequest
BuildSkillHubListRequest(application::SkillHubListQuery query) {
  query.page = std::max(1, query.page);
  query.page_size = std::clamp(query.page_size, 1, 50);
  std::string url = std::string{kApiRoot} + "/api/skills?page=" +
                    std::to_string(query.page) + "&pageSize=" +
                    std::to_string(query.page_size);
  AppendQuery(url, "keyword", query.keyword);
  AppendQuery(url, "category", query.category);
  if (query.source != "all")
    AppendQuery(url, "source", query.source);
  AppendQuery(url, "sortBy", query.sort_by);
  AppendQuery(url, "order", query.order);
  return Get(std::move(url));
}

#define LINECODE_SKILLHUB_SLUG_REQUEST(Name, Suffix)                         \
  SkillHubCodecResult<SkillHubHttpRequest> Name(                             \
      const std::string_view raw_slug, const std::string_view name_space) {  \
    auto slug = ValidateSkillHubSlug(raw_slug);                              \
    if (!slug)                                                               \
      return std::unexpected(slug.error());                                  \
    return Get(std::string{kApiRoot} + "/api/v1/skills/" + Encode(*slug) + \
               Suffix + NamespaceQuery(name_space));                         \
  }

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubDetailRequest(const std::string_view raw_slug) {
  auto slug = ValidateSkillHubSlug(raw_slug);
  if (!slug)
    return std::unexpected(slug.error());
  return Get(std::string{kApiRoot} + "/api/v1/skills/" + Encode(*slug));
}

LINECODE_SKILLHUB_SLUG_REQUEST(BuildSkillHubFilesRequest, "/files")
LINECODE_SKILLHUB_SLUG_REQUEST(BuildSkillHubCommentsRequest, "/comments")
LINECODE_SKILLHUB_SLUG_REQUEST(BuildSkillHubVersionsRequest, "/versions")
LINECODE_SKILLHUB_SLUG_REQUEST(BuildSkillHubEvaluationRequest, "/evaluation")
LINECODE_SKILLHUB_SLUG_REQUEST(BuildSkillHubTestCasesRequest, "/testcases")

#undef LINECODE_SKILLHUB_SLUG_REQUEST

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubFileRequest(const std::string_view raw_slug,
                         const std::string_view raw_version,
                         const std::string_view raw_path,
                         const std::string_view name_space) {
  auto slug = ValidateSkillHubSlug(raw_slug);
  auto version = ValidateSkillHubVersion(raw_version);
  auto path = ValidateSkillHubFilePath(raw_path);
  if (!slug)
    return std::unexpected(slug.error());
  if (!version)
    return std::unexpected(version.error());
  if (!path)
    return std::unexpected(path.error());
  return Get(std::string{kApiRoot} + "/api/v1/skills/" + Encode(*slug) +
             "/file?version=" + Encode(*version) + "&path=" + Encode(*path) +
             NamespaceQuery(name_space, true));
}

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubRepliesRequest(const std::string_view raw_slug,
                            const std::int64_t comment_id,
                            const std::string_view name_space) {
  auto slug = ValidateSkillHubSlug(raw_slug);
  if (!slug)
    return std::unexpected(slug.error());
  if (comment_id <= 0)
    return std::unexpected(Error("invalid SkillHub comment id"));
  return Get(std::string{kApiRoot} + "/api/v1/skills/" + Encode(*slug) +
             "/comments/" + std::to_string(comment_id) + "/replies" +
             NamespaceQuery(name_space));
}

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubDownloadRequest(const std::string_view raw_slug,
                             const std::string_view raw_version) {
  auto slug = ValidateSkillHubSlug(raw_slug);
  auto version = ValidateSkillHubVersion(raw_version);
  if (!slug)
    return std::unexpected(slug.error());
  if (!version)
    return std::unexpected(version.error());
  return Get(std::string{kApiRoot} + "/api/v1/download?slug=" + Encode(*slug) +
             "&version=" + Encode(*version));
}

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubIconRequest(const std::string_view raw_url) {
  auto url = ValidateSkillHubIconUrl(raw_url);
  if (!url)
    return std::unexpected(url.error());
  return Get(std::move(*url));
}

SkillHubCodecResult<SkillHubHttpRequest>
BuildSkillHubSessionRequest(const SkillHubMethod method, std::string path,
                            const std::string_view raw_cookie,
                            const std::string_view json_body) {
  auto cookie = ValidateSkillHubCookie(raw_cookie);
  if (!cookie)
    return std::unexpected(cookie.error());
  if (!path.starts_with("/api/") || path.contains('\r') ||
      path.contains('\n') || path.contains('\0'))
    return std::unexpected(Error("invalid SkillHub API path"));
  if (json_body.size() > kMaximumSessionBodyBytes)
    return std::unexpected(Error("SkillHub request body exceeds 16 KiB"));
  SkillHubHttpRequest request{
      .url = std::string{kApiRoot} + std::move(path),
      .method = method,
      .headers = {{"Accept", "application/json"}},
      .body = BytesFromString(json_body),
  };
  if (!cookie->empty())
    request.headers.emplace_back("Cookie", std::move(*cookie));
  if (!json_body.empty())
    request.headers.emplace_back("Content-Type", "application/json");
  return request;
}

SkillHubCodecResult<domain::SkillHubPage>
DecodeSkillHubPage(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root)
                          -> SkillHubCodecResult<domain::SkillHubPage> {
    const auto code = SignedInteger(root, "code");
    if (json::Find(root, "code") && code != 0)
      return std::unexpected(Error("SkillHub API error: " +
                                   String(root, "message")));
    const auto *data = Object(root, "data");
    if (!data)
      return std::unexpected(Error("SkillHub response is missing data"));
    std::vector<domain::SkillHubSummary> skills;
    if (const auto *values = Array(*data, "skills")) {
      for (const auto &value : *values) {
        const auto *object = json::AsObject(&value);
        if (!object)
          continue;
        auto summary = Summary(*object);
        if (!ValidateSkillHubSlug(summary.slug))
          return std::unexpected(Error("invalid SkillHub slug"));
        skills.push_back(std::move(summary));
      }
    }
    return domain::SkillHubPage{.skills = std::move(skills),
                                .total = Integer(*data, "total")};
  });
}

SkillHubCodecResult<domain::SkillHubDetail>
DecodeSkillHubDetail(const std::string_view text,
                     const std::string_view fallback_slug) {
  return DecodeObject(text, [fallback_slug](const json::Object &root)
                          -> SkillHubCodecResult<domain::SkillHubDetail> {
    const auto *skill = Object(root, "skill");
    if (!skill)
      return std::unexpected(Error("SkillHub detail is missing skill"));
    const auto *latest = Object(root, "latestVersion");
    const auto *name_space = Object(root, "namespace");
    const auto *owner = Object(root, "owner");
    const auto *publisher = Object(root, "publisher");
    const auto *stats = Object(*skill, "stats");
    const auto *labels = Object(*skill, "labels");
    const auto *security = PreferredSecurity(Object(root, "securityReports"));
    domain::SkillHubDetail detail;
    detail.slug = String(*skill, "slug");
    if (detail.slug.empty())
      detail.slug = String(root, "slug");
    if (detail.slug.empty())
      detail.slug = Trim(fallback_slug);
    if (!ValidateSkillHubSlug(detail.slug))
      return std::unexpected(Error("invalid SkillHub slug"));
    detail.name = Prefer(String(*skill, "displayName"), detail.slug);
    detail.description =
        Prefer(String(*skill, "summary_zh"), String(*skill, "summary"));
    detail.owner = owner ? Prefer(String(*owner, "displayName"),
                                  String(*owner, "handle"))
                         : std::string{};
    detail.category = String(*skill, "category");
    detail.source = String(*skill, "source");
    detail.version = latest ? String(*latest, "version") : std::string{};
    detail.icon_url = String(*skill, "iconUrl");
    detail.downloads = stats ? Integer(*stats, "downloads") : 0;
    detail.stars = stats ? Integer(*stats, "stars") : 0;
    detail.updated_at = Integer(*skill, "updatedAt");
    detail.verified = Boolean(*skill, "verified") ||
                      Boolean(*skill, "isAuthorVerified");
    detail.requires_api_key = labels &&
                              AsciiEqualsIgnoreCase(
                                  String(*labels, "requires_api_key"), "true");
    detail.subcategories = Subcategories(Array(*skill, "subCategories"));
    detail.canonical_name = name_space ? String(*name_space, "canonicalName")
                                       : std::string{};
    detail.namespace_handle = name_space ? String(*name_space, "handle")
                                         : std::string{};
    detail.publisher = publisher ? String(*publisher, "name") : std::string{};
    detail.security_status = security ? String(*security, "status")
                                      : std::string{};
    detail.security_status_text = security ? String(*security, "statusText")
                                           : std::string{};
    detail.tags = Strings(Array(*skill, "tags"));
    return detail;
  });
}

SkillHubCodecResult<std::vector<domain::SkillHubFileEntry>>
DecodeSkillHubFiles(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    std::vector<domain::SkillHubFileEntry> files;
    if (const auto *values = Array(root, "files")) {
      for (const auto &value : *values) {
        if (const auto *object = json::AsObject(&value))
          files.push_back({.path = String(*object, "path"),
                           .sha256 = String(*object, "sha256"),
                           .size = Integer(*object, "size")});
      }
    }
    return files;
  });
}

SkillHubCodecResult<std::vector<domain::SkillHubComment>>
DecodeSkillHubComments(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    std::vector<domain::SkillHubComment> comments;
    if (const auto *items = Array(root, "items")) {
      for (const auto &value : *items) {
        if (const auto *object = json::AsObject(&value))
          comments.push_back(Comment(*object));
      }
    }
    return comments;
  });
}

SkillHubCodecResult<std::vector<domain::SkillHubVersion>>
DecodeSkillHubVersions(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    std::vector<domain::SkillHubVersion> versions;
    if (const auto *values = Array(root, "versions")) {
      for (const auto &value : *values) {
        const auto *object = json::AsObject(&value);
        if (!object)
          continue;
        const auto *security =
            PreferredSecurity(Object(*object, "securityReports"));
        versions.push_back({
            .version = String(*object, "version"),
            .changelog = String(*object, "changelog"),
            .created_at = Integer(*object, "createdAt"),
            .security_status =
                security ? String(*security, "status") : std::string{},
            .security_status_text =
                security ? String(*security, "statusText") : std::string{},
        });
      }
    }
    return versions;
  });
}

SkillHubCodecResult<domain::SkillHubEvaluation>
DecodeSkillHubEvaluation(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    domain::SkillHubEvaluation output;
    output.status = root.empty() ? std::string{} : "completed";
    output.summary = Prefer(String(root, "userSummary"), String(root, "summary"));
    const auto *dimensions = Object(root, "dimensions");
    if (!dimensions)
      return output;
    double score{};
    int count{};
    constexpr std::array keys{
        std::string_view{"effectiveness"}, std::string_view{"reliability"},
        std::string_view{"adaptability"}, std::string_view{"convention"},
        std::string_view{"trust"}};
    for (const auto key : keys) {
      const auto *dimension = Object(*dimensions, key);
      if (!dimension)
        continue;
      const auto value = Number(*dimension, "score");
      if (value > 0) {
        score += value;
        ++count;
      }
      auto highlight = Trim(String(*dimension, "userReason"));
      auto suggestion = Trim(String(*dimension, "suggestion"));
      if (!highlight.empty())
        output.highlights.push_back(std::move(highlight));
      if (!suggestion.empty())
        output.suggestions.push_back(std::move(suggestion));
    }
    output.score = count == 0 ? 0 : score / count;
    return output;
  });
}

SkillHubCodecResult<std::vector<domain::SkillHubTestCase>>
DecodeSkillHubTestCases(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    std::vector<domain::SkillHubTestCase> cases;
    if (const auto *values = Array(root, "testcases")) {
      std::size_t index{};
      for (const auto &value : *values) {
        const auto *object = json::AsObject(&value);
        if (!object)
          continue;
        cases.push_back({.title = "测试用例 " + std::to_string(++index),
                         .prompt = String(*object, "question"),
                         .expected = String(*object, "answer")});
      }
    }
    return cases;
  });
}

SkillHubCodecResult<domain::SkillHubSession>
DecodeSkillHubSession(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root)
                          -> SkillHubCodecResult<domain::SkillHubSession> {
    const auto *user = Object(root, "user");
    if (!user)
      user = &root;
    auto handle = Prefer(String(*user, "handle"),
                         Prefer(String(*user, "username"),
                                String(*user, "userName")));
    auto display = Prefer(
        String(*user, "displayName"),
        Prefer(String(*user, "nickname"),
               Prefer(String(*user, "name"), handle)));
    if (display.empty() && handle.empty())
      return std::unexpected(Error("SkillHub account is incomplete"));
    return domain::SkillHubSession{
        .authenticated = true,
        .account = {.display_name = std::move(display),
                    .handle = std::move(handle),
                    .avatar_url = Prefer(
                        String(*user, "avatarUrl"),
                        Prefer(String(*user, "avatar"),
                               String(*user, "image")))}};
  });
}

SkillHubCodecResult<domain::SkillHubComment>
DecodeSkillHubComment(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    return Comment(root);
  });
}

SkillHubCodecResult<bool> DecodeSkillHubStarred(const std::string_view text) {
  return DecodeObject(text, [](const json::Object &root) {
    return Boolean(root, "starred");
  });
}

SkillHubCodecResult<application::GitHubSkillSource>
ResolveGitHubSkillSource(const std::string_view raw) {
  const auto parsed = huxerui::Uri::Parse(Trim(raw));
  if (!parsed || !AsciiEqualsIgnoreCase(parsed->Scheme(), "https") ||
      !parsed->Authority())
    return std::unexpected(Error("invalid GitHub Skill URL"));
  std::string host{*parsed->Authority()};
  std::ranges::transform(host, host.begin(),
                         [](const unsigned char c) { return std::tolower(c); });
  std::string path{parsed->Path()};
  if (path.starts_with('/'))
    path.erase(0, 1);
  std::vector<std::string> parts;
  for (const auto segment : path | std::views::split('/'))
    parts.emplace_back(segment.begin(), segment.end());
  if (host == "raw.githubusercontent.com") {
    if (parts.size() < 3 || parts[0].empty() || parts[1].empty() ||
        parts[2].empty())
      return std::unexpected(Error("invalid GitHub Skill URL"));
    auto repo = parts[1];
    if (repo.ends_with(".git"))
      repo.resize(repo.size() - 4);
    std::string url = "https://raw.githubusercontent.com/" + parts[0] + "/" +
                      repo + "/" + parts[2];
    for (std::size_t index = 3; index < parts.size(); ++index)
      url += "/" + parts[index];
    if (parts.size() == 3)
      url += "/SKILL.md";
    return application::GitHubSkillSource{.suggested_name = std::move(repo),
                                          .primary_url = std::move(url),
                                          .fallback_url = std::nullopt,
                                          .markdown = true};
  }
  if (host != "github.com" && host != "www.github.com")
    return std::unexpected(Error("invalid GitHub Skill URL"));
  if (parts.size() < 2 || parts[0].empty() || parts[1].empty())
    return std::unexpected(Error("invalid GitHub Skill URL"));
  auto repo = parts[1];
  if (repo.ends_with(".git"))
    repo.resize(repo.size() - 4);
  if (parts.size() >= 5 && parts[2] == "blob" &&
      domain::IsSkillMarkdownName(parts.back())) {
    std::string url = "https://raw.githubusercontent.com/" + parts[0] + "/" +
                      repo + "/" + parts[3];
    for (std::size_t index = 4; index < parts.size(); ++index)
      url += "/" + parts[index];
    return application::GitHubSkillSource{.suggested_name = std::move(repo),
                                          .primary_url = std::move(url),
                                          .fallback_url = std::nullopt,
                                          .markdown = true};
  }
  const auto branch = parts.size() >= 4 && parts[2] == "tree" ? parts[3]
                                                                : "main";
  const auto base = "https://codeload.github.com/" + parts[0] + "/" + repo +
                    "/zip/refs/heads/";
  return application::GitHubSkillSource{
      .suggested_name = std::move(repo),
      .primary_url = base + branch,
      .fallback_url = branch == "main"
                          ? std::optional<std::string>{base + "master"}
                          : std::nullopt,
      .markdown = false,
  };
}

} // namespace linecode::infrastructure
