#pragma once

#include <string>

namespace linecode::domain {

struct ImageGenerationRequest final {
  std::string prompt;
  std::string size{"1024x1024"};
  std::string quality;
  std::string background;

  bool operator==(const ImageGenerationRequest &) const = default;
};

struct GeneratedImage final {
  std::string mime_type{"image/png"};
  std::string data_url;
  std::string revised_prompt;

  bool operator==(const GeneratedImage &) const = default;
};

} // namespace linecode::domain
