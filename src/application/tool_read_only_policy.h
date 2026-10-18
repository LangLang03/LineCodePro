#pragma once

#include <string_view>

namespace linecode::application {

// Read-only mode keeps the shell tool available, because on an SSH or terminal
// provider workspace it is the only way to read the project. That exception must
// not become a write path, so every command has to pass this predicate before it
// runs in read-only mode.
//
// The rule is deliberately conservative: a command is accepted only when it
// cannot change the target. Anything unrecognised is refused, and the caller
// tells the model why so it can rephrase instead of retrying blindly.
[[nodiscard]] bool IsReadOnlyShellCommand(std::string_view command) noexcept;

} // namespace linecode::application
