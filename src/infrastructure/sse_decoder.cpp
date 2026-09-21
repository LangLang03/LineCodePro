#include "infrastructure/sse_decoder.h"

#include <algorithm>
#include <charconv>
#include <limits>
#include <system_error>
#include <utility>

namespace linecode::infrastructure {
namespace {

constexpr std::string_view kDataField = "data";
constexpr std::string_view kEventField = "event";
constexpr std::string_view kIdField = "id";
constexpr std::string_view kRetryField = "retry";
constexpr std::string_view kUtf8Bom{"\xEF\xBB\xBF", 3U};

std::pair<std::string_view, std::string_view>
SplitField(std::string_view line) noexcept {
  const auto colon = line.find(':');
  if (colon == std::string_view::npos) {
    return {line, {}};
  }

  auto value = line.substr(colon + 1U);
  if (value.starts_with(' ')) {
    value.remove_prefix(1U);
  }
  return {line.substr(0U, colon), value};
}

std::optional<std::chrono::milliseconds>
ParseRetry(std::string_view value) noexcept {
  if (value.empty() ||
      !std::ranges::all_of(value,
                           [](const char character) noexcept {
                             return character >= '0' && character <= '9';
                           })) {
    return std::nullopt;
  }

  std::uint64_t milliseconds = 0;
  const auto [end, error] =
      std::from_chars(value.data(), value.data() + value.size(), milliseconds);
  constexpr auto kMaximum =
      static_cast<std::uint64_t>(std::numeric_limits<std::int64_t>::max());
  if (error != std::errc{} || end != value.data() + value.size() ||
      milliseconds > kMaximum) {
    return std::nullopt;
  }
  return std::chrono::milliseconds{static_cast<std::int64_t>(milliseconds)};
}

} // namespace

SseDecoder::SseDecoder(const SseDecoderLimits limits) : limits_(limits) {}

SseDecoder::Result SseDecoder::Feed(const std::span<const std::byte> bytes) {
  if (bytes.empty()) {
    return Feed(std::string_view{});
  }
  return Feed({reinterpret_cast<const char *>(bytes.data()), bytes.size()});
}

SseDecoder::Result SseDecoder::Feed(const std::string_view bytes) {
  if (terminal_error_.has_value()) {
    return std::unexpected(*terminal_error_);
  }
  if (finished_) {
    return std::unexpected(MakeError(SseDecodeErrorCode::input_after_finish,
                                     0U, bytes.size()));
  }

  std::vector<SseEvent> events;
  std::size_t start = 0U;
  if (!preamble_resolved_) {
    while (start < bytes.size() && bom_probe_.size() < kUtf8Bom.size()) {
      const auto next = bytes[start];
      if (next != kUtf8Bom[bom_probe_.size()]) {
        preamble_resolved_ = true;
        if (!bom_probe_.empty()) {
          const auto probe_result = ProcessBytes(bom_probe_, events);
          if (!probe_result.has_value()) {
            terminal_error_ = probe_result.error();
            return std::unexpected(*terminal_error_);
          }
          bom_probe_.clear();
        }
        break;
      }
      bom_probe_.push_back(next);
      ++start;
    }
    if (bom_probe_.size() == kUtf8Bom.size()) {
      byte_offset_ += kUtf8Bom.size();
      bom_probe_.clear();
      preamble_resolved_ = true;
    }
  }

  if (start < bytes.size()) {
    const auto processed = ProcessBytes(bytes.substr(start), events);
    if (!processed) {
      terminal_error_ = processed.error();
      return std::unexpected(*terminal_error_);
    }
  }
  return events;
}

SseDecoder::Result SseDecoder::Finish() {
  if (terminal_error_.has_value()) {
    return std::unexpected(*terminal_error_);
  }
  if (finished_) {
    return std::unexpected(
        MakeError(SseDecodeErrorCode::input_after_finish, 0U, 0U));
  }

  std::vector<SseEvent> events;
  if (!preamble_resolved_ && !bom_probe_.empty()) {
    preamble_resolved_ = true;
    const auto processed = ProcessBytes(bom_probe_, events);
    if (!processed.has_value()) {
      terminal_error_ = processed.error();
      return std::unexpected(*terminal_error_);
    }
    bom_probe_.clear();
  }
  if (!pending_line_.empty()) {
    const auto processed = ProcessLine(events, false);
    if (!processed.has_value()) {
      terminal_error_ = processed.error();
      return std::unexpected(*terminal_error_);
    }
  }
  Dispatch(events);
  finished_ = true;
  return events;
}

std::optional<std::string_view> SseDecoder::LastEventId() const noexcept {
  if (!last_event_id_.has_value()) {
    return std::nullopt;
  }
  return *last_event_id_;
}

std::optional<std::chrono::milliseconds>
SseDecoder::RetryDelay() const noexcept {
  return retry_delay_;
}

void SseDecoder::Reset() noexcept {
  bom_probe_.clear();
  pending_line_.clear();
  ClearFrame();
  last_event_id_.reset();
  retry_delay_.reset();
  byte_offset_ = 0U;
  preamble_resolved_ = false;
  skip_lf_after_cr_ = false;
  finished_ = false;
  terminal_error_.reset();
}

std::expected<void, SseDecodeError>
SseDecoder::ProcessBytes(const std::string_view bytes,
                         std::vector<SseEvent> &events) {
  std::size_t start = 0U;
  while (start < bytes.size()) {
    if (skip_lf_after_cr_) {
      skip_lf_after_cr_ = false;
      if (bytes[start] == '\n') {
        ++byte_offset_;
        ++start;
        continue;
      }
    }

    const auto delimiter = bytes.find_first_of("\r\n", start);
    const auto end = delimiter == std::string_view::npos ? bytes.size()
                                                         : delimiter;
    const auto appended = AppendPending(bytes.substr(start, end - start));
    if (!appended.has_value()) {
      return appended;
    }
    if (delimiter == std::string_view::npos) {
      break;
    }

    const auto character = bytes[delimiter];
    ++byte_offset_;
    const auto processed = ProcessLine(events, true);
    if (!processed.has_value()) {
      return processed;
    }
    skip_lf_after_cr_ = character == '\r';
    start = delimiter + 1U;
  }
  return {};
}

std::expected<void, SseDecodeError>
SseDecoder::AppendPending(const std::string_view bytes) {
  const auto available = limits_.max_buffer_bytes -
                         std::min(limits_.max_buffer_bytes,
                                  pending_line_.size());
  if (bytes.size() > available) {
    return std::unexpected(
        MakeError(SseDecodeErrorCode::buffer_limit_exceeded,
                  limits_.max_buffer_bytes,
                  pending_line_.size() + bytes.size()));
  }
  pending_line_.append(bytes);
  byte_offset_ += bytes.size();
  return {};
}

std::expected<void, SseDecodeError>
SseDecoder::ProcessLine(std::vector<SseEvent> &events,
                        const bool terminated) {
  const auto raw_line_bytes = pending_line_.size() + (terminated ? 1U : 0U);
  std::string_view line{pending_line_};

  if (line.empty()) {
    pending_line_.clear();
    Dispatch(events);
    return {};
  }

  const auto available = limits_.max_frame_bytes -
                         std::min(limits_.max_frame_bytes, frame_bytes_);
  if (raw_line_bytes > available) {
    return std::unexpected(MakeError(SseDecodeErrorCode::frame_limit_exceeded,
                                     limits_.max_frame_bytes,
                                     frame_bytes_ + raw_line_bytes));
  }
  frame_bytes_ += raw_line_bytes;
  ConsumeField(line);
  pending_line_.clear();
  return {};
}

void SseDecoder::Dispatch(std::vector<SseEvent> &events) {
  if (has_data_) {
    current_.id = last_event_id_;
    current_.retry = retry_delay_;
    events.push_back(std::move(current_));
  }
  ClearFrame();
}

void SseDecoder::ConsumeField(const std::string_view line) {
  if (line.starts_with(':')) {
    return;
  }

  const auto [field, value] = SplitField(line);
  if (field == kDataField) {
    if (has_data_) {
      current_.data.push_back('\n');
    }
    current_.data.append(value);
    has_data_ = true;
    return;
  }
  if (field == kEventField) {
    current_.event = std::string(value);
    return;
  }
  if (field == kIdField) {
    if (value.find('\0') == std::string_view::npos) {
      last_event_id_ = std::string(value);
    }
    return;
  }
  if (field == kRetryField) {
    if (const auto retry = ParseRetry(value); retry.has_value()) {
      retry_delay_ = *retry;
    }
  }
}

SseDecodeError SseDecoder::MakeError(const SseDecodeErrorCode code,
                                     const std::size_t limit,
                                     const std::size_t observed) const {
  return {.code = code,
          .limit = limit,
          .observed = observed,
          .byte_offset = byte_offset_};
}

void SseDecoder::ClearFrame() noexcept {
  current_ = {};
  frame_bytes_ = 0U;
  has_data_ = false;
}

} // namespace linecode::infrastructure
