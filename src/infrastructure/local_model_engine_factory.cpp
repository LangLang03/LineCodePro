#include "infrastructure/local_model_engine_factory.h"

#include "application/local_model_support.h"
#include "infrastructure/unavailable_local_model_engine.h"

#if LINCODE_LOCAL_MODEL_ENABLED
#include "infrastructure/llama_local_model_engine.h"
#endif

namespace linecode::infrastructure {

std::shared_ptr<application::LocalModelEngine> CreateLocalModelEngine() {
#if LINCODE_LOCAL_MODEL_ENABLED
  return std::make_shared<LlamaLocalModelEngine>();
#else
  return std::make_shared<UnavailableLocalModelEngine>();
#endif
}

} // namespace linecode::infrastructure
