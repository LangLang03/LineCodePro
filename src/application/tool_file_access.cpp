#include "application/ports/tool_file_access.h"

#include <cstddef>
#include <string_view>

namespace linecode::application {
namespace {

// Mirrors the legacy glob-to-regex translation: "**" becomes ".*", "*"
// becomes "[^/]*", "?" becomes "[^/]", and every other character matches
// itself literally.
bool MatchAt(std::string_view pattern, std::size_t index, std::string_view value,
             std::size_t position) {
  while (index < pattern.size()) {
    const char token = pattern[index];
    if (token == '*') {
      if (index + 1 < pattern.size() && pattern[index + 1] == '*') {
        for (std::size_t next = position; next <= value.size(); ++next) {
          if (MatchAt(pattern, index + 2, value, next))
            return true;
        }
        return false;
      }
      if (MatchAt(pattern, index + 1, value, position))
        return true;
      for (std::size_t next = position;
           next < value.size() && value[next] != '/'; ++next) {
        if (MatchAt(pattern, index + 1, value, next + 1))
          return true;
      }
      return false;
    }
    if (token == '?') {
      if (position >= value.size() || value[position] == '/')
        return false;
      ++index;
      ++position;
      continue;
    }
    if (position >= value.size() || value[position] != token)
      return false;
    ++index;
    ++position;
  }
  return position == value.size();
}

} // namespace

bool GlobMatch(std::string_view pattern, std::string_view value) {
  return MatchAt(pattern, 0, value, 0);
}

} // namespace linecode::application
