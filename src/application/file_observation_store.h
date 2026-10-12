#pragma once

#include <cstddef>
#include <map>
#include <mutex>
#include <optional>
#include <string>
#include <string_view>

namespace linecode::application {

// The in-memory record of which file revisions the built-in file tools have
// read, keyed by the resolved absolute path.
//
// Before a tool replaces or edits a file that already exists it consults this
// record:
//
//   * no entry          -> the file has not been read, so the model must read
//                          it before it may change it;
//   * entry, digest equal to the bytes on disk  -> the read is still current,
//                          the mutation may proceed;
//   * entry, digest unequal -> the file changed after it was read (another
//                          process, another Agent, or the user), so the model
//                          is told to read it again instead of silently
//                          overwriting content it never saw.
//
// Nothing is persisted. A fresh session or process starts empty, which only
// ever makes the rule stricter: an unread file can never be overwritten.
//
// The record is bounded, so a long-lived process cannot accumulate one entry
// per file it has ever read. Dropping entries only ever costs another read.
class FileObservationStore final {
public:
  // Number of paths kept before the record is dropped as a whole.
  static constexpr std::size_t kMaximumObservedPaths = 4096;

  // Records the digest observed for `absolute_path`, replacing any earlier one.
  void Record(std::string absolute_path, std::string content_hash);
  // Drops the entry, if any: the next mutation must be preceded by a read.
  void Forget(std::string_view absolute_path);
  // Digest recorded for `absolute_path`, or nullopt when it was never read.
  [[nodiscard]] std::optional<std::string>
  ObservedHash(std::string_view absolute_path) const;
  [[nodiscard]] std::size_t Size() const;
  void Clear();

private:
  mutable std::mutex mutex_;
  // std::less<> keeps the string_view lookups allocation-free.
  std::map<std::string, std::string, std::less<>> observed_;
};

} // namespace linecode::application
