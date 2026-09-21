#include "infrastructure/tutorial_markdown_parser.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <charconv>
#include <cstddef>
#include <cstdint>
#include <memory>
#include <optional>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

namespace linecode::infrastructure {
namespace {

using domain::TutorialInline;
using domain::TutorialInlineLine;

constexpr std::size_t kMaximumImageBytes = 10U * 1024U * 1024U;
constexpr std::uint32_t kMaximumImageDimension = 16'384U;
constexpr std::uint64_t kMaximumImagePixels = 64U * 1024U * 1024U;

std::string_view Trim(std::string_view value) {
  while (!value.empty() && std::isspace(static_cast<unsigned char>(value.front())))
    value.remove_prefix(1);
  while (!value.empty() && std::isspace(static_cast<unsigned char>(value.back())))
    value.remove_suffix(1);
  return value;
}

std::vector<std::string_view> Lines(std::string_view markdown) {
  std::vector<std::string_view> result;
  std::size_t start = 0;
  while (start <= markdown.size()) {
    const auto end = markdown.find('\n', start);
    std::string_view line = markdown.substr(
        start, end == std::string_view::npos ? markdown.size() - start
                                             : end - start);
    if (!line.empty() && line.back() == '\r')
      line.remove_suffix(1);
    result.push_back(line);
    if (end == std::string_view::npos)
      break;
    start = end + 1;
  }
  return result;
}

int Base64Digit(char value) noexcept {
  if (value >= 'A' && value <= 'Z')
    return value - 'A';
  if (value >= 'a' && value <= 'z')
    return value - 'a' + 26;
  if (value >= '0' && value <= '9')
    return value - '0' + 52;
  if (value == '+')
    return 62;
  if (value == '/')
    return 63;
  return -1;
}

std::optional<std::vector<std::byte>> DecodeImageBase64(
    std::string_view payload) {
  constexpr std::size_t kMaximumPayload =
      ((kMaximumImageBytes + 2U) / 3U) * 4U;
  if (payload.empty() || payload.size() > kMaximumPayload ||
      payload.size() % 4U != 0U)
    return std::nullopt;
  std::vector<std::byte> bytes;
  bytes.reserve(payload.size() / 4U * 3U);
  for (std::size_t index{}; index < payload.size(); index += 4U) {
    const bool last = index + 4U == payload.size();
    const bool pad_two = payload[index + 2U] == '=';
    const bool pad_one = payload[index + 3U] == '=';
    if ((!last && (pad_one || pad_two)) || (pad_two && !pad_one))
      return std::nullopt;
    const int a = Base64Digit(payload[index]);
    const int b = Base64Digit(payload[index + 1U]);
    const int c = pad_two ? 0 : Base64Digit(payload[index + 2U]);
    const int d = pad_one ? 0 : Base64Digit(payload[index + 3U]);
    if (a < 0 || b < 0 || c < 0 || d < 0)
      return std::nullopt;
    if ((pad_two && (b & 0x0f) != 0) ||
        (pad_one && !pad_two && (c & 0x03) != 0))
      return std::nullopt;
    const auto bits = static_cast<std::uint32_t>(
        (a << 18) | (b << 12) | (c << 6) | d);
    bytes.push_back(static_cast<std::byte>((bits >> 16) & 0xffU));
    if (!pad_two)
      bytes.push_back(static_cast<std::byte>((bits >> 8) & 0xffU));
    if (!pad_one)
      bytes.push_back(static_cast<std::byte>(bits & 0xffU));
    if (bytes.size() > kMaximumImageBytes)
      return std::nullopt;
  }
  return bytes;
}

std::uint32_t BigEndian32(const std::vector<std::byte> &bytes,
                          std::size_t offset) noexcept {
  return (std::to_integer<std::uint32_t>(bytes[offset]) << 24U) |
         (std::to_integer<std::uint32_t>(bytes[offset + 1U]) << 16U) |
         (std::to_integer<std::uint32_t>(bytes[offset + 2U]) << 8U) |
         std::to_integer<std::uint32_t>(bytes[offset + 3U]);
}

std::uint16_t BigEndian16(const std::vector<std::byte> &bytes,
                          std::size_t offset) noexcept {
  return static_cast<std::uint16_t>(
      (std::to_integer<std::uint16_t>(bytes[offset]) << 8U) |
      std::to_integer<std::uint16_t>(bytes[offset + 1U]));
}

struct ImageMetadata final {
  std::string mime_type;
  std::uint32_t width{};
  std::uint32_t height{};
};

std::optional<ImageMetadata>
ReadPngMetadata(const std::vector<std::byte> &bytes) {
  constexpr std::array signature{
      std::byte{0x89}, std::byte{0x50}, std::byte{0x4e}, std::byte{0x47},
      std::byte{0x0d}, std::byte{0x0a}, std::byte{0x1a}, std::byte{0x0a}};
  if (bytes.size() < 24U ||
      !std::ranges::equal(signature,
                          std::span<const std::byte>{bytes}.first(8U)) ||
      bytes[12] != std::byte{'I'} || bytes[13] != std::byte{'H'} ||
      bytes[14] != std::byte{'D'} || bytes[15] != std::byte{'R'})
    return std::nullopt;
  return ImageMetadata{.mime_type = "image/png",
                       .width = BigEndian32(bytes, 16U),
                       .height = BigEndian32(bytes, 20U)};
}

bool IsJpegStartOfFrame(std::uint8_t marker) noexcept {
  constexpr std::array<std::uint8_t, 12> markers{
      0xc0, 0xc1, 0xc2, 0xc3, 0xc5, 0xc6,
      0xc7, 0xc9, 0xca, 0xcb, 0xcd, 0xce};
  return std::ranges::contains(markers, marker) || marker == 0xcf;
}

std::optional<ImageMetadata>
ReadJpegMetadata(const std::vector<std::byte> &bytes) {
  if (bytes.size() < 4U || bytes[0] != std::byte{0xff} ||
      bytes[1] != std::byte{0xd8})
    return std::nullopt;
  std::size_t cursor = 2U;
  while (cursor + 3U < bytes.size()) {
    if (bytes[cursor] != std::byte{0xff})
      return std::nullopt;
    while (cursor < bytes.size() && bytes[cursor] == std::byte{0xff})
      ++cursor;
    if (cursor >= bytes.size())
      return std::nullopt;
    const auto marker = std::to_integer<std::uint8_t>(bytes[cursor++]);
    if (marker == 0xd9 || marker == 0xda)
      return std::nullopt;
    if (marker == 0x01 || (marker >= 0xd0 && marker <= 0xd7))
      continue;
    if (cursor + 2U > bytes.size())
      return std::nullopt;
    const auto length = BigEndian16(bytes, cursor);
    if (length < 2U || cursor + length > bytes.size())
      return std::nullopt;
    if (IsJpegStartOfFrame(marker)) {
      if (length < 7U)
        return std::nullopt;
      return ImageMetadata{.mime_type = "image/jpeg",
                           .width = BigEndian16(bytes, cursor + 5U),
                           .height = BigEndian16(bytes, cursor + 3U)};
    }
    cursor += length;
  }
  return std::nullopt;
}

bool ValidImageDimensions(const ImageMetadata &metadata) noexcept {
  return metadata.width > 0U && metadata.height > 0U &&
         metadata.width <= kMaximumImageDimension &&
         metadata.height <= kMaximumImageDimension &&
         static_cast<std::uint64_t>(metadata.width) * metadata.height <=
             kMaximumImagePixels;
}

bool LooksLikeStandaloneImage(std::string_view line) noexcept {
  line = Trim(line);
  if (!line.starts_with("![") || !line.ends_with(')'))
    return false;
  const auto label_end = line.find("](", 2U);
  return label_end != std::string_view::npos && label_end + 3U <= line.size();
}

std::optional<domain::TutorialImageBlock>
ParseImage(std::string_view line) {
  line = Trim(line);
  if (!LooksLikeStandaloneImage(line))
    return std::nullopt;
  const auto label_end = line.find("](", 2U);
  if (label_end == std::string_view::npos)
    return std::nullopt;
  const auto target = line.substr(label_end + 2U,
                                  line.size() - label_end - 3U);
  const std::string alternative_text{line.substr(2U, label_end - 2U)};
  if (target.starts_with('/')) {
    return domain::TutorialImageBlock{
        .alternative_text = alternative_text,
        .source = domain::TutorialAbsolutePathImage{
            .path = std::string{target}},
    };
  }
  if (target.starts_with("file://")) {
    return domain::TutorialImageBlock{
        .alternative_text = alternative_text,
        .source = domain::TutorialFileUriImage{.uri = std::string{target}},
    };
  }
  constexpr std::string_view prefix{"data:"};
  constexpr std::string_view delimiter{";base64,"};
  if (!target.starts_with(prefix))
    return std::nullopt;
  const auto delimiter_at = target.find(delimiter, prefix.size());
  if (delimiter_at == std::string_view::npos)
    return std::nullopt;
  auto declared = target.substr(prefix.size(), delimiter_at - prefix.size());
  if (declared == "image/jpg")
    declared = "image/jpeg";
  if (declared != "image/png" && declared != "image/jpeg")
    return std::nullopt;
  auto decoded = DecodeImageBase64(target.substr(delimiter_at + delimiter.size()));
  if (!decoded)
    return std::nullopt;
  auto metadata = declared == "image/png" ? ReadPngMetadata(*decoded)
                                            : ReadJpegMetadata(*decoded);
  if (!metadata || metadata->mime_type != declared ||
      !ValidImageDimensions(*metadata))
    return std::nullopt;
  return domain::TutorialImageBlock{
      .alternative_text = alternative_text,
      .source = domain::TutorialEncodedImage{
          .mime_type = std::move(metadata->mime_type),
          .encoded = std::move(*decoded),
          .pixel_width = metadata->width,
          .pixel_height = metadata->height,
      },
  };
}

bool StartsWithAsciiCaseInsensitive(std::string_view value,
                                    std::string_view prefix) noexcept {
  return value.size() >= prefix.size() &&
         std::ranges::equal(value.substr(0U, prefix.size()), prefix,
                            [](char left, char right) {
                              return std::tolower(
                                         static_cast<unsigned char>(left)) ==
                                     std::tolower(
                                         static_cast<unsigned char>(right));
                            });
}

bool StartsWithHtmlTag(std::string_view line, std::string_view tag) noexcept {
  const std::string opening = "<" + std::string{tag};
  if (!StartsWithAsciiCaseInsensitive(line, opening) ||
      line.size() == opening.size())
    return false;
  const char boundary = line[opening.size()];
  return boundary == '>' || boundary == '/' ||
         std::isspace(static_cast<unsigned char>(boundary));
}

struct HtmlBlockStart final {
  std::string closing_token;
  bool ends_at_blank_line = false;
};

std::optional<HtmlBlockStart> HtmlBlock(std::string_view line) {
  line = Trim(line);
  if (line.starts_with("<!--"))
    return HtmlBlockStart{.closing_token = "-->"};
  if (line.starts_with("<?"))
    return HtmlBlockStart{.closing_token = "?>"};
  if (line.starts_with("<![CDATA["))
    return HtmlBlockStart{.closing_token = "]]>"};
  if (line.starts_with("<!") && line.size() > 2U &&
      std::isupper(static_cast<unsigned char>(line[2U])))
    return HtmlBlockStart{.closing_token = ">"};

  constexpr std::array<std::string_view, 4> raw_tags{
      "script", "pre", "style", "textarea"};
  for (const auto tag : raw_tags) {
    if (StartsWithHtmlTag(line, tag))
      return HtmlBlockStart{.closing_token = "</" + std::string{tag} + ">"};
  }

  if (line.size() < 3U || line.front() != '<')
    return std::nullopt;
  std::size_t name = line[1] == '/' ? 2U : 1U;
  if (name >= line.size() ||
      !std::isalpha(static_cast<unsigned char>(line[name])))
    return std::nullopt;
  while (name < line.size() &&
         (std::isalnum(static_cast<unsigned char>(line[name])) ||
          line[name] == '-'))
    ++name;
  if (name >= line.size() ||
      (line[name] != '>' && line[name] != '/' &&
       !std::isspace(static_cast<unsigned char>(line[name]))))
    return std::nullopt;
  return HtmlBlockStart{.closing_token = {}, .ends_at_blank_line = true};
}

bool ContainsAsciiCaseInsensitive(std::string_view value,
                                  std::string_view needle) noexcept {
  return std::ranges::search(value, needle,
                             [](char left, char right) {
                               return std::tolower(
                                          static_cast<unsigned char>(left)) ==
                                      std::tolower(
                                          static_cast<unsigned char>(right));
                             })
             .begin() != value.end();
}

std::string ConsumeHtmlBlock(const std::vector<std::string_view>& lines,
                             std::size_t& index,
                             const HtmlBlockStart& start) {
  std::string html;
  while (index < lines.size()) {
    const auto line = lines[index];
    if (start.ends_at_blank_line && Trim(line).empty())
      break;
    if (!html.empty())
      html.push_back('\n');
    html.append(line);
    ++index;
    if (!start.closing_token.empty() &&
        ContainsAsciiCaseInsensitive(line, start.closing_token))
      break;
  }
  return html;
}

void AppendInline(TutorialInlineLine& output, std::string_view text,
                  bool strong = false, bool emphasis = false,
                  bool code = false,
                  std::optional<std::string> link = std::nullopt) {
  if (text.empty())
    return;
  TutorialInline span{.text = std::string(text),
                      .strong = strong,
                      .emphasis = emphasis,
                      .code = code,
                      .link = std::move(link)};
  if (!output.empty() && output.back().strong == span.strong &&
      output.back().emphasis == span.emphasis &&
      output.back().code == span.code && output.back().link == span.link) {
    output.back().text += span.text;
  } else {
    output.push_back(std::move(span));
  }
}

TutorialInlineLine ParseInline(std::string_view value) {
  TutorialInlineLine result;
  std::size_t plain_start = 0;
  const auto flush_plain = [&](std::size_t end) {
    AppendInline(result, value.substr(plain_start, end - plain_start));
  };

  std::size_t index = 0;
  while (index < value.size()) {
    if (value.substr(index).starts_with("**")) {
      const auto close = value.find("**", index + 2);
      if (close != std::string_view::npos) {
        flush_plain(index);
        AppendInline(result, value.substr(index + 2, close - index - 2), true);
        index = close + 2;
        plain_start = index;
        continue;
      }
    }
    if (value[index] == '`') {
      const auto close = value.find('`', index + 1);
      if (close != std::string_view::npos) {
        flush_plain(index);
        AppendInline(result, value.substr(index + 1, close - index - 1), false,
                     false, true);
        index = close + 1;
        plain_start = index;
        continue;
      }
    }
    if (value[index] == '[') {
      const auto label_end = value.find("](", index + 1);
      const auto target_end = label_end == std::string_view::npos
                                  ? std::string_view::npos
                                  : value.find(')', label_end + 2);
      if (target_end != std::string_view::npos) {
        flush_plain(index);
        AppendInline(result, value.substr(index + 1, label_end - index - 1),
                     false, false, false,
                     std::string(value.substr(label_end + 2,
                                              target_end - label_end - 2)));
        index = target_end + 1;
        plain_start = index;
        continue;
      }
    }
    if ((value[index] == '*' || value[index] == '_') &&
        (index == 0 || value[index - 1] != '\\')) {
      const char marker = value[index];
      const auto close = value.find(marker, index + 1);
      if (close != std::string_view::npos) {
        flush_plain(index);
        AppendInline(result, value.substr(index + 1, close - index - 1), false,
                     true);
        index = close + 1;
        plain_start = index;
        continue;
      }
    }
    ++index;
  }
  flush_plain(value.size());
  return result;
}

bool IsThematicBreak(std::string_view line) {
  line = Trim(line);
  return line == "---" || line == "***" || line == "___";
}

std::optional<std::pair<std::size_t, std::string_view>> Heading(std::string_view line) {
  std::size_t level = 0;
  while (level < line.size() && level < 6 && line[level] == '#')
    ++level;
  if (level == 0 || level >= line.size() || line[level] != ' ')
    return std::nullopt;
  return std::pair{level, Trim(line.substr(level + 1))};
}

struct ListPrefix final {
  bool ordered = false;
  std::string marker;
  std::size_t depth = 0;
  std::string_view content;
};

std::optional<ListPrefix> ParseListPrefix(std::string_view line) {
  std::size_t spaces = 0;
  while (spaces < line.size() && line[spaces] == ' ')
    ++spaces;
  const std::size_t depth = spaces / 2;
  auto rest = line.substr(spaces);
  if (rest.starts_with("- ") || rest.starts_with("* ") || rest.starts_with("+ ")) {
    return ListPrefix{.ordered = false,
                      .marker = std::string(rest.substr(0, 1)),
                      .depth = depth,
                      .content = rest.substr(2)};
  }
  std::size_t digits = 0;
  while (digits < rest.size() && std::isdigit(static_cast<unsigned char>(rest[digits])))
    ++digits;
  if (digits == 0 || digits + 1 >= rest.size() || rest[digits] != '.' ||
      rest[digits + 1] != ' ')
    return std::nullopt;
  return ListPrefix{.ordered = true,
                    .marker = std::string(rest.substr(0, digits + 1)),
                    .depth = depth,
                    .content = rest.substr(digits + 2)};
}

std::vector<std::string_view> TableCells(std::string_view line) {
  line = Trim(line);
  if (line.starts_with('|'))
    line.remove_prefix(1);
  if (line.ends_with('|'))
    line.remove_suffix(1);
  std::vector<std::string_view> cells;
  std::size_t start = 0;
  while (start <= line.size()) {
    const auto end = line.find('|', start);
    cells.push_back(Trim(line.substr(
        start, end == std::string_view::npos ? line.size() - start
                                             : end - start)));
    if (end == std::string_view::npos)
      break;
    start = end + 1;
  }
  return cells;
}

bool IsTableSeparator(std::string_view line) {
  const auto cells = TableCells(line);
  return !cells.empty() && std::ranges::all_of(cells, [](std::string_view cell) {
    cell = Trim(cell);
    if (cell.starts_with(':'))
      cell.remove_prefix(1);
    if (cell.ends_with(':'))
      cell.remove_suffix(1);
    return cell.size() >= 3 &&
           std::ranges::all_of(cell, [](char value) { return value == '-'; });
  });
}

std::size_t LeadingSpaces(std::string_view line) noexcept {
  std::size_t spaces = 0;
  while (spaces < line.size() && line[spaces] == ' ')
    ++spaces;
  return spaces;
}

/// Removes up to `column` leading spaces, the way CommonMark dedents the
/// content of a container block such as a list item.
std::string_view Dedent(std::string_view line, std::size_t column) noexcept {
  const auto spaces = LeadingSpaces(line);
  line.remove_prefix(std::min(spaces, column));
  return line;
}

/// Nested block children are immutable; an empty sequence stays a null pointer
/// so "has nested blocks" is a single check at the call site.
std::shared_ptr<const domain::TutorialBlockSequence>
MakeSequence(std::vector<domain::TutorialBlock> blocks) {
  if (blocks.empty())
    return {};
  return std::make_shared<const domain::TutorialBlockSequence>(
      domain::TutorialBlockSequence{.blocks = std::move(blocks)});
}

// Deeply nested quotes or list content would otherwise recurse once per
// container; real documents never come close to this bound.
constexpr std::size_t kMaximumNestingDepth = 24U;

domain::TutorialDocument ParseLines(const std::vector<std::string_view>& lines,
                                    std::size_t depth);

/// Content lines of a list item: everything indented to at least the item's
/// content column, with fenced code blocks consumed as a unit so a `-` inside
/// a fence cannot be mistaken for the next list item.
std::vector<std::string_view> ListItemContentLines(
    const std::vector<std::string_view>& lines, std::size_t& index,
    std::size_t content_column) {
  std::vector<std::string_view> nested;
  bool in_fence = false;
  while (index < lines.size()) {
    const auto line = lines[index];
    const auto text = Trim(line);
    if (in_fence) {
      nested.push_back(Dedent(line, content_column));
      if (text.starts_with("```"))
        in_fence = false;
      ++index;
      continue;
    }
    if (text.empty()) {
      // A blank line only continues this item when deeper-indented content
      // follows; otherwise it ends the list exactly as before.
      std::size_t probe = index;
      while (probe < lines.size() && Trim(lines[probe]).empty())
        ++probe;
      if (probe >= lines.size() || ParseListPrefix(lines[probe]) ||
          LeadingSpaces(lines[probe]) < content_column)
        break;
      while (index < probe) {
        nested.push_back(Dedent(lines[index], content_column));
        ++index;
      }
      continue;
    }
    // Sibling and nested markers stay in the flat item run this parser has
    // always produced; only deeper, non-marker content belongs to the item.
    if (ParseListPrefix(line))
      break;
    if (LeadingSpaces(line) < content_column)
      break;
    if (text.starts_with("```"))
      in_fence = true;
    nested.push_back(Dedent(line, content_column));
    ++index;
  }
  return nested;
}

domain::TutorialDocument ParseLines(const std::vector<std::string_view>& lines,
                                    std::size_t depth) {
  domain::TutorialDocument document;
  if (depth > kMaximumNestingDepth) {
    // Defensive fallback for pathologically nested input: keep the text.
    std::string flattened;
    for (const auto line : lines) {
      if (!flattened.empty())
        flattened.push_back('\n');
      flattened.append(Trim(line));
    }
    if (!flattened.empty())
      document.blocks.emplace_back(
          domain::TutorialParagraph{ParseInline(flattened)});
    return document;
  }
  std::size_t index = 0;

  while (index < lines.size()) {
    const auto line = lines[index];
    if (Trim(line).empty()) {
      ++index;
      continue;
    }

    if (Trim(line).starts_with("```")) {
      const std::string language(Trim(Trim(line).substr(3)));
      std::string code;
      for (++index; index < lines.size() &&
                    !Trim(lines[index]).starts_with("```");
           ++index) {
        if (!code.empty())
          code.push_back('\n');
        code.append(lines[index]);
      }
      if (index < lines.size())
        ++index;
      document.blocks.emplace_back(
          domain::TutorialCodeBlock{language, std::move(code)});
      continue;
    }

    if (LooksLikeStandaloneImage(line)) {
      if (auto image = ParseImage(line)) {
        document.blocks.emplace_back(std::move(*image));
      } else {
        // Never expose a rejected data URI as a clickable link or keep its
        // base64 payload in the presentation tree.
        document.blocks.emplace_back(
            domain::TutorialParagraph{ParseInline("Image unavailable")});
      }
      ++index;
      continue;
    }

    if (const auto html = HtmlBlock(line)) {
      document.blocks.emplace_back(domain::TutorialHtmlBlock{
          .html = ConsumeHtmlBlock(lines, index, *html)});
      continue;
    }

    if (const auto heading = Heading(line)) {
      const std::size_t block_index = document.blocks.size();
      auto content = ParseInline(heading->second);
      const std::string title = TutorialMarkdownParser::PlainText(content);
      document.blocks.emplace_back(
          domain::TutorialHeading{heading->first, std::move(content)});
      if (heading->first == 2)
        document.sections.push_back({title, block_index});
      ++index;
      continue;
    }

    if (IsThematicBreak(line)) {
      document.blocks.emplace_back(domain::TutorialThematicBreak{});
      ++index;
      continue;
    }

    if (line.contains('|') && index + 1 < lines.size() &&
        IsTableSeparator(lines[index + 1])) {
      domain::TutorialTable table;
      for (auto cell : TableCells(line))
        table.header.push_back(ParseInline(cell));
      index += 2;
      while (index < lines.size() && lines[index].contains('|') &&
             !Trim(lines[index]).empty()) {
        std::vector<TutorialInlineLine> row;
        for (auto cell : TableCells(lines[index]))
          row.push_back(ParseInline(cell));
        table.rows.push_back(std::move(row));
        ++index;
      }
      document.blocks.emplace_back(std::move(table));
      continue;
    }

    if (const auto first = ParseListPrefix(line)) {
      domain::TutorialList list{.ordered = first->ordered, .items = {}};
      while (index < lines.size()) {
        const auto item = ParseListPrefix(lines[index]);
        if (!item)
          break;
        // The content column is where the marker line's own text starts, so
        // `- x` continues at column 2 and `1. x` at column 3.
        const auto content_column = static_cast<std::size_t>(
            item->content.data() - lines[index].data());
        domain::TutorialListItem entry{.marker = item->marker,
                                       .depth = item->depth,
                                       .content = ParseInline(item->content)};
        ++index;
        auto nested = ListItemContentLines(lines, index, content_column);
        if (!nested.empty())
          entry.blocks = MakeSequence(ParseLines(nested, depth + 1).blocks);
        list.items.push_back(std::move(entry));
      }
      document.blocks.emplace_back(std::move(list));
      continue;
    }

    if (Trim(line).starts_with('>')) {
      std::vector<std::string_view> inner;
      while (index < lines.size() && Trim(lines[index]).starts_with('>')) {
        auto text = Trim(lines[index]);
        text.remove_prefix(1);
        inner.push_back(Trim(text));
        ++index;
      }
      document.blocks.emplace_back(domain::TutorialQuote{
          .blocks = MakeSequence(ParseLines(inner, depth + 1).blocks)});
      continue;
    }

    std::string paragraph;
    while (index < lines.size() && !Trim(lines[index]).empty() &&
           !Heading(lines[index]) && !IsThematicBreak(lines[index]) &&
           !Trim(lines[index]).starts_with("```") &&
           !Trim(lines[index]).starts_with('>') &&
           !LooksLikeStandaloneImage(lines[index]) &&
           !HtmlBlock(lines[index]) &&
           !ParseListPrefix(lines[index]) &&
           !(lines[index].contains('|') && index + 1 < lines.size() &&
             IsTableSeparator(lines[index + 1]))) {
      if (!paragraph.empty())
        paragraph.push_back('\n');
      paragraph.append(Trim(lines[index]));
      ++index;
    }
    if (!paragraph.empty())
      document.blocks.emplace_back(
          domain::TutorialParagraph{ParseInline(paragraph)});
  }
  return document;
}

} // namespace

domain::TutorialDocument TutorialMarkdownParser::Parse(
    std::string_view markdown) const {
  return ParseLines(Lines(markdown), 0U);
}

std::string TutorialMarkdownParser::PlainText(
    const domain::TutorialInlineLine& line) {
  std::string text;
  for (const auto& span : line)
    text += span.text;
  return text;
}

std::string TutorialMarkdownParser::ShortSectionTitle(std::string_view title) {
  if (const auto dot = title.find('.'); dot != std::string_view::npos)
    title = Trim(title.substr(dot + 1));
  if (const auto colon = title.find("："); colon != std::string_view::npos)
    title = Trim(title.substr(colon + std::string_view("：").size()));
  return std::string(title);
}

} // namespace linecode::infrastructure
