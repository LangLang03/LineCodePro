#pragma once

#include <string_view>

namespace linecode::application {

class ExternalLinkService {
public:
  virtual ~ExternalLinkService() = default;

  // Requests the host to open an absolute HTTP(S) URL in its default external
  // handler. Presentation code does not depend on platform launch APIs.
  virtual void Open(std::string_view url) = 0;
};

} // namespace linecode::application
