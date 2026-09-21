#include "infrastructure/hux_skill_hub_gateway.h"

#include <algorithm>
#include <array>
#include <chrono>
#include <cctype>
#include <cstddef>
#include <expected>
#include <optional>
#include <stdexcept>
#include <string_view>
#include <utility>

#include "infrastructure/skill_hub_codec.h"

namespace linecode::infrastructure {
namespace {

using application::SkillError;
template <class Value>
using Result = application::SkillResult<Value>;

constexpr std::size_t kMaximumJsonBytes = 2U * 1024U * 1024U;
constexpr std::size_t kMaximumMarkdownBytes = 512U * 1024U;
constexpr std::size_t kMaximumZipBytes = 20U * 1024U * 1024U;
constexpr std::size_t kMaximumIconBytes = 512U * 1024U;
constexpr std::size_t kReadChunkBytes = 64U * 1024U;
constexpr auto kDefaultTimeout = std::chrono::seconds{30};
constexpr auto kDownloadTimeout = std::chrono::seconds{60};

SkillError Error(std::string message) {
  return {.message = std::move(message)};
}

Result<SkillHubHttpRequest> CodecRequest(
    SkillHubCodecResult<SkillHubHttpRequest> request) {
  if (!request)
    return std::unexpected(Error(request.error().message));
  return std::move(*request);
}

huxerui::HttpMethod GetMethod(SkillHubMethod) {
  return huxerui::HttpMethod::Get;
}
huxerui::HttpMethod PostMethod(SkillHubMethod) {
  return huxerui::HttpMethod::Post;
}
huxerui::HttpMethod DeleteMethod(SkillHubMethod) {
  return huxerui::HttpMethod::Delete;
}

struct MethodStrategy final {
  SkillHubMethod method;
  huxerui::HttpMethod (*convert)(SkillHubMethod);
};

constexpr std::array kMethods{
    MethodStrategy{SkillHubMethod::get, GetMethod},
    MethodStrategy{SkillHubMethod::post, PostMethod},
    MethodStrategy{SkillHubMethod::delete_, DeleteMethod},
};

huxerui::HttpMethod ToHttpMethod(const SkillHubMethod method) {
  const auto strategy = std::ranges::find(kMethods, method,
                                          &MethodStrategy::method);
  if (strategy == kMethods.end())
    throw std::invalid_argument("unsupported SkillHub HTTP method");
  return strategy->convert(method);
}

huxerui::HttpRequest ToHttpRequest(SkillHubHttpRequest request,
                                   const std::chrono::milliseconds timeout) {
  std::vector<huxerui::HttpHeader> headers;
  headers.reserve(request.headers.size());
  for (auto &[name, value] : request.headers)
    headers.push_back({.name = std::move(name), .value = std::move(value)});
  return {.url = std::move(request.url),
          .method = ToHttpMethod(request.method),
          .headers = std::move(headers),
          .body = std::move(request.body),
          .timeout = timeout};
}

struct BodyResponse final {
  std::string url;
  int status{};
  std::vector<huxerui::HttpHeader> headers;
  huxerui::Bytes body;
};

huxerui::Task<Result<BodyResponse>>
Send(const std::shared_ptr<huxerui::HttpClient> &http,
     SkillHubHttpRequest request, const std::size_t maximum_bytes,
     const std::chrono::milliseconds timeout = kDefaultTimeout) {
  auto opened =
      co_await http->SendStreamAsync(ToHttpRequest(std::move(request), timeout));
  if (!opened.Succeeded())
    co_return std::unexpected(Error(opened.Error().message));
  auto stream = std::move(opened).Value();
  BodyResponse response{.url = stream.Url(),
                        .status = stream.StatusCode(),
                        .headers = {stream.Headers().begin(),
                                    stream.Headers().end()},
                        .body = {}};
  while (true) {
    auto read = co_await stream.Body().ReadAsync(kReadChunkBytes);
    if (!read.Succeeded())
      co_return std::unexpected(Error(read.Error().message));
    auto bytes = std::move(read).Value();
    if (bytes.empty())
      break;
    if (bytes.size() > maximum_bytes -
                           std::min(maximum_bytes, response.body.size()))
      co_return std::unexpected(Error("SkillHub response exceeds its safety limit"));
    response.body.insert(response.body.end(), bytes.begin(), bytes.end());
  }
  co_return response;
}

std::string Text(const huxerui::Bytes &bytes) {
  return {reinterpret_cast<const char *>(bytes.data()), bytes.size()};
}

Result<void> RequireSuccess(const BodyResponse &response) {
  if (response.status < 200 || response.status >= 300) {
    auto message = Text(response.body);
    if (message.size() > 4096)
      message.resize(4096);
    return std::unexpected(
        Error("SkillHub HTTP " + std::to_string(response.status) +
              (message.empty() ? std::string{} : ": " + message)));
  }
  return {};
}

template <class Value, class Decoder>
Result<Value> Decode(const BodyResponse &response, Decoder decoder) {
  auto success = RequireSuccess(response);
  if (!success)
    return std::unexpected(std::move(success.error()));
  auto decoded = decoder(Text(response.body));
  if (!decoded)
    return std::unexpected(Error(decoded.error().message));
  return std::move(*decoded);
}

std::string NamespaceFromCanonical(const std::string_view canonical) {
  if (!canonical.starts_with('@'))
    return {};
  const auto slash = canonical.find('/');
  return slash > 1 ? std::string{canonical.substr(1, slash - 1)}
                   : std::string{};
}

bool HeaderStartsWith(const BodyResponse &response, const std::string_view name,
                      const std::string_view prefix) {
  return std::ranges::any_of(response.headers, [&](const huxerui::HttpHeader &h) {
    if (h.name.size() != name.size())
      return false;
    const bool same_name = std::ranges::equal(
        h.name, name, [](const unsigned char a, const unsigned char b) {
          return std::tolower(a) == std::tolower(b);
        });
    return same_name && h.value.size() >= prefix.size() &&
           std::ranges::equal(
               std::string_view{h.value}.substr(0, prefix.size()), prefix,
               [](const unsigned char a, const unsigned char b) {
                 return std::tolower(a) == std::tolower(b);
               });
  });
}

SkillHubHttpRequest SimpleGet(std::string url) {
  return {.url = std::move(url),
          .method = SkillHubMethod::get,
          .headers = {{"Accept", "application/json"}},
          .body = {}};
}

} // namespace

HuxSkillHubGateway::HuxSkillHubGateway(
    std::shared_ptr<huxerui::HttpClient> http)
    : http_(std::move(http)) {
  if (!http_)
    throw std::invalid_argument("HuxSkillHubGateway requires HttpClient");
}

huxerui::Task<Result<domain::SkillHubPage>>
HuxSkillHubGateway::List(application::SkillHubListQuery query) {
  auto response = co_await Send(http_, BuildSkillHubListRequest(std::move(query)),
                                kMaximumJsonBytes);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Decode<domain::SkillHubPage>(*response, DecodeSkillHubPage);
}

huxerui::Task<Result<domain::SkillHubDetail>>
HuxSkillHubGateway::Detail(std::string slug) {
  auto core_request = CodecRequest(BuildSkillHubDetailRequest(slug));
  if (!core_request)
    co_return std::unexpected(std::move(core_request.error()));
  auto core_response =
      co_await Send(http_, std::move(*core_request), kMaximumJsonBytes);
  if (!core_response)
    co_return std::unexpected(std::move(core_response.error()));
  auto detail = Decode<domain::SkillHubDetail>(
      *core_response, [&slug](const std::string_view json) {
        return DecodeSkillHubDetail(json, slug);
      });
  if (!detail)
    co_return std::unexpected(std::move(detail.error()));
  const auto name_space = detail->namespace_handle.empty()
                              ? NamespaceFromCanonical(detail->canonical_name)
                              : detail->namespace_handle;

  auto files_request = CodecRequest(BuildSkillHubFilesRequest(slug, name_space));
  if (!files_request)
    co_return std::unexpected(std::move(files_request.error()));
  auto files_response =
      co_await Send(http_, std::move(*files_request), kMaximumJsonBytes);
  if (!files_response)
    co_return std::unexpected(std::move(files_response.error()));
  auto files = Decode<std::vector<domain::SkillHubFileEntry>>(
      *files_response, DecodeSkillHubFiles);
  if (!files)
    co_return std::unexpected(std::move(files.error()));
  detail->files = std::move(*files);

  if (!detail->version.empty()) {
    auto markdown_request = CodecRequest(BuildSkillHubFileRequest(
        slug, detail->version, "SKILL.md", name_space));
    if (!markdown_request)
      co_return std::unexpected(std::move(markdown_request.error()));
    auto markdown_response =
        co_await Send(http_, std::move(*markdown_request), kMaximumMarkdownBytes);
    if (!markdown_response)
      co_return std::unexpected(std::move(markdown_response.error()));
    auto success = RequireSuccess(*markdown_response);
    if (!success)
      co_return std::unexpected(std::move(success.error()));
    detail->markdown = Text(markdown_response->body);
  }

  auto comments_request =
      CodecRequest(BuildSkillHubCommentsRequest(slug, name_space));
  auto versions_request =
      CodecRequest(BuildSkillHubVersionsRequest(slug, name_space));
  auto evaluation_request =
      CodecRequest(BuildSkillHubEvaluationRequest(slug, name_space));
  auto tests_request =
      CodecRequest(BuildSkillHubTestCasesRequest(slug, name_space));
  if (!comments_request || !versions_request || !evaluation_request ||
      !tests_request)
    co_return std::unexpected(Error("cannot build SkillHub detail requests"));

  auto comments_response =
      co_await Send(http_, std::move(*comments_request), kMaximumJsonBytes);
  if (!comments_response)
    co_return std::unexpected(std::move(comments_response.error()));
  auto comments = Decode<std::vector<domain::SkillHubComment>>(
      *comments_response, DecodeSkillHubComments);
  if (!comments)
    co_return std::unexpected(std::move(comments.error()));
  detail->comments = std::move(*comments);

  auto versions_response =
      co_await Send(http_, std::move(*versions_request), kMaximumJsonBytes);
  if (!versions_response)
    co_return std::unexpected(std::move(versions_response.error()));
  auto versions = Decode<std::vector<domain::SkillHubVersion>>(
      *versions_response, DecodeSkillHubVersions);
  if (!versions)
    co_return std::unexpected(std::move(versions.error()));
  detail->versions = std::move(*versions);

  auto evaluation_response =
      co_await Send(http_, std::move(*evaluation_request), kMaximumJsonBytes);
  if (!evaluation_response)
    co_return std::unexpected(std::move(evaluation_response.error()));
  auto evaluation = Decode<domain::SkillHubEvaluation>(
      *evaluation_response, DecodeSkillHubEvaluation);
  if (!evaluation)
    co_return std::unexpected(std::move(evaluation.error()));
  detail->evaluation = std::move(*evaluation);

  auto tests_response =
      co_await Send(http_, std::move(*tests_request), kMaximumJsonBytes);
  if (!tests_response)
    co_return std::unexpected(std::move(tests_response.error()));
  auto test_cases = Decode<std::vector<domain::SkillHubTestCase>>(
      *tests_response, DecodeSkillHubTestCases);
  if (!test_cases)
    co_return std::unexpected(std::move(test_cases.error()));
  detail->test_cases = std::move(*test_cases);
  co_return std::move(*detail);
}

huxerui::Task<Result<std::string>>
HuxSkillHubGateway::FileContent(std::string slug, std::string version,
                                std::string path) {
  auto request = CodecRequest(BuildSkillHubFileRequest(slug, version, path));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response =
      co_await Send(http_, std::move(*request), kMaximumMarkdownBytes);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = RequireSuccess(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  co_return Text(response->body);
}

huxerui::Task<Result<huxerui::Bytes>>
HuxSkillHubGateway::Download(std::string slug, std::string version) {
  auto request = CodecRequest(BuildSkillHubDownloadRequest(slug, version));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response =
      co_await Send(http_, std::move(*request), kMaximumZipBytes,
                    kDownloadTimeout);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = RequireSuccess(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  if (response->body.size() < 4 ||
      response->body[0] != static_cast<std::byte>('P') ||
      response->body[1] != static_cast<std::byte>('K'))
    co_return std::unexpected(Error("SkillHub returned an invalid ZIP archive"));
  co_return std::move(response->body);
}

huxerui::Task<Result<huxerui::Bytes>> HuxSkillHubGateway::Icon(std::string url) {
  auto request = CodecRequest(BuildSkillHubIconRequest(url));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request), kMaximumIconBytes);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = RequireSuccess(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  if (!ValidateSkillHubIconUrl(response->url))
    co_return std::unexpected(
        Error("SkillHub icon redirected to an untrusted host"));
  if (!HeaderStartsWith(*response, "Content-Type", "image/"))
    co_return std::unexpected(Error("SkillHub icon response is not an image"));
  co_return std::move(response->body);
}

huxerui::Task<Result<std::vector<domain::SkillHubComment>>>
HuxSkillHubGateway::CommentReplies(std::string slug,
                                   const std::int64_t comment_id,
                                   std::string name_space) {
  auto request = CodecRequest(
      BuildSkillHubRepliesRequest(slug, comment_id, name_space));
  if (!request)
    co_return std::unexpected(std::move(request.error()));
  auto response = co_await Send(http_, std::move(*request), kMaximumJsonBytes);
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  co_return Decode<std::vector<domain::SkillHubComment>>(
      *response, DecodeSkillHubComments);
}

huxerui::Task<Result<application::SkillInstallRequest>>
HuxSkillHubGateway::FetchGitHub(application::SkillRoots roots,
                                const domain::SkillLocation location,
                                std::string url) {
  auto source = ResolveGitHubSkillSource(url);
  if (!source)
    co_return std::unexpected(Error(source.error().message));
  auto request = SimpleGet(source->primary_url);
  const auto maximum_bytes =
      source->markdown ? kMaximumMarkdownBytes : kMaximumZipBytes;
  auto response = co_await Send(http_, std::move(request), maximum_bytes,
                                kDownloadTimeout);
  if ((!response || !RequireSuccess(*response)) && source->fallback_url) {
    response = co_await Send(http_, SimpleGet(*source->fallback_url),
                             maximum_bytes, kDownloadTimeout);
  }
  if (!response)
    co_return std::unexpected(std::move(response.error()));
  auto success = RequireSuccess(*response);
  if (!success)
    co_return std::unexpected(std::move(success.error()));
  if (source->markdown) {
    if (response->body.empty())
      co_return std::unexpected(Error("GitHub returned an empty SKILL.md"));
    co_return application::SkillInstallRequest{
        .roots = std::move(roots),
        .location = location,
        .name = std::move(source->suggested_name),
        .package = application::SkillMarkdownPackage{
            .markdown = Text(response->body)}};
  }
  if (response->body.size() < 4 ||
      response->body[0] != static_cast<std::byte>('P') ||
      response->body[1] != static_cast<std::byte>('K'))
    co_return std::unexpected(Error("GitHub returned an invalid ZIP archive"));
  co_return application::SkillInstallRequest{
      .roots = std::move(roots),
      .location = location,
      .name = std::move(source->suggested_name),
      .package = application::SkillZipPackage{
          .archive = std::move(response->body), .strip_common_root = true}};
}

huxerui::Task<Result<application::SkillInstallRequest>>
HuxSkillHubGateway::FetchSkillHub(application::SkillRoots roots,
                                  const domain::SkillLocation location,
                                  std::string slug, std::string version) {
  const auto name = slug;
  auto archive = co_await Download(std::move(slug), std::move(version));
  if (!archive)
    co_return std::unexpected(std::move(archive.error()));
  co_return application::SkillInstallRequest{
      .roots = std::move(roots),
      .location = location,
      .name = name,
      .package = application::SkillZipPackage{
          .archive = std::move(*archive), .strip_common_root = false}};
}

} // namespace linecode::infrastructure
