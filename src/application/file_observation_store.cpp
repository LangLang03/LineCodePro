#include "application/file_observation_store.h"

#include <cstddef>
#include <map>
#include <mutex>
#include <optional>
#include <set>
#include <string>
#include <string_view>
#include <utility>

namespace linecode::application {

bool FileObservationStore::Claim(std::string_view absolute_path) {
  const std::lock_guard lock{mutex_};
  return claimed_.insert(std::string{absolute_path}).second;
}

void FileObservationStore::Release(std::string_view absolute_path) {
  const std::lock_guard lock{mutex_};
  // Erasing through a heterogeneous lookup is not portable across the supported
  // standard libraries, so the entry is found first and erased by iterator.
  const auto found = claimed_.find(absolute_path);
  if (found != claimed_.end())
    claimed_.erase(found);
}

void FileObservationStore::Record(std::string absolute_path,
                                  FileObservation observation) {
  const std::lock_guard lock{mutex_};
  const auto found = observed_.find(absolute_path);
  if (found == observed_.end() &&
      observed_.size() >= kMaximumObservedPaths) {
    // The record is a hint, never a permission: dropping it can only require
    // another read before the next mutation.
    observed_.clear();
  }
  observed_.insert_or_assign(std::move(absolute_path), std::move(observation));
}

void FileObservationStore::Forget(std::string_view absolute_path) {
  const std::lock_guard lock{mutex_};
  const auto found = observed_.find(absolute_path);
  if (found != observed_.end())
    observed_.erase(found);
}

std::optional<FileObservation>
FileObservationStore::Observed(std::string_view absolute_path) const {
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
