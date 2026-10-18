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
  /// Identity of the turn currently being answered. A new turn re-arms the
  /// follow even when the reader had scrolled away from the tail; content that
  /// arrives within the same turn leaves their position alone.
  std::uint64_t turn_id = 0;

  bool operator==(const ChatTailScroll &) const = default;

  static const huxerui::detail::ModifierDescriptor &Descriptor();
};

static_assert(huxerui::ViewModifier<ChatTailScroll>);

} // namespace linecode::presentation
