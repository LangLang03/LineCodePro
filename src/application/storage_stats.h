#pragma once

#include <cstdint>
#include <string>

namespace linecode::application {

[[nodiscard]] std::string FormatStorageSize(std::uint64_t bytes);

} // namespace linecode::application
