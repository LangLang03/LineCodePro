#pragma once

#include <string_view>

namespace linecode::application {

class ShareTextService {
public:
  virtual ~ShareTextService() = default;

  // Returns whether the request was accepted by a native share surface.
  [[nodiscard]] virtual bool Share(std::string_view text) = 0;
};

} // namespace linecode::application
