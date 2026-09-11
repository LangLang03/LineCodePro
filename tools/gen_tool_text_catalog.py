#!/usr/bin/env python3
"""Generate the application-owned tool text catalog from the packaged strings.

The built-in tools run outside composition, where ``huxerui::UseString``
is unavailable, so the application layer owns a plain C++ table of the migrated
``tool_*`` string resources.  This script is the single source of that table:
it parses ``resources/strings/default.properties`` and
``resources/strings/zh.properties`` exactly like the HuxerUI resource compiler
(outer quotes stripped, ``\\n``/``\\t``/``\\r``/``\\\\``/``\\"`` unescaped,
inner quotes and ``{{``/``}}`` kept verbatim) and rewrites
``src/application/tool_text_catalog.cpp``.

The hand-written ``src/application/tool_text_catalog.h`` owns the
``ToolTextKey`` enumeration; the script verifies that enumeration against the
properties keys - same members, same order - before writing anything, so the
header and the generated table can never drift apart.

Usage:
    python3 tools/gen_tool_text_catalog.py            # regenerate
    python3 tools/gen_tool_text_catalog.py --check    # verify only
"""

from __future__ import annotations

import argparse
import re
import sys
from pathlib import Path

REPOSITORY_ROOT = Path(__file__).resolve().parent.parent
DEFAULT_PROPERTIES = REPOSITORY_ROOT / "resources" / "strings" / "default.properties"
CHINESE_PROPERTIES = REPOSITORY_ROOT / "resources" / "strings" / "zh.properties"
CATALOG_HEADER = REPOSITORY_ROOT / "src" / "application" / "tool_text_catalog.h"
CATALOG_SOURCE = REPOSITORY_ROOT / "src" / "application" / "tool_text_catalog.cpp"

# Prefixes of the migrated built-in tool strings. `tool_agent_` also covers the
# `tool_agent_output_*` resources of the agent_output tool.
KEY_PREFIXES = (
    "tool_file_",
    "tool_glob_",
    "tool_list_dir_",
    "tool_call_action_",
    "tool_agent_",
    "tool_pipeline_",
)

_PROPERTY_LINE = re.compile(r"^([A-Za-z0-9_.\-]+)\s*=\s*(.*)$")
_ENUM_BODY = re.compile(
    r"enum\s+class\s+ToolTextKey\s*:[^{]*\{(?P<body>.*?)\}", re.DOTALL
)

_UNESCAPE = {
    "n": "\n",
    "t": "\t",
    "r": "\r",
    "f": "\f",
    "b": "\b",
    "\\": "\\",
    '"': '"',
    "'": "'",
}


def unescape(value: str) -> str:
    """Apply the resource compiler's escape rules to one raw properties value."""
    value = value.strip()
    if len(value) >= 2 and value[0] == '"' and value[-1] == '"':
        value = value[1:-1]
    out: list[str] = []
    index = 0
    while index < len(value):
        character = value[index]
        if character == "\\" and index + 1 < len(value):
            following = value[index + 1]
            if following == "u" and index + 5 < len(value):
                out.append(chr(int(value[index + 2 : index + 6], 16)))
                index += 6
                continue
            out.append(_UNESCAPE.get(following, following))
            index += 2
            continue
        out.append(character)
        index += 1
    return "".join(out)


def parse_properties(path: Path) -> dict[str, str]:
    """Parse a ``key = "value"`` properties file, preserving file order."""
    entries: dict[str, str] = {}
    for raw in path.read_text(encoding="utf-8").splitlines():
        line = raw.strip()
        if not line or line[0] in "#!":
            continue
        match = _PROPERTY_LINE.match(line)
        if match is None:
            continue
        entries[match.group(1)] = unescape(match.group(2))
    return entries


def load_table() -> list[tuple[str, str, str]]:
    """Return ``(key, english, chinese)`` rows in properties order."""
    english = parse_properties(DEFAULT_PROPERTIES)
    chinese = parse_properties(CHINESE_PROPERTIES)
    keys = [key for key in english if key.startswith(KEY_PREFIXES)]
    missing = [key for key in keys if key not in chinese]
    if missing:
        raise SystemExit(f"{CHINESE_PROPERTIES} is missing keys: {missing}")
    extra = [key for key in chinese if key.startswith(KEY_PREFIXES) and key not in english]
    if extra:
        raise SystemExit(f"{DEFAULT_PROPERTIES} is missing keys: {extra}")
    return [(key, english[key], chinese[key]) for key in keys]


def header_keys() -> list[str]:
    """Return the enumerator names of ``ToolTextKey`` in declaration order."""
    text = CATALOG_HEADER.read_text(encoding="utf-8")
    match = _ENUM_BODY.search(text)
    if match is None:
        raise SystemExit(f"{CATALOG_HEADER} does not declare enum class ToolTextKey")
    names = []
    for entry in match.group("body").split(","):
        entry = entry.strip()
        if not entry or entry.startswith("//"):
            continue
        names.append(entry.split("=")[0].strip())
    return names


def check_header(rows: list[tuple[str, str, str]]) -> None:
    expected = [key for key, _, _ in rows]
    declared = header_keys()
    if declared == expected:
        return
    only_header = [key for key in declared if key not in expected]
    only_properties = [key for key in expected if key not in declared]
    details = []
    if only_header:
        details.append(f"only in the header: {only_header}")
    if only_properties:
        details.append(f"only in the properties: {only_properties}")
    if not details:
        details.append("same members but a different order")
    raise SystemExit(
        "ToolTextKey does not match the packaged tool strings (" + "; ".join(details) + ")"
    )


def cpp_literal(value: str) -> str:
    out = []
    for character in value:
        if character == "\\":
            out.append("\\\\")
        elif character == '"':
            out.append('\\"')
        elif character == "\n":
            out.append("\\n")
        elif character == "\t":
            out.append("\\t")
        elif character == "\r":
            out.append("\\r")
        elif ord(character) < 0x20:
            out.append(f"\\x{ord(character):02x}")
        else:
            out.append(character)
    return '"' + "".join(out) + '"'


def render_source(rows: list[tuple[str, str, str]]) -> str:
    table = "\n".join(
        "    {\n"
        f"        .key = ToolTextKey::{key},\n"
        f"        .name = {cpp_literal(key)},\n"
        f"        .english = {cpp_literal(english)},\n"
        f"        .chinese = {cpp_literal(chinese)},\n"
        "    },"
        for key, english, chinese in rows
    )
    return f'''// Generated by tools/gen_tool_text_catalog.py - do not edit by hand.
//
// Verbatim copies of the tool_file_* / tool_glob_* / tool_list_dir_* /
// tool_call_action_* / tool_agent_* / tool_pipeline_* entries of
// resources/strings/default.properties (english)
// and resources/strings/zh.properties (chinese). Re-run
// `python3 tools/gen_tool_text_catalog.py` after changing those files.

#include "application/tool_text_catalog.h"

#include <algorithm>
#include <array>
#include <cctype>
#include <cstddef>
#include <cstdint>
#include <span>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::application {{
namespace {{

// One localized template. `name` is the packaged resource key the text was
// migrated from, kept so the table can be diffed against the properties files.
struct ToolTextEntry final {{
  ToolTextKey key;
  std::string_view name;
  std::string_view english;
  std::string_view chinese;
}};

constexpr std::array<ToolTextEntry, kToolTextKeyCount> kToolTextEntries{{{{
{table}
}}}};

static_assert(kToolTextEntries.size() == kToolTextKeyCount,
              "kToolTextEntries must cover every ToolTextKey");

// The table mirrors the ToolTextKey enumeration, so the ordinal lookup below is
// a direct index and a reordering mistake fails the build instead of silently
// crossing two strings.
consteval bool ToolTextTableIsOrdered() {{
  for (std::size_t index = 0; index < kToolTextEntries.size(); ++index) {{
    if (std::to_underlying(kToolTextEntries[index].key) != index)
      return false;
  }}
  return true;
}}

static_assert(ToolTextTableIsOrdered(),
              "ToolTextKey declaration order must match kToolTextEntries");

const ToolTextEntry *FindEntry(ToolTextKey key) noexcept {{
  return &kToolTextEntries[static_cast<std::size_t>(std::to_underlying(key))];
}}

const ToolTextEntry *FindEntry(std::string_view name) noexcept {{
  const auto found =
      std::ranges::find(kToolTextEntries, name, &ToolTextEntry::name);
  return found == kToolTextEntries.end() ? nullptr : &*found;
}}

std::string_view Select(const ToolTextEntry &entry,
                        ToolTextLanguage language) noexcept {{
  return language == ToolTextLanguage::chinese ? entry.chinese : entry.english;
}}

// The legacy positional template syntax: `{{0}}`-style placeholders are
// zero-based, `{{{{` and `}}}}` are literal braces, and any other brace is
// copied through unchanged. A placeholder without a matching argument is kept
// verbatim so a mismatched call is visible instead of silently empty.
std::string FormatTemplate(std::string_view template_text,
                           std::span<const std::string> arguments) {{
  constexpr std::size_t kMaximumPlaceholderDigits = 4;
  std::string formatted;
  formatted.reserve(template_text.size());
  for (std::size_t index = 0; index < template_text.size(); ++index) {{
    const char character = template_text[index];
    if (character == '{{') {{
      if (index + 1U < template_text.size() && template_text[index + 1U] == '{{') {{
        formatted += '{{';
        ++index;
        continue;
      }}
      std::size_t cursor = index + 1U;
      std::size_t placeholder = 0;
      while (cursor < template_text.size() &&
             cursor - index <= kMaximumPlaceholderDigits &&
             std::isdigit(static_cast<unsigned char>(template_text[cursor])) != 0) {{
        placeholder = placeholder * 10U +
                      static_cast<std::size_t>(template_text[cursor] - '0');
        ++cursor;
      }}
      if (cursor > index + 1U && cursor < template_text.size() &&
          template_text[cursor] == '}}') {{
        if (placeholder < arguments.size())
          formatted += arguments[placeholder];
        else
          formatted.append(template_text.substr(index, cursor - index + 1U));
        index = cursor;
        continue;
      }}
      formatted += character;
      continue;
    }}
    if (character == '}}' && index + 1U < template_text.size() &&
        template_text[index + 1U] == '}}') {{
      formatted += '}}';
      ++index;
      continue;
    }}
    formatted += character;
  }}
  return formatted;
}}

}} // namespace

std::string_view ToolTextName(ToolTextKey key) noexcept {{
  return FindEntry(key)->name;
}}

bool IsToolTextKey(std::string_view name) noexcept {{
  return FindEntry(name) != nullptr;
}}

std::string_view ToolTextTemplate(std::string_view name,
                                  ToolTextLanguage language) noexcept {{
  const ToolTextEntry *entry = FindEntry(name);
  return entry == nullptr ? std::string_view{{}} : Select(*entry, language);
}}

std::string_view ToolTextTemplate(ToolTextKey key,
                                  ToolTextLanguage language) noexcept {{
  return Select(*FindEntry(key), language);
}}

std::string FormatToolText(std::string_view template_text,
                           std::span<const std::string> arguments) {{
  return FormatTemplate(template_text, arguments);
}}

std::string ToolText(std::string_view name,
                     std::span<const std::string> arguments,
                     ToolTextLanguage language) {{
  const ToolTextEntry *entry = FindEntry(name);
  // An unknown key echoes the key so a typo is visible at the call site.
  return entry == nullptr ? std::string{{name}}
                          : FormatTemplate(Select(*entry, language), arguments);
}}

std::string ToolText(ToolTextKey key, std::span<const std::string> arguments,
                     ToolTextLanguage language) {{
  return FormatTemplate(Select(*FindEntry(key), language), arguments);
}}

}} // namespace linecode::application
'''


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument(
        "--check",
        action="store_true",
        help="verify the generated source without rewriting it",
    )
    arguments = parser.parse_args()

    rows = load_table()
    check_header(rows)
    source = render_source(rows)

    if arguments.check:
        current = CATALOG_SOURCE.read_text(encoding="utf-8") if CATALOG_SOURCE.is_file() else ""
        if current != source:
            print(f"{CATALOG_SOURCE} is stale; re-run without --check", file=sys.stderr)
            return 1
        print(f"{CATALOG_SOURCE} matches the packaged strings ({len(rows)} keys)")
        return 0

    CATALOG_SOURCE.write_text(source, encoding="utf-8")
    print(f"wrote {CATALOG_SOURCE} ({len(rows)} keys)")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
