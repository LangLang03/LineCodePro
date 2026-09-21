#include "infrastructure/hux_skill_hub_session_gateway.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cctype>
#include <cstddef>
#include <filesystem>
#include <random>
#include <ranges>
#include <string_view>
#include <utility>

#include <huxerui/file.h>
#include <huxerui/task.h>

#include "infrastructure/archive_json.h"
#include "infrastructure/skill_hub_codec.h"

namespace linecode::infrastructure {
namespace {

using application::SkillError;
template <class Value>
using Result = application::SkillResult<Value>;
namespace json = archive_json;

constexpr std::size_t kMaximumResponseBytes = 256U * 1024U;
constexpr std::size_t kMaximumRequestBodyBytes = 16U * 1024U;
constexpr std::size_t kMaximumPublishFiles = 200;
constexpr std::size_t kMaximumPublishFileBytes = 2U * 1024U * 1024U;
constexpr std::size_t kMaximumPublishTotalBytes = 10U * 1024U * 1024U;
constexpr std::size_t kMaximumMultipartOverhead = 512U * 1024U;
constexpr std::size_t kMaximumDisplayNameCodepoints = 100;
constexpr std::size_t kMaximumCommentCodepoints = 500;
constexpr auto kDefaultTimeout = std::chrono::seconds{30};
constexpr auto kPublishTimeout = std::chrono::seconds{60};

SkillError Error(std::string message) {
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

std::size_t Utf8Codepoints(const std::string_view value) {
  return std::ranges::count_if(value, [](const unsigned char byte) {
    return (byte & 0xC0U) != 0x80U;
  });
}

std::string Encode(std::string_view value) {
  constexpr std::string_view hexadecimal = "0123456789ABCDEF";
  std::string output;
  for (const unsigned char byte : value) {
    if (std::isalnum(byte) != 0 || byte == '-' || byte == '_' || byte == '.' ||
        byte == '~') {
      output.push_back(static_cast<char>(byte));
    } else {
      output += {'%', hexadecimal[byte >> 4U], hexadecimal[byte & 0x0FU]};
    }
  }
  return output;
}

std::string NamespaceQuery(const std::string_view raw) {
  const auto value = Trim(raw);
  return value.empty() ? std::string{} : "?namespace=" + Encode(value);
}

std::string JsonText(std::string content) {
  return json::Serialize(json::Object{{"content", std::move(content)},
                                      {"imageUrls", json::Array{}}});
}

huxerui::Bytes BytesFromString(const std::string_view value) {
  const auto *first = reinterpret_cast<const std::byte *>(value.data());
  return value.empty() ? huxerui::Bytes{}
                       : huxerui::Bytes(first, first + value.size());
}

std::vector<huxerui::HttpHeader>
Headers(std::string cookie, const bool has_json) {
  std::vector<huxerui::HttpHeader> headers{{.name = "Accept",
                                             .value = "application/json"}};
  if (!cookie.empty())
    headers.push_back({.name = "Cookie", .value = std::move(cookie)});
  if (has_json)
    headers.push_back(
        {.name = "Content-Type", .value = "application/json"});
  return headers;
}

Result<huxerui::HttpRequest>
Request(std::string path, std::string cookie,
        const huxerui::HttpMethod method = huxerui::HttpMethod::Get,
        std::string body = {},
        const std::chrono::milliseconds timeout = kDefaultTimeout) {
  auto validated_cookie = ValidateSkillHubCookie(cookie);
  if (!validated_cookie)
    return std::unexpected(Error(validated_cookie.error().message));
  if (body.size() > kMaximumRequestBodyBytes)
    return std::unexpected(Error("SkillHub request body exceeds 16 KiB"));
  return huxerui::HttpRequest{
      .url = "https://api.skillhub.cn" + std::move(path),
      .method = method,
      .headers = Headers(std::move(*validated_cookie), !body.empty()),
      .body = BytesFromString(body),
      .timeout = timeout,
  };
}

struct SessionResponse final {
  int status_code{};
  huxerui::Bytes body;
};

huxerui::Task<Result<SessionResponse>>
Send(const std::shared_ptr<huxerui::HttpClient> &http,
     huxerui::HttpRequest request) {
  auto opened = co_await http->SendStreamAsync(std::move(request));
  if (!opened.Succeeded())
    co_return std::unexpected(Error(opened.Error().message));
  auto stream = std::move(opened).Value();
  SessionResponse response{.status_code = stream.StatusCode(), .body = {}};
  while (true) {
    auto read = co_await stream.Body().ReadAsync(32U * 1024U);
    if (!read.Succeeded())
      co_return std::unexpected(Error(read.Error().message));
    auto bytes = std::move(read).Value();
    if (bytes.empty())
      break;
    if (bytes.size() >
        kMaximumResponseBytes -
            std::min(kMaximumResponseBytes, response.body.size()))
      co_return std::unexpected(
          Error("SkillHub account response exceeds 256 KiB"));
    response.body.insert(response.body.end(), bytes.begin(), bytes.end());
  }
  co_return response;
}

std::string Text(const huxerui::Bytes &bytes) {
  return {reinterpret_cast<const char *>(bytes.data()), bytes.size()};
}

Result<void> Success(const SessionResponse &response) {
  if (response.status_code == 401)
    return std::unexpected(Error("SkillHub login is required"));
  if (response.status_code < 200 || response.status_code >= 300)
    return std::unexpected(
        Error("SkillHub HTTP " + std::to_string(response.status_code)));
  return {};
}

struct PublishFile final {
  std::string path;
  huxerui::Bytes bytes;
};

bool Sensitive(const std::string_view raw_path) {
  std::string path{raw_path};
  std::ranges::transform(path, path.begin(), [](const unsigned char c) {
    return static_cast<char>(std::tolower(c));
  });
  const auto slash = path.find_last_of('/');
  const auto leaf = std::string_view{path}.substr(
      slash == std::string::npos ? 0 : slash + 1);
  return leaf == ".env" || leaf.starts_with(".env.") ||
         leaf.contains("credential") || leaf.contains("secret") ||
         leaf.ends_with(".pem") || leaf.ends_with(".key") ||
         leaf.ends_with(".p12") || leaf.ends_with(".pfx") ||
         leaf.ends_with(".jks") || leaf.ends_with(".keystore") ||
         leaf == "id_rsa" || leaf == "id_ed25519";
}

Result<void> Collect(const huxerui::File &root, const huxerui::File &current,
                     std::vector<PublishFile> &files, std::size_t &total) {
  auto children = current.ListChildren();
  if (!children.Succeeded())
    return std::unexpected(Error(children.Error().message));
  auto values = std::move(children).Value();
  std::ranges::sort(values, {}, &huxerui::File::Path);
  for (const auto &child : values) {
    std::error_code status_error;
    const auto status =
        std::filesystem::symlink_status(child.Path(), status_error);
    if (status_error || std::filesystem::is_symlink(status))
      return std::unexpected(
          Error("Skill publishing rejects symbolic links"));
    const auto relative = std::filesystem::relative(child.Path(), root.Path(),
                                                    status_error)
                              .generic_string();
    if (status_error || relative.empty() || relative.starts_with(".."))
      return std::unexpected(Error("Skill publish path escapes its root"));
    if (relative.size() > 512 || relative.contains('"') ||
        relative.contains('\r') || relative.contains('\n') ||
        relative.contains('\0'))
      return std::unexpected(Error("Skill publish path is invalid"));
    if (child.IsDirectory()) {
      auto nested = Collect(root, child, files, total);
      if (!nested)
        return nested;
      continue;
    }
    if (Sensitive(relative))
      return std::unexpected(Error("Skill contains a sensitive file: " + relative));
    auto stat = child.Stat();
    if (!stat.Succeeded() || stat.Value().type != huxerui::FileType::File)
      return std::unexpected(Error("Skill contains an unsupported entry"));
    if (stat.Value().size > kMaximumPublishFileBytes ||
        stat.Value().size > kMaximumPublishTotalBytes - total ||
        files.size() >= kMaximumPublishFiles)
      return std::unexpected(Error("Skill publish package exceeds safety limits"));
    auto bytes = child.ReadBytes();
    if (!bytes.Succeeded())
      return std::unexpected(Error(bytes.Error().message));
    if (bytes.Value().size() > kMaximumPublishFileBytes ||
        bytes.Value().size() > kMaximumPublishTotalBytes - total)
      return std::unexpected(Error("Skill publish package exceeds safety limits"));
    total += bytes.Value().size();
    files.push_back({.path = relative, .bytes = std::move(bytes).Value()});
  }
  return {};
}

Result<std::vector<PublishFile>> CollectPublishFiles(
    const domain::SkillRecord &skill) {
  if (skill.location == domain::SkillLocation::ssh)
    return std::unexpected(Error("Select a local Skill to publish"));
  const huxerui::File root{skill.root_path};
  std::error_code status_error;
  const auto root_status =
      std::filesystem::symlink_status(root.Path(), status_error);
  if (!root.IsDirectory() || status_error ||
      std::filesystem::is_symlink(root_status))
    return std::unexpected(Error("Skill directory does not exist"));
  std::vector<PublishFile> files;
  std::size_t total{};
  auto collected = Collect(root, root, files, total);
  if (!collected)
    return std::unexpected(std::move(collected.error()));
  if (!std::ranges::any_of(files, [](const PublishFile &file) {
        return file.path == "SKILL.md";
      }))
    return std::unexpected(Error("Skill publish package is missing SKILL.md"));
  return files;
}

void Append(huxerui::Bytes &output, const std::string_view text) {
  const auto bytes = BytesFromString(text);
  output.insert(output.end(), bytes.begin(), bytes.end());
}

void AppendPart(huxerui::Bytes &output, const std::string_view boundary,
                const std::string_view name,
                const std::optional<std::string_view> filename,
                const std::string_view content_type,
                const std::span<const std::byte> bytes) {
  Append(output, "--" + std::string{boundary} + "\r\n");
  std::string disposition =
      "Content-Disposition: form-data; name=\"" + std::string{name} + "\"";
  if (filename)
    disposition += "; filename=\"" + std::string{*filename} + "\"";
  Append(output, disposition + "\r\nContent-Type: " +
                     std::string{content_type} + "\r\n\r\n");
  output.insert(output.end(), bytes.begin(), bytes.end());
  Append(output, "\r\n");
}

std::string Boundary() {
  std::array<std::uint32_t, 4> words{};
  std::mt19937 engine{std::random_device{}()};
  for (auto &word : words)
    word = engine();
  constexpr std::string_view hex = "0123456789abcdef";
  std::string value{"LineCode-"};
  for (const auto word : words) {
    for (int shift = 28; shift >= 0; shift -= 4)
      value.push_back(hex[(word >> shift) & 0xFU]);
  }
  return value;
}

Result<std::pair<std::string, huxerui::Bytes>> Multipart(
    const application::SkillHubPublishRequest &request,
    const std::vector<PublishFile> &files) {
  json::Object payload{{"slug", request.slug},
                       {"displayName", request.display_name},
                       {"version", request.version},
                       {"summaryZh", request.skill.description},
                       {"iconUrl", std::string{}}};
  const auto payload_text = json::Serialize(payload);
  const auto boundary = Boundary();
  huxerui::Bytes body;
  AppendPart(body, boundary, "payload", std::nullopt, "application/json",
             BytesFromString(payload_text));
  for (const auto &file : files)
    AppendPart(body, boundary, "files", file.path, "application/octet-stream",
               file.bytes);
  Append(body, "--" + boundary + "--\r\n");
  if (body.size() > kMaximumPublishTotalBytes + kMaximumMultipartOverhead)
    return std::unexpected(Error("Skill publish request exceeds safety limit"));
  return std::pair{boundary, std::move(body)};
}

Result<std::string> ValidatedComment(std::string content) {
  content = Trim(content);
  if (content.empty() || Utf8Codepoints(content) > kMaximumCommentCodepoints)
    return std::unexpected(Error("Comment must contain 1 to 500 characters"));
  return content;
}

} // namespace

HuxSkillHubSessionGateway::HuxSkillHubSessionGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument(
        "HuxSkillHubSessionGateway requires HttpClient");
}

huxerui::Task<Result<domain::SkillHubSession>>
HuxSkillHubSessionGateway::CurrentSession(std::string cookie) {
  auto request = Request("/api/v1/auth/me", std::move(cookie));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (response->status_code == 401)
    co_return domain::SkillHubSession{};
  auto success = Success(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  auto decoded = DecodeSkillHubSession(Text(response->body));
  if (!decoded)
    co_return std::unexpected(Error(decoded.error().message));
  co_return std::move(*decoded);
}

huxerui::Task<Result<void>> HuxSkillHubSessionGateway::Publish(
    std::string cookie, application::SkillHubPublishRequest request) {
  auto slug = ValidateSkillHubSlug(request.slug);
  auto version = ValidateSkillHubVersion(request.version);
  request.display_name = Trim(request.display_name);
  if (!slug)
    co_return std::unexpected(Error(slug.error().message));
  if (!version)
    co_return std::unexpected(Error(version.error().message));
  if (request.display_name.empty() ||
      Utf8Codepoints(request.display_name) > kMaximumDisplayNameCodepoints)
    co_return std::unexpected(Error("Skill display name is invalid"));
  request.slug = std::move(*slug);
  request.version = std::move(*version);
  auto files = co_await huxerui::RunWorker(
      [skill = request.skill] { return CollectPublishFiles(skill); });
  if (!files)
    co_return std::unexpected(std::move(files.error()));
  auto multipart = Multipart(request, *files);
  if (!multipart)
    co_return std::unexpected(std::move(multipart.error()));
  auto validated_cookie = ValidateSkillHubCookie(cookie);
  if (!validated_cookie)
    co_return std::unexpected(Error(validated_cookie.error().message));
  std::vector<huxerui::HttpHeader> headers{
      {.name = "Accept", .value = "application/json"},
      {.name = "Content-Type",
       .value = "multipart/form-data; boundary=" + multipart->first}};
  if (!validated_cookie->empty())
    headers.push_back(
        {.name = "Cookie", .value = std::move(*validated_cookie)});
  auto response = co_await Send(
      http_, {.url = "https://api.skillhub.cn/api/v1/community/skills/publish",
              .method = huxerui::HttpMethod::Post,
              .headers = std::move(headers),
              .body = std::move(multipart->second),
              .timeout = kPublishTimeout});
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Success(*response);
}

huxerui::Task<Result<domain::SkillHubComment>>
HuxSkillHubSessionGateway::PostComment(
    std::string cookie, std::string slug, std::string name_space,
    std::string content, const std::optional<std::int64_t> reply_to) {
  auto safe_slug = ValidateSkillHubSlug(slug);
  auto safe_content = ValidatedComment(std::move(content));
  if (!safe_slug)
    co_return std::unexpected(Error(safe_slug.error().message));
  if (!safe_content)
    co_return std::unexpected(std::move(safe_content.error()));
  if (reply_to && *reply_to <= 0)
    co_return std::unexpected(Error("invalid SkillHub comment id"));
  auto path = "/api/v1/skills/" + *safe_slug + "/comments";
  if (reply_to)
    path += "/" + std::to_string(*reply_to) + "/replies";
  path += NamespaceQuery(name_space);
  auto request = Request(std::move(path), std::move(cookie),
                         huxerui::HttpMethod::Post,
                         JsonText(std::move(*safe_content)));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = Success(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  auto decoded = DecodeSkillHubComment(Text(response->body));
  if (!decoded)
    co_return std::unexpected(Error(decoded.error().message));
  co_return std::move(*decoded);
}

huxerui::Task<Result<void>> HuxSkillHubSessionGateway::SetCommentLiked(
    std::string cookie, std::string slug, const std::int64_t comment_id,
    std::string name_space, const bool liked) {
  auto safe_slug = ValidateSkillHubSlug(slug);
  if (!safe_slug)
    co_return std::unexpected(Error(safe_slug.error().message));
  if (comment_id <= 0)
    co_return std::unexpected(Error("invalid SkillHub comment id"));
  auto request = Request("/api/v1/skills/" + *safe_slug + "/comments/" +
                             std::to_string(comment_id) + "/like" +
                             NamespaceQuery(name_space),
                         std::move(cookie),
                         liked ? huxerui::HttpMethod::Post
                               : huxerui::HttpMethod::Delete);
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Success(*response);
}

huxerui::Task<Result<void>> HuxSkillHubSessionGateway::DeleteComment(
    std::string cookie, std::string slug, const std::int64_t comment_id,
    std::string name_space) {
  auto safe_slug = ValidateSkillHubSlug(slug);
  if (!safe_slug)
    co_return std::unexpected(Error(safe_slug.error().message));
  if (comment_id <= 0)
    co_return std::unexpected(Error("invalid SkillHub comment id"));
  auto request = Request("/api/v1/skills/" + *safe_slug + "/comments/" +
                             std::to_string(comment_id) +
                             NamespaceQuery(name_space),
                         std::move(cookie), huxerui::HttpMethod::Delete);
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Success(*response);
}

huxerui::Task<Result<bool>>
HuxSkillHubSessionGateway::Starred(std::string cookie, std::string slug,
                                   std::string name_space) {
  auto safe_slug = ValidateSkillHubSlug(slug);
  if (!safe_slug)
    co_return std::unexpected(Error(safe_slug.error().message));
  auto request = Request("/api/v1/skills/" + *safe_slug + "/starred" +
                             NamespaceQuery(name_space),
                         std::move(cookie));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = Success(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  auto decoded = DecodeSkillHubStarred(Text(response->body));
  if (!decoded)
    co_return std::unexpected(Error(decoded.error().message));
  co_return *decoded;
}

huxerui::Task<Result<void>> HuxSkillHubSessionGateway::SetStarred(
    std::string cookie, std::string slug, std::string name_space,
    const bool starred) {
  auto safe_slug = ValidateSkillHubSlug(slug);
  if (!safe_slug)
    co_return std::unexpected(Error(safe_slug.error().message));
  auto request = Request("/api/v1/skills/" + *safe_slug + "/star" +
                             NamespaceQuery(name_space),
                         std::move(cookie),
                         starred ? huxerui::HttpMethod::Post
                                 : huxerui::HttpMethod::Delete);
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Success(*response);
}

huxerui::Task<Result<void>>
HuxSkillHubSessionGateway::Logout(std::string cookie) {
  auto request = Request("/api/v1/auth/logout", std::move(cookie),
                         huxerui::HttpMethod::Post);
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request));
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  if (response->status_code == 401)
    co_return Result<void>{};
  co_return Success(*response);
}

} // namespace linecode::infrastructure
