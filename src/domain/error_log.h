#pragma once

#include <cstdint>
#include <string>

namespace linecode::domain {

struct ErrorLogEntry final {
  // Opaque repository identity. Presentation must not interpret it as a path.
  std::string id;
  std::string title;
  std::string subtitle;
  std::int64_t timestamp_millis{};

  bool operator==(const ErrorLogEntry &) const = default;
};

} // namespace linecode::domain
