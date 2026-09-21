#pragma once

#include <chrono>
#include <cstddef>
#include <cstdint>
#include <expected>
#include <optional>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::infrastructure {

struct SseEvent final {
  std::string data;
  std::optional<std::string> event;
  std::optional<std::string> id;
  std::optional<std::chrono::milliseconds> retry;

  bool operator==(const SseEvent &) const = default;
};

enum class SseDecodeErrorCode : std::uint8_t {
  buffer_limit_exceeded,
  frame_limit_exceeded,
  input_after_finish,
};

struct SseDecodeError final {
  SseDecodeErrorCode code;
  std::size_t limit;
  std::size_t observed;
  std::size_t byte_offset;

  bool operator==(const SseDecodeError &) const = default;
};

struct SseDecoderLimits final {
  // Maximum bytes retained while waiting for a line terminator.
  std::size_t max_buffer_bytes{256U * 1024U};
  // Maximum bytes accepted between two empty lines.
  std::size_t max_frame_bytes{1024U * 1024U};
};

class SseDecoder final {
public:
  using Result = std::expected<std::vector<SseEvent>, SseDecodeError>;

  explicit SseDecoder(SseDecoderLimits limits = {});

  [[nodiscard]] Result Feed(std::span<const std::byte> bytes);
  [[nodiscard]] Result Feed(std::string_view bytes);

  // Flushes a final unterminated line and frame, then closes this stream.
  [[nodiscard]] Result Finish();

  [[nodiscard]] std::optional<std::string_view> LastEventId() const noexcept;
  [[nodiscard]] std::optional<std::chrono::milliseconds>
  RetryDelay() const noexcept;

  void Reset() noexcept;

private:
  [[nodiscard]] std::expected<void, SseDecodeError>
  ProcessBytes(std::string_view bytes, std::vector<SseEvent> &events);
  [[nodiscard]] std::expected<void, SseDecodeError>
  AppendPending(std::string_view bytes);
  [[nodiscard]] std::expected<void, SseDecodeError>
  ProcessLine(std::vector<SseEvent> &events, bool terminated);
  void Dispatch(std::vector<SseEvent> &events);
  void ConsumeField(std::string_view line);
  [[nodiscard]] SseDecodeError MakeError(SseDecodeErrorCode code,
                                         std::size_t limit,
                                         std::size_t observed) const;
  void ClearFrame() noexcept;

  SseDecoderLimits limits_;
  std::string bom_probe_;
  std::string pending_line_;
  SseEvent current_;
  std::optional<std::string> last_event_id_;
  std::optional<std::chrono::milliseconds> retry_delay_;
  std::size_t frame_bytes_{0};
  std::size_t byte_offset_{0};
  bool has_data_{false};
  bool preamble_resolved_{false};
  bool skip_lf_after_cr_{false};
  bool finished_{false};
  std::optional<SseDecodeError> terminal_error_;
};

} // namespace linecode::infrastructure
