#include "domain/memory_rag.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cmath>
#include <ranges>
#include <unordered_map>

namespace linecode::domain {
namespace {

constexpr double kRecencyWeight = 0.15;
constexpr double kBm25K = 1.2;
constexpr double kBm25B = 0.75;
constexpr std::size_t kMaximumTermBytes = 32;
constexpr std::size_t kMaximumExtractedCharacters = 320;

constexpr std::array<std::string_view, 25> kStopWords{
    "the",  "and",  "for",  "with", "this", "that", "from", "have", "into",
    "your", "you",  "are",  "how",  "一个", "这个", "那个", "怎么", "如何",
    "什么", "以及", "或者", "可以", "需要", "进行", "使用",
};

constexpr std::array<std::string_view, 12> kSensitiveMarkers{
    "api key", "apikey",      "password", "passwd", "secret", "cookie",
    "token",   "private key", "私钥",     "密码",   "密钥",   "sk-",
};

struct ExtractionPrefix final {
  std::string_view value;
  MemoryScope scope;
};

constexpr std::array kExtractionPrefixes{
    ExtractionPrefix{"请记住项目：", MemoryScope::project},
    ExtractionPrefix{"请记住项目:", MemoryScope::project},
    ExtractionPrefix{"记住项目：", MemoryScope::project},
    ExtractionPrefix{"记住项目:", MemoryScope::project},
    ExtractionPrefix{"请记住环境：", MemoryScope::environment},
    ExtractionPrefix{"请记住环境:", MemoryScope::environment},
    ExtractionPrefix{"记住环境：", MemoryScope::environment},
    ExtractionPrefix{"记住环境:", MemoryScope::environment},
    ExtractionPrefix{"请记住：", MemoryScope::user},
    ExtractionPrefix{"请记住:", MemoryScope::user},
    ExtractionPrefix{"记住：", MemoryScope::user},
    ExtractionPrefix{"记住:", MemoryScope::user},
    ExtractionPrefix{"remember that ", MemoryScope::user},
    ExtractionPrefix{"remember: ", MemoryScope::user},
};

std::string Trim(std::string_view value) {
  const auto whitespace = [](unsigned char byte) { return byte <= 0x20U; };
  while (!value.empty() && whitespace(value.front()))
    value.remove_prefix(1);
  while (!value.empty() && whitespace(value.back()))
    value.remove_suffix(1);
  return std::string{value};
}

std::string LowerAscii(std::string_view value) {
  std::string result{value};
  std::ranges::transform(result, result.begin(), [](unsigned char byte) {
    return byte < 0x80U ? static_cast<char>(std::tolower(byte))
                        : static_cast<char>(byte);
  });
  return result;
}

bool IsLatinTokenByte(unsigned char byte) noexcept {
  return (byte >= 'a' && byte <= 'z') || (byte >= '0' && byte <= '9') ||
         byte == '_' || byte == '#' || byte == '.' || byte == '-';
}

std::vector<std::string_view> Utf8Characters(std::string_view value) {
  std::vector<std::string_view> result;
  for (std::size_t offset = 0; offset < value.size();) {
    const auto first = static_cast<unsigned char>(value[offset]);
    std::size_t length = first < 0x80U   ? 1U
                         : first < 0xE0U ? 2U
                         : first < 0xF0U ? 3U
                                         : 4U;
    length = std::min(length, value.size() - offset);
    result.push_back(value.substr(offset, length));
    offset += length;
  }
  return result;
}

bool IsCjk(std::string_view character) noexcept {
  if (character.size() != 3U)
    return false;
  const auto a = static_cast<unsigned char>(character[0]);
  const auto b = static_cast<unsigned char>(character[1]);
  const auto c = static_cast<unsigned char>(character[2]);
  if ((b & 0xC0U) != 0x80U || (c & 0xC0U) != 0x80U)
    return false;
  const std::uint32_t codepoint =
      (static_cast<std::uint32_t>(a & 0x0FU) << 12U) |
      (static_cast<std::uint32_t>(b & 0x3FU) << 6U) |
      static_cast<std::uint32_t>(c & 0x3FU);
  return codepoint >= 0x4E00U && codepoint <= 0x9FFFU;
}

void AddKeyword(std::vector<std::string> &keywords, std::string term) {
  while (!term.empty() && (term.front() == '.' || term.front() == '-' ||
                           term.front() == '_' || term.front() == '#')) {
    term.erase(term.begin());
  }
  while (!term.empty() && (term.back() == '.' || term.back() == '-' ||
                           term.back() == '_' || term.back() == '#')) {
    term.pop_back();
  }
  if (term.size() < 2U)
    return;
  if (term.size() > kMaximumTermBytes)
    term.resize(kMaximumTermBytes);
  if (std::ranges::find(kStopWords, std::string_view{term}) == kStopWords.end())
    keywords.push_back(std::move(term));
}

void AddCjkChunk(std::vector<std::string> &keywords,
                 std::span<const std::string_view> chunk) {
  if (chunk.size() < 2U)
    return;
  if (chunk.size() <= 4U) {
    std::string joined;
    for (const auto character : chunk)
      joined.append(character);
    AddKeyword(keywords, std::move(joined));
    return;
  }
  for (std::size_t index = 1; index < chunk.size(); ++index) {
    AddKeyword(keywords,
               std::string{chunk[index - 1]} + std::string{chunk[index]});
  }
}

std::unordered_map<std::string, std::size_t>
TermFrequency(std::span<const std::string> terms) {
  std::unordered_map<std::string, std::size_t> result;
  for (const auto &term : terms)
    ++result[term];
  return result;
}

std::string Utf8Prefix(std::string_view value, std::size_t characters) {
  const auto parts = Utf8Characters(value);
  std::string result;
  for (const auto part : parts | std::views::take(characters))
    result.append(part);
  return result;
}

} // namespace

std::vector<std::string> ExtractMemoryKeywords(std::string_view input) {
  const auto lowered = LowerAscii(input);
  const auto characters = Utf8Characters(lowered);
  std::vector<std::string> keywords;
  std::string latin;
  std::vector<std::string_view> cjk;
  const auto flush_latin = [&] {
    AddKeyword(keywords, std::move(latin));
    latin.clear();
  };
  const auto flush_cjk = [&] {
    AddCjkChunk(keywords, cjk);
    cjk.clear();
  };
  for (const auto character : characters) {
    if (character.size() == 1U &&
        IsLatinTokenByte(static_cast<unsigned char>(character.front()))) {
      latin.push_back(character.front());
    } else {
      flush_latin();
    }
    if (IsCjk(character)) {
      cjk.push_back(character);
    } else {
      flush_cjk();
    }
  }
  flush_latin();
  flush_cjk();
  return keywords;
}

double MemoryRelevance(std::string_view query, std::string_view text) {
  const auto query_keywords = ExtractMemoryKeywords(query);
  const auto document_keywords = ExtractMemoryKeywords(text);
  if (query_keywords.empty() || document_keywords.empty())
    return 0.0;
  const auto query_terms = TermFrequency(query_keywords);
  const auto document_terms = TermFrequency(document_keywords);
  const double document_length =
      static_cast<double>(std::max<std::size_t>(1U, document_keywords.size()));
  const auto lowered_text = LowerAscii(text);
  double score{};
  for (const auto &[term, query_frequency] : query_terms) {
    const auto found = document_terms.find(term);
    const double document_frequency =
        found == document_terms.end()
            ? (lowered_text.contains(term) ? 1.0 : 0.0)
            : static_cast<double>(found->second);
    if (document_frequency <= 0.0)
      continue;
    const double frequency =
        document_frequency /
        (document_frequency + kBm25K + kBm25B * document_length / 100.0);
    score += (1.0 + std::log(static_cast<double>(query_frequency))) * frequency;
  }
  const auto phrase = LowerAscii(Trim(query));
  if (Utf8Characters(phrase).size() >= 4U && lowered_text.contains(phrase))
    score += 2.0;
  return score;
}

double MemoryRecencyBoost(std::int64_t updated_at, std::int64_t now) noexcept {
  if (updated_at <= 0 || now <= 0)
    return 0.0;
  constexpr double day_millis = 86'400'000.0;
  const double age_days =
      std::max(0.0, static_cast<double>(now - updated_at) / day_millis);
  return 1.0 / (1.0 + age_days / 30.0);
}

std::vector<MemoryCandidate>
RankMemoryCandidates(std::vector<MemoryCandidate> candidates,
                     std::string_view query, std::size_t limit,
                     bool allow_recent_fallback, double boost,
                     std::int64_t now) {
  if (limit == 0U)
    return {};
  bool has_matches{};
  for (auto &candidate : candidates) {
    candidate.relevance = MemoryRelevance(query, candidate.search_text);
    candidate.score =
        candidate.relevance +
        MemoryRecencyBoost(candidate.updated_at, now) * kRecencyWeight +
        std::max(0.0, boost);
    has_matches = has_matches || candidate.relevance > 0.0;
  }
  if (!has_matches && !allow_recent_fallback)
    return {};
  std::ranges::sort(candidates, [](const auto &left, const auto &right) {
    return left.score == right.score ? left.updated_at > right.updated_at
                                     : left.score > right.score;
  });
  std::vector<MemoryCandidate> selected;
  selected.reserve(std::min(limit, candidates.size()));
  for (auto &candidate : candidates) {
    if ((has_matches && candidate.relevance <= 0.0) ||
        (!has_matches && candidate.score <= 0.0)) {
      continue;
    }
    selected.push_back(std::move(candidate));
    if (selected.size() == limit)
      break;
  }
  return selected;
}

bool LooksLikeSensitiveMemory(std::string_view content) {
  const auto lowered = LowerAscii(content);
  return std::ranges::any_of(kSensitiveMarkers, [&](std::string_view marker) {
    return lowered.contains(marker);
  });
}

std::string NormalizedMemoryKey(std::string_view content) {
  const auto lowered = LowerAscii(content);
  std::string result;
  for (const auto character : Utf8Characters(lowered)) {
    if ((character.size() == 1U &&
         std::isalnum(static_cast<unsigned char>(character.front())) != 0) ||
        IsCjk(character)) {
      result.append(character);
    }
  }
  return result;
}

std::optional<ExtractedMemory>
ExplicitMemoryExtractionPolicy::Extract(std::string_view user_text) const {
  const auto trimmed = Trim(user_text);
  const auto lowered = LowerAscii(trimmed);
  const auto prefix = std::ranges::find_if(
      kExtractionPrefixes, [&](const ExtractionPrefix &candidate) {
        return lowered.starts_with(candidate.value);
      });
  if (prefix == kExtractionPrefixes.end())
    return std::nullopt;
  std::string content =
      Trim(std::string_view{trimmed}.substr(prefix->value.size()));
  if (content.empty() || LooksLikeSensitiveMemory(content))
    return std::nullopt;
  if (Utf8Characters(content).size() > kMaximumExtractedCharacters) {
    content = Utf8Prefix(content, kMaximumExtractedCharacters - 1U);
    content += "。";
  }
  return ExtractedMemory{
      .scope = prefix->scope, .content = std::move(content), .confidence = 1.0};
}

} // namespace linecode::domain
