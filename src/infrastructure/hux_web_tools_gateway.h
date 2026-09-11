#pragma once

#include <memory>

#include <huxerui/http.h>

#include "application/ports/web_tools.h"

namespace linecode::infrastructure {

// HuxerUI HTTP adapter for the web tool group. Wire policy (endpoints, API key
// placement, response normalization, page extraction) lives in
// web_tools_codec.h; this class only performs transport, status handling and
// size limits.
class HuxWebToolsGateway final : public application::WebToolsGateway {
public:
  explicit HuxWebToolsGateway(std::shared_ptr<huxerui::HttpClient> http);

  [[nodiscard]] huxerui::Task<application::WebToolResult<
      std::vector<application::WebSearchResultItem>>>
  Search(application::WebSearchRequest request) override;

  [[nodiscard]] huxerui::Task<
      application::WebToolResult<std::string>>
  FetchPage(application::WebFetchRequest request) override;

private:
  std::shared_ptr<huxerui::HttpClient> http_;
};

} // namespace linecode::infrastructure
