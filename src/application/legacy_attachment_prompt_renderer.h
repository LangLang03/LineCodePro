#pragma once

#include <string>

#include "application/ports/attachment_prompt_renderer.h"

namespace linecode::application {

struct AttachmentPromptText final {
  std::string files_header{"## 附加文件位置"};
  std::string files_description{
      "这些路径来自用户在输入框左侧选择的文件；除非用户明确要求，不要在回复中原样复述。"};
  std::string user_message_label{"用户消息 "};
  std::string attached_files_label{"已附加文件"};

  bool operator==(const AttachmentPromptText &) const = default;
};

class LegacyAttachmentPromptRenderer final : public AttachmentPromptRenderer {
public:
  explicit LegacyAttachmentPromptRenderer(AttachmentPromptText text = {});

  [[nodiscard]] std::string
  Render(std::span<const domain::ChatMessage> history) const override;

private:
  [[nodiscard]] std::string RecallText(
      const domain::ChatMessage &message) const;

  AttachmentPromptText text_;
};

} // namespace linecode::application
