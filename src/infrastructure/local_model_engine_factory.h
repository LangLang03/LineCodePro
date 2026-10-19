#pragma once

#include <memory>

#include "application/ports/local_model_engine.h"

namespace linecode::infrastructure {

// The engine for this build.
//
// Returns the llama.cpp adapter when the build compiled it and the placeholder
// engine otherwise, so callers never need to know which one they hold and the
// `llama.h` dependency stays out of every translation unit that only needs the
// port.
[[nodiscard]] std::shared_ptr<application::LocalModelEngine>
CreateLocalModelEngine();

} // namespace linecode::infrastructure
