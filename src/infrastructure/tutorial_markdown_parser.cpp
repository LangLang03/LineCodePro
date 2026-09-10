#include "infrastructure/tutorial_markdown_parser.h"

#include <algorithm>
#include <cctype>
#include <charconv>
#include <ranges>
#include <string>
#include <utility>
#include <vector>

namespace linecode::infrastructure {
namespace {

using domain::TutorialInline;
using domain::TutorialInlineLine;

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

} // namespace

domain::TutorialDocument TutorialMarkdownParser::Parse(
    std::string_view markdown) const {
  domain::TutorialDocument document;
  const auto lines = Lines(markdown);
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

    if (const auto heading = Heading(line)) {
      const std::size_t block_index = document.blocks.size();
      auto content = ParseInline(heading->second);
      const std::string title = PlainText(content);
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
        list.items.push_back({item->marker, item->depth,
                              ParseInline(item->content)});
        ++index;
      }
      document.blocks.emplace_back(std::move(list));
      continue;
    }

    if (Trim(line).starts_with('>')) {
      std::string quote;
      while (index < lines.size() && Trim(lines[index]).starts_with('>')) {
        auto text = Trim(lines[index]);
        text.remove_prefix(1);
        text = Trim(text);
        if (!quote.empty())
          quote.push_back('\n');
        quote.append(text);
        ++index;
      }
      document.blocks.emplace_back(domain::TutorialQuote{ParseInline(quote)});
      continue;
    }

    std::string paragraph;
    while (index < lines.size() && !Trim(lines[index]).empty() &&
           !Heading(lines[index]) && !IsThematicBreak(lines[index]) &&
           !Trim(lines[index]).starts_with("```") &&
           !Trim(lines[index]).starts_with('>') &&
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
