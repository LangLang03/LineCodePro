#include "domain/input_attachment.h"

#include <utility>

namespace linecode::domain {

InputAttachment::InputAttachment(std::string name, std::string path,
                                 std::string source)
    : name_(name.empty() ? Basename(path) : std::move(name)),
      path_(std::move(path)), source_(NormalizeSource(source)) {}

bool InputAttachment::Matches(const std::string_view path,
                              const std::string_view source) const noexcept {
  return path_ == path && source_ == NormalizeSource(source);
}

std::string InputAttachment::Basename(const std::string_view path) {
  auto value = path;
  while (!value.empty() &&
         static_cast<unsigned char>(value.front()) <=
             static_cast<unsigned char>(' ')) {
    value.remove_prefix(1);
  }
  while (!value.empty() &&
         static_cast<unsigned char>(value.back()) <=
             static_cast<unsigned char>(' ')) {
    value.remove_suffix(1);
  }
  while (value.size() > 1 && value.back() == '/') {
    value.remove_suffix(1);
  }
  const auto separator = value.rfind('/');
  if (separator != std::string_view::npos && separator + 1 < value.size()) {
    value.remove_prefix(separator + 1);
  }
  return std::string{value};
}

} // namespace linecode::domain
