#pragma once

#include <cstdint>
#include <expected>
#include <optional>
#include <string>
#include <string_view>
#include <vector>

#include <huxerui/task.h>

#include "domain/diff_record.h"

namespace linecode::application {

enum class DiffStoreErrorCode : std::uint8_t {
  unavailable,
  read_failed,
  write_failed,
};

struct DiffStoreError final {
  DiffStoreErrorCode code{DiffStoreErrorCode::unavailable};
  std::string message;

  bool operator==(const DiffStoreError &) const = default;
};

template <class Value>
using DiffStoreResult = std::expected<Value, DiffStoreError>;

// Outcome of a revert request, mirroring `DiffRepository.RevertResult`.
//
// A successful result may still carry no record: "already reverted" is a
// success with nothing left to restore.
struct DiffRevertResult final {
  bool success{};
  std::string message;
  std::optional<domain::DiffRecord> record;
};

// Contract of the legacy `DiffStore`, the persistence boundary behind the
// write card's Accept / Revert actions.
//
// Every operation is asynchronous because the HuxerUI SQLite adapter only
// exposes queued asynchronous statements; a synchronous signature could not
// be implemented without blocking the owning task.
class DiffStore {
public:
  virtual ~DiffStore() = default;

  // Records a change and returns it with its new identifier.
  [[nodiscard]] virtual huxerui::Task<DiffStoreResult<domain::DiffRecord>>
  Record(std::string file_path, std::string old_content,
         std::string new_content, bool old_exists) = 0;

  [[nodiscard]] virtual huxerui::Task<
      DiffStoreResult<std::optional<domain::DiffRecord>>>
  Find(std::string diff_id) = 0;

  // Every record for one file, oldest first. The revert guard walks this chain
  // to refuse reverting past a change that is still in place.
  [[nodiscard]] virtual huxerui::Task<
      DiffStoreResult<std::vector<domain::DiffRecord>>>
  Chain(std::string file_path) = 0;

  // Checks whether `diff_id` may be reverted right now. Returns failure when
  // the record is missing or a later change to the same file is still applied.
  [[nodiscard]] virtual huxerui::Task<DiffStoreResult<DiffRevertResult>>
  CheckRevert(std::string diff_id) = 0;

  [[nodiscard]] virtual huxerui::Task<DiffStoreResult<void>>
  MarkReverted(std::string diff_id) = 0;

  [[nodiscard]] virtual huxerui::Task<DiffStoreResult<void>>
  SetReview(std::string diff_id, std::string state, std::string message) = 0;
};

} // namespace linecode::application
