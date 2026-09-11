#pragma once

#include <cstdint>
#include <string>
#include <string_view>

#include "domain/app_state.h"

namespace linecode::domain {

// Port of `ChatMessage.COMPACT_STATUS_*` (core-model
// `ChatMessage.java:10-12`). The three values are copied verbatim: they are
// written into persisted transcripts by the legacy application, so a different
// spelling would break conversation compatibility.
inline constexpr std::string_view compact_status_running = "running";
inline constexpr std::string_view compact_status_done = "done";
inline constexpr std::string_view compact_status_error = "error";

// Port of `ChatMessage.normalizeCompactStatus`: anything that is not one of the
// three statuses collapses to the empty string ("no compact block").
[[nodiscard]] std::string NormalizeCompactStatus(std::string_view value);

// Port of `ChatMessage.isCompactBlock()` (`ChatMessage.java:286-288`).
[[nodiscard]] bool IsCompactBlock(const ChatMessage &message) noexcept;

// Port of `ChatMessage.compactProgress(id, status)` (`ChatMessage.java:398-402`):
// an assistant-role, content-less block that is flagged streaming while running
// and excluded from the model context.
[[nodiscard]] ChatMessage CompactProgressMessage(std::uint64_t id,
                                                 std::string_view status);

// Port of `ChatMessage.withCompactStatus(nextStatus, nextStreaming)`
// (`ChatMessage.java:367-377`): the status and the streaming flag change, every
// other field (including `exclude_from_context`) is carried over.
[[nodiscard]] ChatMessage WithCompactStatus(const ChatMessage &message,
                                            std::string_view status,
                                            bool streaming);

// Legacy failure copies, taken verbatim from `ContextCompactionController`
// so the UI text is byte-identical.
inline constexpr std::string_view compact_failure_no_summary =
    "上下文压缩失败：模型没有返回摘要。";
inline constexpr std::string_view compact_failure_prefix = "上下文压缩失败：";
inline constexpr std::string_view compact_failure_out_of_memory =
    "上下文过大，压缩时内存不足，请手动清理早期对话后重试";

// Port of the catch branch at `ContextCompactionController.java:342`:
// "上下文压缩失败：" + exception message.
[[nodiscard]] std::string CompactFailureMessage(std::string_view detail);

} // namespace linecode::domain
