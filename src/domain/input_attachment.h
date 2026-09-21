#pragma once

#include <string>
#include <string_view>

namespace linecode::domain {

class InputAttachment final {
public:
  static constexpr std::string_view source_local = "local";
  static constexpr std::string_view source_ssh = "ssh";
  static constexpr std::string_view source_terminal_provider =
      "terminal_provider";

  InputAttachment() = default;
  InputAttachment(std::string name, std::string path, std::string source);

  [[nodiscard]] const std::string &Name() const noexcept { return name_; }
  [[nodiscard]] const std::string &Path() const noexcept { return path_; }
  [[nodiscard]] const std::string &Source() const noexcept { return source_; }

  [[nodiscard]] bool Matches(std::string_view path,
                             std::string_view source) const noexcept;

  bool operator==(const InputAttachment &) const = default;

private:
  [[nodiscard]] static constexpr std::string_view
  NormalizeSource(std::string_view source) noexcept {
    if (source == source_ssh) {
      return source_ssh;
    }
    if (source == source_terminal_provider) {
      return source_terminal_provider;
    }
    return source_local;
  }
  [[nodiscard]] static std::string Basename(std::string_view path);

  std::string name_;
  std::string path_;
  std::string source_{source_local};
};

} // namespace linecode::domain
