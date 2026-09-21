#pragma once

#include <cstddef>
#include <span>
#include <string>
#include <string_view>
#include <vector>

namespace linecode::application {

struct ShareFileRequest final {
  std::string file_name;
  std::string mime_type;
  std::vector<std::byte> content;

  bool operator==(const ShareFileRequest &) const = default;
};

struct TranscriptBlock final {
  std::string speaker;
  std::string content;
  bool user{};

  bool operator==(const TranscriptBlock &) const = default;
};

struct RenderedTranscriptRequest final {
  std::string renderer_id;
  std::string file_name;
  std::string mime_type;
  std::vector<TranscriptBlock> blocks;

  bool operator==(const RenderedTranscriptRequest &) const = default;
};

// Platform delivery boundary shared by every registered chat export format.
// Formatting and format selection stay in C++; the host only performs native
// clipboard/share operations and the two platform text-rendering capabilities.
class ChatExportDelivery {
public:
  virtual ~ChatExportDelivery() = default;

  [[nodiscard]] virtual bool CopyText(std::string_view text) = 0;
  [[nodiscard]] virtual bool ShareText(std::string_view text) = 0;
  [[nodiscard]] virtual bool ShareFile(const ShareFileRequest &file) = 0;
  [[nodiscard]] virtual bool
  RenderAndShare(const RenderedTranscriptRequest &request) = 0;
};

} // namespace linecode::application
