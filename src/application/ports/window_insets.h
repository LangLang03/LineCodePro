#pragma once

namespace linecode::application {

struct WindowInsets {
  float top = 0.0F;
  float right = 0.0F;
  float bottom = 0.0F;
  float left = 0.0F;
};

class WindowInsetsProvider {
public:
  virtual ~WindowInsetsProvider() = default;

  [[nodiscard]] virtual WindowInsets Current() const noexcept = 0;
};

} // namespace linecode::application
