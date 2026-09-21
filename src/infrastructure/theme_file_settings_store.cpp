#include "infrastructure/theme_file_settings_store.h"

#include <sstream>
#include <utility>

namespace linecode::infrastructure {

ThemeFileSettingsStore::ThemeFileSettingsStore(huxerui::File data_directory)
    : directory_(std::move(data_directory)),
      file_(directory_.Child("theme-settings.properties")) {}

std::optional<std::string>
ThemeFileSettingsStore::Read(std::string_view key) const {
  const std::scoped_lock lock(mutex_);
  LoadLocked();
  const auto found = values_.find(key);
  return found == values_.end() ? std::nullopt
                                : std::optional<std::string>{found->second};
}

void ThemeFileSettingsStore::Write(std::string_view key, std::string value) {
  const std::scoped_lock lock(mutex_);
  LoadLocked();
  values_.insert_or_assign(std::string(key), std::move(value));
  FlushLocked();
}

void ThemeFileSettingsStore::LoadLocked() const {
  if (loaded_)
    return;
  loaded_ = true;
  auto contents = file_.ReadString();
  if (!contents.Succeeded())
    return;
  std::istringstream lines(contents.Value());
  for (std::string line; std::getline(lines, line);) {
    const auto separator = line.find('=');
    if (separator == std::string::npos)
      continue;
    values_.insert_or_assign(line.substr(0, separator),
                             line.substr(separator + 1));
  }
}

void ThemeFileSettingsStore::FlushLocked() const {
  static_cast<void>(directory_.CreateDirectories());
  std::string contents;
  for (const auto &[key, value] : values_) {
    contents += key;
    contents.push_back('=');
    contents += value;
    contents.push_back('\n');
  }
  static_cast<void>(file_.WriteString(contents));
}

} // namespace linecode::infrastructure
