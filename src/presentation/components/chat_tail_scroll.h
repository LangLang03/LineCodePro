#pragma once

#include <cstddef>
#include <cstdint>
#include <string>

#include <huxerui/huxerui.h>

namespace linecode::presentation {

struct ChatTailScroll final {
  huxerui::ScrollController controller;
  std::string conversation_id;
  std::size_t content_revision = 0;
  std::uint64_t smooth_request = 0;

  bool operator==(const ChatTailScroll &) const = default;

  static const huxerui::detail::ModifierDescriptor &Descriptor();
};

static_assert(huxerui::ViewModifier<ChatTailScroll>);

} // namespace linecode::presentation
