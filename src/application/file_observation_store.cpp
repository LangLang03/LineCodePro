#include "application/file_observation_store.h"

#include <cstddef>
#include <map>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::application {

void FileObservationStore::Record(std::string absolute_path,
                                  std::string content_hash) {
  const std::lock_guard lock{mutex_};
  const auto found = observed_.find(absolute_path);
  if (found == observed_.end() &&
      observed_.size() >= kMaximumObservedPaths) {
    // The record is a hint, never a permission: dropping it can only require
    // another read before the next mutation.
    observed_.clear();
  }
  observed_.insert_or_assign(std::move(absolute_path), std::move(content_hash));
}

void FileObservationStore::Forget(std::string_view absolute_path) {
  const std::lock_guard lock{mutex_};
  // Erasing through a heterogeneous lookup is not portable across the supported
  // standard libraries, so the entry is found first and erased by iterator.
  const auto found = observed_.find(absolute_path);
  if (found != observed_.end())
    observed_.erase(found);
}

std::optional<std::string>
FileObservationStore::ObservedHash(std::string_view absolute_path) const {
  const std::lock_guard lock{mutex_};
  const auto found = observed_.find(absolute_path);
  if (found == observed_.end())
    return std::nullopt;
  return found->second;
}

std::size_t FileObservationStore::Size() const {
  const std::lock_guard lock{mutex_};
  return observed_.size();
}

void FileObservationStore::Clear() {
  const std::lock_guard lock{mutex_};
  observed_.clear();
}

} // namespace linecode::application
