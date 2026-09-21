#pragma once

#include <algorithm>
#include <cstddef>
#include <expected>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::infrastructure {

struct BoundedTextError {
  std::size_t maximum_bytes{};
};

class BoundedTextAccumulator final {
public:
  explicit BoundedTextAccumulator(const std::size_t maximum_bytes)
      : maximum_bytes_(maximum_bytes) {}

  [[nodiscard]] std::expected<void, BoundedTextError>
  Append(const std::string_view chunk) {
    const auto remaining =
        maximum_bytes_ - std::min(maximum_bytes_, value_.size());
    if (chunk.size() > remaining) {
      return std::unexpected(
          BoundedTextError{.maximum_bytes = maximum_bytes_});
    }
    value_.append(chunk);
    return {};
  }

  [[nodiscard]] const std::string &Value() const noexcept { return value_; }

  [[nodiscard]] std::string Take() && noexcept { return std::move(value_); }

private:
  std::size_t maximum_bytes_{};
  std::string value_;
};

} // namespace linecode::infrastructure
