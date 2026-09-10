#pragma once

#include <string>
#include <string_view>

#include "domain/tutorial_document.h"

namespace linecode::infrastructure {

class TutorialMarkdownParser final {
public:
  [[nodiscard]] domain::TutorialDocument Parse(std::string_view markdown) const;

  [[nodiscard]] static std::string PlainText(
      const domain::TutorialInlineLine& line);
  [[nodiscard]] static std::string ShortSectionTitle(std::string_view title);
};

} // namespace linecode::infrastructure
