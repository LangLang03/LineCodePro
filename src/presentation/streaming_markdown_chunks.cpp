#include "presentation/streaming_markdown_chunks.h"

#include <array>
#include <cctype>
#include <cstddef>
#include <string_view>
#include <utility>
#include <vector>

namespace linecode::presentation {
namespace {

std::string_view Trim(std::string_view line) noexcept {
  while (!line.empty() && (line.front() == ' ' || line.front() == '\t' ||
                           line.front() == '\r'))
    line.remove_prefix(1);
  while (!line.empty() && (line.back() == ' ' || line.back() == '\t' ||
                           line.back() == '\r'))
    line.remove_suffix(1);
  return line;
}

bool ContinuesContainer(std::string_view line) noexcept {
  return !line.empty() &&
         (line.front() == ' ' || line.front() == '\t' ||
          Trim(line).starts_with('>'));
}

bool StartsRawTag(std::string_view line, std::string_view tag) noexcept {
  if (line.size() <= tag.size() + 1U || line.front() != '<')
    return false;
  for (std::size_t index = 0; index < tag.size(); ++index) {
    if (std::tolower(static_cast<unsigned char>(line[index + 1U])) !=
        tag[index])
      return false;
  }
  const char boundary = line[tag.size() + 1U];
  return boundary == '>' || boundary == '/' ||
         std::isspace(static_cast<unsigned char>(boundary));
}

std::string_view RawHtmlEnd(std::string_view line) noexcept {
  if (line.starts_with("<!--"))
    return "-->";
  if (line.starts_with("<?"))
    return "?>";
  if (line.starts_with("<![CDATA["))
    return "]]>";
  constexpr std::array<std::pair<std::string_view, std::string_view>, 4> tags{
      std::pair<std::string_view, std::string_view>{"script", "</script>"},
      std::pair<std::string_view, std::string_view>{"pre", "</pre>"},
      std::pair<std::string_view, std::string_view>{"style", "</style>"},
      std::pair<std::string_view, std::string_view>{"textarea", "</textarea>"},
  };
  for (const auto &[tag, closing] : tags) {
    if (StartsRawTag(line, tag))
      return closing;
  }
  return {};
}

bool ContainsAsciiCaseInsensitive(std::string_view text,
                                  std::string_view needle) noexcept {
  for (std::size_t at = 0; at + needle.size() <= text.size(); ++at) {
    bool match = true;
    for (std::size_t index = 0; index < needle.size(); ++index) {
      if (std::tolower(static_cast<unsigned char>(text[at + index])) !=
          needle[index]) {
        match = false;
        break;
      }
    }
    if (match)
      return true;
  }
  return false;
}

} // namespace

std::vector<StreamingMarkdownChunk>
SplitStreamingMarkdown(std::string_view markdown) {
  std::vector<StreamingMarkdownChunk> chunks;
  std::size_t start = 0;
  std::size_t line_start = 0;
  bool fenced = false;
  std::string_view html_end;
  while (line_start < markdown.size()) {
    const auto end = markdown.find('\n', line_start);
    const auto line_end = end == std::string_view::npos ? markdown.size() : end;
    const auto line = markdown.substr(line_start, line_end - line_start);
    const auto text = Trim(line);
    if (!fenced && html_end.empty()) {
      html_end = RawHtmlEnd(text);
      if (!html_end.empty() && ContainsAsciiCaseInsensitive(text, html_end))
        html_end = {};
    } else if (!html_end.empty() &&
               ContainsAsciiCaseInsensitive(text, html_end)) {
      html_end = {};
    }
    if (html_end.empty() && text.starts_with("```"))
      fenced = !fenced;
    if (!fenced && html_end.empty() && text.empty()) {
      std::size_t next = end == std::string_view::npos ? markdown.size()
                                                       : end + 1U;
      while (next < markdown.size()) {
        const auto next_end = markdown.find('\n', next);
        const auto next_line_end = next_end == std::string_view::npos
                                       ? markdown.size()
                                       : next_end;
        const auto next_line = markdown.substr(next, next_line_end - next);
        if (!Trim(next_line).empty()) {
          if (!ContinuesContainer(next_line)) {
            if (line_start > start)
              chunks.push_back({start, line_start - start});
            start = next;
          }
          break;
        }
        next = next_end == std::string_view::npos ? markdown.size()
                                                  : next_end + 1U;
      }
    }
    if (end == std::string_view::npos)
      break;
    line_start = end + 1U;
  }
  if (start < markdown.size() && !Trim(markdown.substr(start)).empty())
    chunks.push_back({start, markdown.size() - start});
  return chunks;
}

} // namespace linecode::presentation
