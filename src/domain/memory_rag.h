#pragma once

#include <cstddef>
#include <cstdint>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

#include "domain/memory.h"

namespace linecode::domain {

struct MemoryCandidate final {
  std::string id;
  std::string search_text;
  std::string formatted;
  std::int64_t updated_at{};
  double relevance{};
  double score{};

  bool operator==(const MemoryCandidate &) const = default;
};

[[nodiscard]] std::vector<std::string>
ExtractMemoryKeywords(std::string_view input);
[[nodiscard]] double MemoryRelevance(std::string_view query,
                                     std::string_view text);
[[nodiscard]] double MemoryRecencyBoost(std::int64_t updated_at,
                                        std::int64_t now) noexcept;
[[nodiscard]] std::vector<MemoryCandidate>
RankMemoryCandidates(std::vector<MemoryCandidate> candidates,
                     std::string_view query, std::size_t limit,
                     bool allow_recent_fallback, double boost,
                     std::int64_t now);

struct ExtractedMemory final {
  MemoryScope scope{MemoryScope::user};
  std::string content;
  double confidence{1.0};

  bool operator==(const ExtractedMemory &) const = default;
};

class MemoryExtractionPolicy {
public:
  virtual ~MemoryExtractionPolicy() = default;
  [[nodiscard]] virtual std::optional<ExtractedMemory>
  Extract(std::string_view user_text) const = 0;
};

class ExplicitMemoryExtractionPolicy final : public MemoryExtractionPolicy {
public:
  [[nodiscard]] std::optional<ExtractedMemory>
  Extract(std::string_view user_text) const override;
};

[[nodiscard]] bool LooksLikeSensitiveMemory(std::string_view content);
[[nodiscard]] std::string NormalizedMemoryKey(std::string_view content);

} // namespace linecode::domain
