#pragma once

#include <cstddef>
#include <map>
#include <mutex>
#include <optional>
#include <set>
#include <string>
#include <string_view>

namespace linecode::application {

// What the tools know about one file revision, keyed by its resolved absolute
// path: the digest of the text that was read or written, plus whether the model
// received that revision in full.
struct FileObservation final {
  // SHA-256 of the exact text the tool read or wrote for this revision. The
  // digest never comes from a separate look at the path, so it always describes
  // the bytes the model was given rather than a revision that appeared later.
  std::string content_hash;
  // True only when the model received every byte of that revision: a whole-file
  // read whose output was not truncated, or content the tool itself just wrote.
  // A partial observation still authorizes a localized edit, because the edit
  // carries over every byte it does not replace, but never a whole-file
  // replacement that would drop the content the model never saw.
  bool complete{};

  bool operator==(const FileObservation &) const = default;
};

// The in-memory record of which file revisions the built-in file tools have
// read, keyed by the resolved absolute path.
//
// Before a tool replaces or edits a file that already exists it consults this
// record:
//
//   * no entry          -> the file has not been read, so the model must read
//                          it before it may change it;
//   * entry, digest equal to the text on disk -> the read is still current, so
//                          the mutation may proceed;
//   * entry, digest unequal -> the file changed after it was read (another
//                          process, another Agent, or the user), so the model
//                          is told to read it again instead of silently
//                          overwriting content it never saw.
//
// The same record also hands out one in-process claim per path, which is what
// keeps two tool calls from both passing that check and then both writing: the
// second one is refused instead of dropping the first one's change.
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

  // Takes the in-process mutation claim for one path. False means another tool
  // call is already changing that file, and the caller must not mutate it
  // behind that call's back. Claims are held by MutationClaim, never recorded.
  [[nodiscard]] bool Claim(std::string_view absolute_path);
  void Release(std::string_view absolute_path);

  // Records the revision observed for `absolute_path`, replacing any earlier
  // one.
  void Record(std::string absolute_path, FileObservation observation);
  // Drops the entry, if any: the next mutation must be preceded by a read.
  void Forget(std::string_view absolute_path);
  // Revision recorded for `absolute_path`, or nullopt when it was never read.
  [[nodiscard]] std::optional<FileObservation>
  Observed(std::string_view absolute_path) const;
  [[nodiscard]] std::size_t Size() const;
  void Clear();

private:
  mutable std::mutex mutex_;
  // std::less<> keeps the string_view lookups allocation-free.
  std::map<std::string, FileObservation, std::less<>> observed_;
  std::set<std::string, std::less<>> claimed_;
};

} // namespace linecode::application
