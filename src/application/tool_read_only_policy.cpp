#include "application/tool_read_only_policy.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <ranges>
#include <string>
#include <vector>

namespace linecode::application {
namespace {

[[nodiscard]] std::string Trim(std::string_view value) {
  const auto first = value.find_first_not_of(" \t\r\n");
  if (first == std::string_view::npos)
    return {};
  const auto last = value.find_last_not_of(" \t\r\n");
  return std::string{value.substr(first, last - first + 1U)};
}

[[nodiscard]] std::vector<std::string> SplitWords(std::string_view value) {
  std::vector<std::string> words;
  for (const auto part : value | std::views::split(' ')) {
    const std::string_view word{part.begin(), part.end()};
    if (!word.empty())
      words.emplace_back(word);
  }
  return words;
}

// Commands that only read. `git` and `sed` need their arguments checked too, so
// they are handled separately below.
constexpr std::array<std::string_view, 25> kReadOnlyCommands{
    "pwd",   "ls",     "find",  "cat",    "head",  "tail",  "grep",
    "rg",    "wc",     "stat",  "file",   "du",    "df",    "tree",
    "which", "whoami", "echo",  "realpath", "basename", "dirname",
    "sort",  "uniq",   "cut",   "date",   "uname",
};

[[nodiscard]] bool IsReadOnlyVerb(std::string_view verb) {
  return std::ranges::find(kReadOnlyCommands, verb) != kReadOnlyCommands.end();
}

[[nodiscard]] bool IsReadOnlyGitInvocation(const std::vector<std::string> &words) {
  if (words.size() < 2U)
    return false;
  constexpr std::array<std::string_view, 10> kReadOnlyGitSubcommands{
      "status", "diff",   "log",   "show",  "branch",
      "blame",  "remote", "rev-parse", "ls-files", "describe"};
  return std::ranges::find(kReadOnlyGitSubcommands, words[1]) !=
         kReadOnlyGitSubcommands.end();
}

// One pipeline segment: a verb plus arguments that must not themselves write.
[[nodiscard]] bool IsReadOnlySegment(std::string_view segment) {
  const auto words = SplitWords(Trim(segment));
  if (words.empty())
    return false;
  const auto &verb = words.front();
  if (verb == "git")
    return IsReadOnlyGitInvocation(words);
  if (verb == "sed") {
    // `sed -i` edits in place; everything else only prints.
    return std::ranges::none_of(words, [](const std::string &word) {
      return word == "-i" || word.starts_with("-i") ||
             word.starts_with("--in-place");
    });
  }
  return IsReadOnlyVerb(verb);
}

} // namespace

bool IsReadOnlyShellCommand(std::string_view command) noexcept {
  try {
    const auto trimmed = Trim(command);
    if (trimmed.empty())
      return false;

    // Shell metacharacters that redirect, substitute, chain or background make
    // the command's effect unprovable, so they are refused outright.
    for (const std::string_view forbidden :
         {">", "<", "`", "$(", "${", ";", "&&", "&", "\n", "\r", "\t"}) {
      if (trimmed.find(forbidden) != std::string::npos)
        return false;
    }

    // A pipeline is fine as long as every stage only reads.
    for (const auto stage : std::string_view{trimmed} | std::views::split('|')) {
      if (!IsReadOnlySegment(std::string_view{stage.begin(), stage.end()}))
        return false;
    }
    return true;
  } catch (...) {
    // Refuse on any unexpected failure rather than allowing the command.
    return false;
  }
}

} // namespace linecode::application
