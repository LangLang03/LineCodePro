#include "infrastructure/unavailable_local_model_engine.h"

#include <utility>

#include "application/local_model_support.h"
#include "infrastructure/app_logger.h"

namespace linecode::infrastructure {

application::LocalModelRuntimeInfo
UnavailableLocalModelEngine::RuntimeInfo() const {
  return application::LocalModelRuntimeInfo{
      .available = false,
      .packaged = application::local_model_packaged,
      .engine = {},
      .revision = {},
      .backends = application::EnabledLocalModelBackendNames(),
      .devices = {},
  };
}

huxerui::Task<std::expected<application::LocalGenerationResult,
                            application::CompletionError>>
UnavailableLocalModelEngine::Generate(application::LocalGenerationRequest,
                                      std::function<void(std::string_view)>) {
  LogAppError("local_model_engine_unavailable");
  co_return std::unexpected(application::CompletionError{
      .code = application::CompletionErrorCode::unsupported_protocol,
      .message = application::local_model_enabled
                     ? "The local model backend did not initialize"
                     : "This build does not include the local model backend",
  });
}

} // namespace linecode::infrastructure
