#include "infrastructure/web_tools_codec.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstdint>
#include <cstdlib>
#include <expected>
#include <initializer_list>
#include <iterator>
#include <optional>
#include <ranges>
#include <string>
#include <string_view>
#include <utility>
#include <vector>

#include "infrastructure/archive_json.h"

namespace linecode::infrastructure {
namespace {

namespace json = archive_json;

using application::WebSearchResultItem;
using application::WebToolError;
using application::WebToolErrorCode;

// cn.lineai.model.WebSearchConfig.defaultConfig() and
// cn.lineai.tool.builtin.search.BingRssSearchProvider.
constexpr std::string_view kBingRssEndpoint =
    "https://www.bing.com/search?format=rss";
constexpr std::string_view kBingRssUserAgent =
    "Mozilla/5.0 (Linux; Android 10) AppleWebKit/537.36 (KHTML, like Gecko) "
    "Chrome/120.0.0.0 Mobile Safari/537.36";
constexpr std::string_view kJsonAccept = "application/json";
constexpr std::string_view kRssAccept =
    "application/rss+xml, application/xml, text/xml, */*";

constexpr std::int32_t kDefaultSearchLimit = 5;
constexpr std::int32_t kMaximumSearchLimit = 10;
constexpr std::int32_t kDefaultFetchCharacters = 12'000;
constexpr std::int32_t kMinimumFetchCharacters = 1'000;
constexpr std::int32_t kMaximumFetchCharacters = 30'000;

WebToolError Error(WebToolErrorCode code, std::string message) {
  return {.code = code, .message = std::move(message)};
}

std::string Lower(std::string_view value) {
  std::string lowered;
  lowered.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(lowered),
                         [](unsigned char byte) {
                           return static_cast<char>(std::tolower(byte));
                         });
  return lowered;
}

std::string Upper(std::string_view value) {
  std::string raised;
  raised.reserve(value.size());
  std::ranges::transform(value, std::back_inserter(raised),
                         [](unsigned char byte) {
                           return static_cast<char>(std::toupper(byte));
                         });
  return raised;
}

// Java String.trim() removes every char <= U+0020; the port keeps the same
// byte-level rule used by domain::NormalizeMemoryContent.
std::string Trim(std::string_view value) {
  const auto trimmable = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && trimmable(value.front()))
    value.remove_prefix(1U);
  while (!value.empty() && trimmable(value.back()))
    value.remove_suffix(1U);
  return std::string{value};
}

std::size_t FindIgnoreCase(std::string_view haystack, std::string_view needle,
                           std::size_t from = 0) {
  if (needle.empty() || haystack.size() < needle.size())
    return std::string_view::npos;
  const auto lowered_needle = Lower(needle);
  for (std::size_t index = from; index + needle.size() <= haystack.size();
       ++index) {
    if (Lower(haystack.substr(index, needle.size())) == lowered_needle)
      return index;
  }
  return std::string_view::npos;
}

std::string EncodeUtf8(std::uint32_t code_point) {
  std::string encoded;
  if (code_point <= 0x7FU) {
    encoded.push_back(static_cast<char>(code_point));
    return encoded;
  }
  if (code_point <= 0x7FFU) {
    encoded.push_back(static_cast<char>(0xC0U | (code_point >> 6U)));
  } else if (code_point <= 0xFFFFU) {
    encoded.push_back(static_cast<char>(0xE0U | (code_point >> 12U)));
    encoded.push_back(
        static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
  } else {
    encoded.push_back(static_cast<char>(0xF0U | (code_point >> 18U)));
    encoded.push_back(
        static_cast<char>(0x80U | ((code_point >> 12U) & 0x3FU)));
    encoded.push_back(
        static_cast<char>(0x80U | ((code_point >> 6U) & 0x3FU)));
  }
  encoded.push_back(static_cast<char>(0x80U | (code_point & 0x3FU)));
  return encoded;
}

std::optional<std::uint32_t> ParseNumericEntity(std::string_view entity) {
  if (entity.size() < 2U || entity.front() != '#')
    return std::nullopt;
  auto digits = entity.substr(1U);
  int base = 10;
  if (!digits.empty() && (digits.front() == 'x' || digits.front() == 'X')) {
    base = 16;
    digits.remove_prefix(1U);
  }
  if (digits.empty())
    return std::nullopt;
  std::uint32_t value{};
  for (const char digit : digits) {
    const auto byte = static_cast<unsigned char>(digit);
    int numeric = -1;
    if (byte >= '0' && byte <= '9')
      numeric = byte - '0';
    else if (base == 16 && byte >= 'a' && byte <= 'f')
      numeric = byte - 'a' + 10;
    else if (base == 16 && byte >= 'A' && byte <= 'F')
      numeric = byte - 'A' + 10;
    if (numeric < 0 || numeric >= base)
      return std::nullopt;
    value = value * static_cast<std::uint32_t>(base) +
            static_cast<std::uint32_t>(numeric);
    if (value > 0x10FFFFU)
      return std::nullopt;
  }
  if (value == 0U)
    return std::nullopt;
  return value;
}

// htmlToText()/SearchResultItem.stripHtml() decode this legacy set; numeric
// references are decoded as well because the RSS reader decoded them too.
std::string DecodeEntities(std::string_view text) {
  static constexpr std::array<std::pair<std::string_view, char>, 7> kNamed{{
      {"nbsp", ' '},
      {"amp", '&'},
      {"lt", '<'},
      {"gt", '>'},
      {"quot", '"'},
      {"apos", '\''},
      {"#39", '\''},
  }};
  std::string decoded;
  decoded.reserve(text.size());
  std::size_t index = 0;
  while (index < text.size()) {
    const auto ampersand = text.find('&', index);
    if (ampersand == std::string_view::npos) {
      decoded.append(text.substr(index));
      break;
    }
    decoded.append(text.substr(index, ampersand - index));
    const auto semicolon = text.find(';', ampersand + 1U);
    if (semicolon == std::string_view::npos || semicolon - ampersand > 12U) {
      decoded.push_back('&');
      index = ampersand + 1U;
      continue;
    }
    const auto entity = text.substr(ampersand + 1U, semicolon - ampersand - 1U);
    const auto named = std::ranges::find(
        kNamed, entity, &std::pair<std::string_view, char>::first);
    if (named != kNamed.end()) {
      decoded.push_back(named->second);
      index = semicolon + 1U;
      continue;
    }
    if (const auto code_point = ParseNumericEntity(entity)) {
      decoded += EncodeUtf8(*code_point);
      index = semicolon + 1U;
      continue;
    }
    decoded.push_back('&');
    index = ampersand + 1U;
  }
  return decoded;
}

// Legacy regex: </(p|div|section|article|header|footer|li|h[1-6]|tr)\s*>
bool IsBlockTag(std::string_view name) {
  constexpr std::array<std::string_view, 8> kBlocks{
      "p", "div", "section", "article", "header", "footer", "li", "tr"};
  if (std::ranges::find(kBlocks, name) != kBlocks.end())
    return true;
  return name.size() == 2U && name.front() == 'h' && name.back() >= '1' &&
         name.back() <= '6';
}

// Steps 2..4 of htmlToText(): closing block tags and <br> become newlines,
// every other tag becomes a single space.
std::string StripTags(std::string_view html) {
  std::string stripped;
  stripped.reserve(html.size());
  std::size_t index = 0;
  while (index < html.size()) {
    const auto open = html.find('<', index);
    if (open == std::string_view::npos) {
      stripped.append(html.substr(index));
      break;
    }
    stripped.append(html.substr(index, open - index));
    const auto close = html.find('>', open + 1U);
    if (close == std::string_view::npos) {
      stripped.append(html.substr(open));
      break;
    }
    auto inner = Trim(html.substr(open + 1U, close - open - 1U));
    if (!inner.empty() && inner.back() == '/') {
      inner.pop_back();
      inner = Trim(inner);
    }
    const bool closing = !inner.empty() && inner.front() == '/';
    if (closing)
      inner = Trim(std::string_view{inner}.substr(1U));
    const auto name_end = inner.find_first_of(" \t\r\n");
    const auto name = Lower(inner.substr(0U, name_end));
    if (closing && IsBlockTag(name))
      stripped.push_back('\n');
    else if (!closing && name == "br")
      stripped.push_back('\n');
    else
      stripped.push_back(' ');
    index = close + 1U;
  }
  return stripped;
}

// Step 1 of htmlToText(): drop <script>/<style>/<noscript> including content.
std::string RemoveElementBlocks(std::string_view html,
                                std::string_view element) {
  const std::string open{"<" + std::string{element}};
  const std::string close{"</" + std::string{element}};
  std::string output;
  std::string_view remaining{html};
  while (true) {
    const auto start = FindIgnoreCase(remaining, open);
    if (start == std::string_view::npos) {
      output.append(remaining);
      return output;
    }
    output.append(remaining.substr(0U, start));
    output.push_back(' ');
    const auto close_start =
        FindIgnoreCase(remaining, close, start + open.size());
    if (close_start == std::string_view::npos)
      return output;
    const auto close_end = remaining.find('>', close_start + close.size());
    if (close_end == std::string_view::npos)
      return output;
    remaining = remaining.substr(close_end + 1U);
  }
}

// Steps 5..6 of htmlToText(): [ \t]{2,} -> " " and then \n[ \t]+ -> \n.
std::string CollapseRuns(std::string_view text) {
  std::string collapsed;
  collapsed.reserve(text.size());
  std::size_t index = 0;
  while (index < text.size()) {
    const char current = text[index];
    if (current == ' ' || current == '\t') {
      std::size_t run = 1;
      while (index + run < text.size() &&
             (text[index + run] == ' ' || text[index + run] == '\t')) {
        ++run;
      }
      collapsed.push_back(run > 1U ? ' ' : current);
      index += run;
      continue;
    }
    collapsed.push_back(current);
    ++index;
  }
  std::string dedented;
  dedented.reserve(collapsed.size());
  index = 0;
  while (index < collapsed.size()) {
    if (collapsed[index] != '\n') {
      dedented.push_back(collapsed[index]);
      ++index;
      continue;
    }
    dedented.push_back('\n');
    ++index;
    while (index < collapsed.size() &&
           (collapsed[index] == ' ' || collapsed[index] == '\t')) {
      ++index;
    }
  }
  return dedented;
}

std::size_t Utf8CharacterCount(std::string_view value) noexcept {
  return static_cast<std::size_t>(std::ranges::count_if(
      value, [](unsigned char byte) { return (byte & 0xC0U) != 0x80U; }));
}

std::size_t Utf8PrefixBytes(std::string_view value,
                            std::size_t characters) noexcept {
  std::size_t byte_index{};
  std::size_t count{};
  while (byte_index < value.size() && count < characters) {
    ++byte_index;
    while (byte_index < value.size() &&
           (static_cast<unsigned char>(value[byte_index]) & 0xC0U) == 0x80U) {
      ++byte_index;
    }
    ++count;
  }
  return byte_index;
}

// Java URLEncoder.encode(value, "UTF-8"): unreserved characters plus '.', '-',
// '*' and '_' stay literal, space becomes '+', everything else is percent
// encoded with uppercase hex digits.
std::string PercentEncodeForm(std::string_view value) {
  constexpr std::string_view kUnreserved{
      "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-_.*"};
  constexpr std::string_view kHex{"0123456789ABCDEF"};
  std::string encoded;
  encoded.reserve(value.size());
  for (const unsigned char byte : value) {
    if (byte == ' ') {
      encoded.push_back('+');
      continue;
    }
    if (kUnreserved.find(static_cast<char>(byte)) != std::string_view::npos) {
      encoded.push_back(static_cast<char>(byte));
      continue;
    }
    encoded.push_back('%');
    encoded.push_back(kHex[byte >> 4U]);
    encoded.push_back(kHex[byte & 0x0FU]);
  }
  return encoded;
}

// cn.lineai.tool.builtin.SearchRequest.appendQuery: empty keys and values are
// skipped and the separator depends on whether the endpoint already has one.
std::string AppendQuery(
    std::string_view base_url,
    const std::vector<std::pair<std::string, std::string>> &parameters) {
  std::string query;
  for (const auto &[key, value] : parameters) {
    if (key.empty() || value.empty())
      continue;
    if (!query.empty())
      query.push_back('&');
    query += PercentEncodeForm(key);
    query.push_back('=');
    query += PercentEncodeForm(value);
  }
  if (query.empty())
    return std::string{base_url};
  std::string url{base_url};
  url += url.find('?') == std::string::npos ? "?" : "&";
  url += query;
  return url;
}

// DefaultSearchProvider/QueryCountSearchProvider/SerpApiSearchProvider:
// "authorization" gets the Bearer prefix, every other header name gets the raw
// key.
std::string ApiKeyHeaderValue(std::string_view header, std::string_view key) {
  return Lower(header) == "authorization" ? "Bearer " + std::string{key}
                                          : std::string{key};
}

std::string QueryParameter(const domain::WebSearchConfig &config) {
  return std::string{domain::EffectiveWebSearchQueryParam(config)};
}

WebHttpRequestPlan MakeRequest(std::string url, WebHttpMethod method) {
  WebHttpRequestPlan plan;
  plan.url = std::move(url);
  plan.method = method;
  return plan;
}

std::expected<WebHttpRequestPlan, WebToolError> BuildDefaultSearchRequest(
    const domain::WebSearchConfig &config, std::string_view query,
    std::int32_t limit) {
  std::vector<std::pair<std::string, std::string>> parameters;
  parameters.emplace_back(QueryParameter(config), std::string{query});
  if (!config.model.empty())
    parameters.emplace_back("model", config.model);
  if (!config.api_key_param.empty())
    parameters.emplace_back(config.api_key_param, config.api_key);
  parameters.emplace_back("limit", std::to_string(limit));
  auto plan = MakeRequest(AppendQuery(config.base_url, parameters),
                          WebHttpMethod::get);
  plan.headers.emplace_back("Accept", std::string{kJsonAccept});
  if (!config.api_key_header.empty()) {
    plan.headers.emplace_back(
        config.api_key_header,
        ApiKeyHeaderValue(config.api_key_header, config.api_key));
  }
  return plan;
}

std::expected<WebHttpRequestPlan, WebToolError> BuildQueryCountSearchRequest(
    const domain::WebSearchConfig &config, std::string_view query,
    std::int32_t limit) {
  std::vector<std::pair<std::string, std::string>> parameters;
  parameters.emplace_back(QueryParameter(config), std::string{query});
  parameters.emplace_back("count", std::to_string(limit));
  auto plan = MakeRequest(AppendQuery(config.base_url, parameters),
                          WebHttpMethod::get);
  plan.headers.emplace_back("Accept", std::string{kJsonAccept});
  if (!config.api_key_header.empty()) {
    plan.headers.emplace_back(
        config.api_key_header,
        ApiKeyHeaderValue(config.api_key_header, config.api_key));
  }
  return plan;
}

std::expected<WebHttpRequestPlan, WebToolError> BuildSerpApiSearchRequest(
    const domain::WebSearchConfig &config, std::string_view query,
    std::int32_t limit) {
  std::vector<std::pair<std::string, std::string>> parameters;
  parameters.emplace_back(QueryParameter(config), std::string{query});
  parameters.emplace_back("engine",
                          config.model.empty() ? "google" : config.model);
  parameters.emplace_back(
      config.api_key_param.empty() ? "api_key" : config.api_key_param,
      config.api_key);
  parameters.emplace_back("num", std::to_string(limit));
  auto plan = MakeRequest(AppendQuery(config.base_url, parameters),
                          WebHttpMethod::get);
  plan.headers.emplace_back("Accept", std::string{kJsonAccept});
  if (!config.api_key_header.empty()) {
    plan.headers.emplace_back(
        config.api_key_header,
        ApiKeyHeaderValue(config.api_key_header, config.api_key));
  }
  return plan;
}

std::expected<WebHttpRequestPlan, WebToolError> BuildTavilySearchRequest(
    const domain::WebSearchConfig &config, std::string_view query,
    std::int32_t limit) {
  auto body = json::Serialize(json::Object{
      {"include_answer", false},
      {"max_results", static_cast<std::int64_t>(limit)},
      {"query", std::string{query}},
      {"search_depth",
       config.model.empty() ? std::string{"basic"} : config.model},
  });
  auto plan = MakeRequest(config.base_url, WebHttpMethod::post);
  plan.headers.emplace_back("Content-Type", "application/json");
  plan.headers.emplace_back("Authorization", "Bearer " + config.api_key);
  plan.body = std::move(body);
  return plan;
}

std::expected<WebHttpRequestPlan, WebToolError> BuildBingRssSearchRequest(
    const domain::WebSearchConfig &config, std::string_view query,
    std::int32_t limit) {
  std::vector<std::pair<std::string, std::string>> parameters;
  parameters.emplace_back(QueryParameter(config), std::string{query});
  parameters.emplace_back("count", std::to_string(limit));
  parameters.emplace_back("mkt", WebSearchMarket());
  parameters.emplace_back("safe", "strict");
  const auto base_url = config.base_url.empty()
                            ? std::string{kBingRssEndpoint}
                            : config.base_url;
  auto plan = MakeRequest(AppendQuery(base_url, parameters),
                          WebHttpMethod::get);
  plan.headers.emplace_back("Accept", std::string{kRssAccept});
  plan.headers.emplace_back("User-Agent", std::string{kBingRssUserAgent});
  return plan;
}

std::string FirstString(const json::Object &object,
                        std::initializer_list<std::string_view> keys) {
  for (const auto key : keys) {
    if (key.empty())
      continue;
    const auto *text = json::AsString(json::Find(object, key));
    if (text == nullptr)
      continue;
    auto trimmed = Trim(*text);
    if (!trimmed.empty())
      return trimmed;
  }
  return {};
}

struct ResultKeys final {
  std::string_view title;
  std::string_view url;
  std::string_view snippet;
  std::string_view date;
};

// SearchResultItem.arrayToResults: an item without a URL is dropped and an item
// without a title becomes "Untitled".
std::vector<WebSearchResultItem> NormalizeResults(const json::Array *array,
                                                  const ResultKeys &keys) {
  std::vector<WebSearchResultItem> results;
  if (array == nullptr)
    return results;
  results.reserve(array->size());
  for (const auto &value : *array) {
    const auto *item = json::AsObject(&value);
    if (item == nullptr)
      continue;
    auto url = FirstString(*item, {keys.url, "link", "href"});
    if (url.empty())
      continue;
    auto title = FirstString(*item, {keys.title, "name", "title"});
    if (title.empty())
      title = "Untitled";
    results.push_back(WebSearchResultItem{
        .title = std::move(title),
        .url = std::move(url),
        .snippet = FirstString(*item, {keys.snippet, "description", "content"}),
        .published_date = FirstString(*item, {keys.date}),
    });
  }
  return results;
}

using ArrayPicker = const json::Array *(*)(const json::Object &);

// DefaultSearchProvider.normalizeResults candidate order.
const json::Array *PickDefaultArray(const json::Object &root) {
  if (const auto *found = json::AsArray(json::Find(root, "results")))
    return found;
  if (const auto *found = json::AsArray(json::Find(root, "items")))
    return found;
  if (const auto *found = json::AsArray(json::Find(root, "data")))
    return found;
  if (const auto *web = json::AsObject(json::Find(root, "web"))) {
    if (const auto *found = json::AsArray(json::Find(*web, "results")))
      return found;
  }
  return json::AsArray(json::Find(root, "organic_results"));
}

const json::Array *PickResultsArray(const json::Object &root) {
  return json::AsArray(json::Find(root, "results"));
}

const json::Array *PickWebResultsArray(const json::Object &root) {
  const auto *web = json::AsObject(json::Find(root, "web"));
  return web == nullptr ? nullptr : json::AsArray(json::Find(*web, "results"));
}

const json::Array *PickWebPagesArray(const json::Object &root) {
  const auto *pages = json::AsObject(json::Find(root, "webPages"));
  return pages == nullptr ? nullptr
                          : json::AsArray(json::Find(*pages, "value"));
}

const json::Array *PickOrganicResultsArray(const json::Object &root) {
  return json::AsArray(json::Find(root, "organic_results"));
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseJsonResults(std::string_view body, ArrayPicker picker,
                 const ResultKeys &keys) {
  auto parsed = json::Parse(body);
  const auto *root = parsed ? json::AsObject(&*parsed) : nullptr;
  if (root == nullptr) {
    return std::unexpected(
        Error(WebToolErrorCode::decode,
              "Search API returned a response that is not a JSON object"));
  }
  return NormalizeResults(picker(*root), keys);
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseDefaultSearchResponse(std::string_view body) {
  return ParseJsonResults(body, &PickDefaultArray,
                          ResultKeys{.title = "title",
                                     .url = "url",
                                     .snippet = "snippet",
                                     .date = "publishedDate"});
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseBingResponse(std::string_view body) {
  return ParseJsonResults(body, &PickWebPagesArray,
                          ResultKeys{.title = "name",
                                     .url = "url",
                                     .snippet = "snippet",
                                     .date = "dateLastCrawled"});
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseBraveResponse(std::string_view body) {
  return ParseJsonResults(body, &PickWebResultsArray,
                          ResultKeys{.title = "title",
                                     .url = "url",
                                     .snippet = "description",
                                     .date = "age"});
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseTavilyResponse(std::string_view body) {
  return ParseJsonResults(body, &PickResultsArray,
                          ResultKeys{.title = "title",
                                     .url = "url",
                                     .snippet = "content",
                                     .date = "published_date"});
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseSerpApiResponse(std::string_view body) {
  return ParseJsonResults(body, &PickOrganicResultsArray,
                          ResultKeys{.title = "title",
                                     .url = "link",
                                     .snippet = "snippet",
                                     .date = "date"});
}

std::optional<std::string> ElementText(std::string_view item,
                                       std::string_view name) {
  const std::string open{"<" + std::string{name}};
  const std::string close{"</" + std::string{name}};
  const auto open_start = FindIgnoreCase(item, open);
  if (open_start == std::string_view::npos)
    return std::nullopt;
  const auto content_start = item.find('>', open_start + open.size());
  if (content_start == std::string_view::npos)
    return std::nullopt;
  const auto close_start = FindIgnoreCase(item, close, content_start + 1U);
  if (close_start == std::string_view::npos)
    return std::nullopt;
  auto text =
      Trim(item.substr(content_start + 1U, close_start - content_start - 1U));
  constexpr std::string_view kCdataOpen{"<![CDATA["};
  constexpr std::string_view kCdataClose{"]]>"};
  if (text.size() >= kCdataOpen.size() + kCdataClose.size() &&
      text.starts_with(kCdataOpen) && text.ends_with(kCdataClose)) {
    text = text.substr(kCdataOpen.size(),
                       text.size() - kCdataOpen.size() - kCdataClose.size());
  }
  return DecodeEntities(text);
}

// BingRssSearchProvider.parseRss on the public Bing RSS document.
std::vector<WebSearchResultItem> ParseRssItems(std::string_view xml) {
  std::vector<WebSearchResultItem> results;
  std::string_view remaining{xml};
  while (true) {
    const auto item_start = FindIgnoreCase(remaining, "<item");
    if (item_start == std::string_view::npos)
      return results;
    const auto item_end = FindIgnoreCase(remaining, "</item", item_start);
    if (item_end == std::string_view::npos)
      return results;
    const auto item = remaining.substr(item_start, item_end - item_start);
    remaining = remaining.substr(item_end);
    const auto url = ElementText(item, "link").value_or(std::string{});
    if (url.empty())
      continue;
    auto title = ElementText(item, "title").value_or(std::string{});
    if (title.empty())
      title = "Untitled";
    const auto description =
        ElementText(item, "description").value_or(std::string{});
    results.push_back(WebSearchResultItem{
        .title = std::move(title),
        .url = url,
        .snippet = Trim(CollapseRuns(StripTags(description))),
        .published_date = ElementText(item, "pubDate").value_or(std::string{}),
    });
  }
}

std::expected<std::vector<WebSearchResultItem>, WebToolError>
ParseBingRssSearchResponse(std::string_view body) {
  return ParseRssItems(body);
}

using WebSearchRequestBuilder =
    std::expected<WebHttpRequestPlan, WebToolError> (*)(
        const domain::WebSearchConfig &config, std::string_view query,
        std::int32_t limit);
using WebSearchResponseParser =
    std::expected<std::vector<WebSearchResultItem>, WebToolError> (*)(
        std::string_view body);

struct WebSearchProviderPlan final {
  domain::WebSearchProvider provider;
  WebSearchRequestBuilder build_request;
  WebSearchResponseParser parse_response;
};

// WebSearchService registers bing_rss_free, tavily, serpapi, bing and brave;
// every other provider id (including custom) falls back to the default
// provider, which is the last row here.
constexpr std::array kWebSearchProviderPlans{
    WebSearchProviderPlan{domain::WebSearchProvider::bing_rss_free,
                          &BuildBingRssSearchRequest,
                          &ParseBingRssSearchResponse},
    WebSearchProviderPlan{domain::WebSearchProvider::tavily,
                          &BuildTavilySearchRequest, &ParseTavilyResponse},
    WebSearchProviderPlan{domain::WebSearchProvider::brave_search,
                          &BuildQueryCountSearchRequest, &ParseBraveResponse},
    WebSearchProviderPlan{domain::WebSearchProvider::serp_api,
                          &BuildSerpApiSearchRequest, &ParseSerpApiResponse},
    WebSearchProviderPlan{domain::WebSearchProvider::bing_search,
                          &BuildQueryCountSearchRequest, &ParseBingResponse},
    WebSearchProviderPlan{domain::WebSearchProvider::custom,
                          &BuildDefaultSearchRequest,
                          &ParseDefaultSearchResponse},
};

const WebSearchProviderPlan &
PlanFor(domain::WebSearchProvider provider) noexcept {
  const auto found = std::ranges::find(kWebSearchProviderPlans, provider,
                                       &WebSearchProviderPlan::provider);
  return found == kWebSearchProviderPlans.end() ? kWebSearchProviderPlans.back()
                                                : *found;
}

std::optional<std::string> HostOf(std::string_view authority) {
  if (const auto at = authority.rfind('@'); at != std::string_view::npos)
    authority = authority.substr(at + 1U);
  if (authority.empty())
    return std::nullopt;
  if (authority.front() == '[') {
    const auto close = authority.find(']');
    if (close == std::string_view::npos)
      return std::nullopt;
    return Lower(authority.substr(1U, close - 1U));
  }
  const auto colon = authority.find(':');
  const auto host =
      colon == std::string_view::npos ? authority : authority.substr(0U, colon);
  if (host.empty())
    return std::nullopt;
  return Lower(host);
}

// UrlPolicy.isPrivateIpv4: literal dotted-quad in 10/8, 172.16/12, 192.168/16
// or 127/8. Host names that merely start with those digits stay public.
bool IsPrivateIpv4(std::string_view host) {
  std::array<int, 4> octets{};
  std::size_t index = 0;
  for (std::size_t part = 0; part < octets.size(); ++part) {
    const auto dot = host.find('.', index);
    const auto end = dot == std::string_view::npos ? host.size() : dot;
    const auto digits = host.substr(index, end - index);
    if (digits.empty() || digits.size() > 3U)
      return false;
    int value = 0;
    for (const char digit : digits) {
      const auto byte = static_cast<unsigned char>(digit);
      if (byte < '0' || byte > '9')
        return false;
      value = value * 10 + (byte - '0');
    }
    if (value > 255)
      return false;
    octets[part] = value;
    if (dot == std::string_view::npos)
      return part + 1U == octets.size() &&
             (octets[0] == 10 || octets[0] == 127 ||
              (octets[0] == 172 && octets[1] >= 16 && octets[1] <= 31) ||
              (octets[0] == 192 && octets[1] == 168));
    if (part + 1U == octets.size())
      return false;
    index = dot + 1U;
  }
  return false;
}

bool IsAllowedCleartextHost(std::string_view host) {
  return host == "localhost" || host == "127.0.0.1" || host == "10.0.2.2" ||
         host == "::1" || IsPrivateIpv4(host);
}

} // namespace

std::expected<domain::WebSearchConfig, application::WebToolError>
ValidateWebSearchConfiguration(domain::WebSearchConfig config) {
  config = domain::NormalizeWebSearchConfig(std::move(config));
  if (WebSearchRequiresApiKey(config.provider)) {
    if (config.base_url.empty() || config.api_key.empty()) {
      return std::unexpected(Error(
          WebToolErrorCode::not_configured,
          "Web search not configured. Please fill in the search API, "
          "model/search source and key in MCP tool settings."));
    }
  } else if (config.base_url.empty() &&
             config.provider != domain::WebSearchProvider::bing_rss_free) {
    // Kept for parity with WebSearchService.search; the provider check above
    // already decides this branch for every known provider.
    return std::unexpected(Error(
        WebToolErrorCode::not_configured,
        "Web search not configured. Please fill in the search API URL in MCP "
        "tool settings."));
  }
  return config;
}

std::int32_t ClampWebSearchResultLimit(std::int32_t limit) noexcept {
  const auto resolved = limit <= 0 ? kDefaultSearchLimit : limit;
  return std::clamp(resolved, 1, kMaximumSearchLimit);
}

std::int32_t ClampWebFetchCharacters(std::int32_t max_characters) noexcept {
  const auto resolved =
      max_characters <= 0 ? kDefaultFetchCharacters : max_characters;
  return std::clamp(resolved, kMinimumFetchCharacters,
                    kMaximumFetchCharacters);
}

std::expected<WebHttpRequestPlan, application::WebToolError>
BuildWebSearchRequest(const domain::WebSearchConfig &config,
                      std::string_view query, std::int32_t limit) {
  if (query.empty()) {
    return std::unexpected(
        Error(WebToolErrorCode::invalid_arguments,
              "Search query cannot be empty."));
  }
  auto plan = PlanFor(config.provider)
                  .build_request(config, query,
                                 ClampWebSearchResultLimit(limit));
  if (!plan)
    return std::unexpected(std::move(plan.error()));
  if (plan->url.empty()) {
    return std::unexpected(Error(
        WebToolErrorCode::not_configured,
        "Web search not configured. Please fill in the search API URL in MCP "
        "tool settings."));
  }
  return plan;
}

std::expected<std::vector<application::WebSearchResultItem>,
              application::WebToolError>
ParseWebSearchResponse(domain::WebSearchProvider provider,
                       std::string_view body) {
  return PlanFor(provider).parse_response(body);
}

std::expected<std::string, application::WebToolError>
ValidateWebUrl(std::string_view url) {
  const auto trimmed = Trim(url);
  const auto scheme_end = trimmed.find("://");
  if (scheme_end == std::string::npos) {
    return std::unexpected(Error(WebToolErrorCode::unsupported_url,
                                 "URL must start with http:// or https://."));
  }
  const auto scheme = Lower(std::string_view{trimmed}.substr(0U, scheme_end));
  if (scheme != "http" && scheme != "https") {
    return std::unexpected(Error(WebToolErrorCode::unsupported_url,
                                 "URL must start with http:// or https://."));
  }
  const auto authority_start = scheme_end + 3U;
  const auto authority_end = trimmed.find_first_of("/?#", authority_start);
  const auto authority = std::string_view{trimmed}.substr(
      authority_start, authority_end == std::string::npos
                           ? std::string_view::npos
                           : authority_end - authority_start);
  const auto host = HostOf(authority);
  if (!host) {
    return std::unexpected(Error(WebToolErrorCode::unsupported_url,
                                 "URL must start with http:// or https://."));
  }
  if (scheme == "http" && !IsAllowedCleartextHost(*host)) {
    return std::unexpected(Error(
        WebToolErrorCode::unsupported_url,
        "URL using HTTP cleartext is only allowed for localhost, 127.0.0.1, "
        "or 10.0.2.2."));
  }
  return trimmed;
}

std::string HtmlToPlainText(std::string_view html) {
  auto value = RemoveElementBlocks(html, "script");
  value = RemoveElementBlocks(value, "style");
  value = RemoveElementBlocks(value, "noscript");
  value = StripTags(value);
  value = DecodeEntities(value);
  value = CollapseRuns(value);
  return Trim(value);
}

std::string CompactWebText(std::string_view text) {
  std::string compact;
  compact.reserve(text.size());
  std::size_t index = 0;
  while (index < text.size()) {
    if (text[index] != '\n') {
      compact.push_back(text[index]);
      ++index;
      continue;
    }
    std::size_t run = 0;
    while (index + run < text.size() && text[index + run] == '\n')
      ++run;
    if (run > 2U)
      compact += "\n\n";
    else
      compact.append(text.substr(index, run));
    index += run;
  }
  return Trim(compact);
}

std::string TruncateWebText(std::string_view text,
                            std::int32_t max_characters) {
  const auto limit = static_cast<std::size_t>(std::max(max_characters, 0));
  const auto characters = Utf8CharacterCount(text);
  if (characters <= limit)
    return std::string{text};
  std::string truncated{text.substr(0U, Utf8PrefixBytes(text, limit))};
  truncated += "\n\n[Content truncated, original length approx. ";
  truncated += std::to_string(characters);
  truncated += " characters]";
  return truncated;
}

std::string NormalizeLanguageTag(std::string_view value) {
  auto tag = Trim(value);
  if (const auto cut = tag.find_first_of(".[@,;");
      cut != std::string::npos) {
    tag.resize(cut);
  }
  std::ranges::replace(tag, '_', '-');
  std::vector<std::string> parts;
  std::string current;
  for (const char character : tag) {
    if (character == '-') {
      if (!current.empty())
        parts.push_back(std::move(current));
      current.clear();
      continue;
    }
    current.push_back(character);
  }
  if (!current.empty())
    parts.push_back(std::move(current));
  if (parts.empty())
    return {};
  auto language = Lower(parts.front());
  if (language.size() < 2U || language.size() > 3U)
    return {};
  if (!std::ranges::all_of(language, [](unsigned char byte) {
        return std::isalpha(byte) != 0;
      })) {
    return {};
  }
  std::string normalized{language};
  for (std::size_t index = 1U; index < parts.size(); ++index) {
    const auto &part = parts[index];
    const bool alphabetic = std::ranges::all_of(part, [](unsigned char byte) {
      return std::isalpha(byte) != 0;
    });
    const bool numeric = std::ranges::all_of(part, [](unsigned char byte) {
      return std::isdigit(byte) != 0;
    });
    if (part.size() == 4U && alphabetic) {
      auto script = Lower(part);
      script.front() = static_cast<char>(
          std::toupper(static_cast<unsigned char>(script.front())));
      normalized += "-" + script;
      continue;
    }
    if ((part.size() == 2U && alphabetic) || (part.size() == 3U && numeric)) {
      normalized += "-" + Upper(part);
      continue;
    }
  }
  return normalized;
}

std::string WebSearchMarket() {
  for (const auto *name : {"LC_ALL", "LC_MESSAGES", "LANG"}) {
    const auto *value = std::getenv(name);
    if (value == nullptr)
      continue;
    auto normalized = NormalizeLanguageTag(value);
    if (!normalized.empty())
      return normalized;
  }
  return "en-US";
}

} // namespace linecode::infrastructure
