#pragma once

#include <memory>
#include <string>
#include <vector>

#include <huxerui/task.h>

#include "application/ports/completion_gateway.h"

namespace linecode::application {

// Mid-loop context compaction boundary.
//
// Legacy `GenerationFlowController.continueModelAfterTools()` checked the
// context budget after every tool batch and, when the window was 80% full,
// compacted the history before the next model request — keeping the in-flight
// assistant + tool group intact.
//
// The completion loop owns that loop but not the conversation, so it asks this
// port instead of reaching into the session. Implementations decide whether to
// compact and rewrite `request.messages` in place.
class MidLoopCompactor {
public:
  virtual ~MidLoopCompactor() = default;

  // Called after each tool batch, before the next model turn.
  //
  // Returns the possibly rewritten request. A request returned unchanged means
  // "nothing to do"; the loop then proceeds exactly as before.
  [[nodiscard]] virtual huxerui::Task<CompletionRequest>
  CompactIfNeeded(CompletionRequest request) = 0;
};

} // namespace linecode::application
