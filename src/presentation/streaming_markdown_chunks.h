#pragma once

#include <cstddef>
#include <string_view>
#include <vector>

namespace linecode::presentation {

struct StreamingMarkdownChunk final {
  std::size_t start{};
  std::size_t length{};

  bool operator==(const StreamingMarkdownChunk &) const = default;
};

// Blank lines separate independently renderable blocks. A fenced code block
// and indented list/quote continuations stay together until their boundary.
[[nodiscard]] std::vector<StreamingMarkdownChunk>
SplitStreamingMarkdown(std::string_view markdown);

} // namespace linecode::presentation
