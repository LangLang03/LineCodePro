#pragma once

#include <optional>
#include <string_view>

#include <huxerui/data.h>

namespace linecode::presentation {

// Markdown may describe arbitrary URI schemes, but the app's browser and
// external-link ports intentionally expose only absolute HTTP(S) navigation.
[[nodiscard]] std::optional<huxerui::Uri>
ParseNavigableMarkdownLink(std::string_view value);

[[nodiscard]] bool IsHttpsMarkdownLink(const huxerui::Uri& value) noexcept;

} // namespace linecode::presentation
