#pragma once

#include <memory>
#include <string>

#include <huxerui/resource.h>
#include <huxerui/view.h>

namespace linecode::application {
class MemoryStore;
}

namespace linecode::presentation {

struct MemoryScreenPresentation final {
  huxerui::StringVariant title{"Memory"};
  huxerui::StringVariant long_term{"Long-term memory"};
  huxerui::StringVariant project{"Project memory"};
  huxerui::StringVariant environment{"Environment memory"};
  huxerui::StringVariant short_term{"Short-term memory"};
  huxerui::StringVariant chat_index{"Chat index"};
  huxerui::StringVariant delete_title{"Delete memory"};
  huxerui::StringVariant delete_prompt{"Delete this memory?"};
  huxerui::StringVariant batch_delete_prefix{"Delete the selected "};
  huxerui::StringVariant batch_delete_suffix{" memory item(s)?"};
  huxerui::StringVariant editor_add{"Add memory"};
  huxerui::StringVariant editor_edit{"Edit memory"};
  huxerui::StringVariant scope_user{"user"};
  huxerui::StringVariant scope_project{"project"};
  huxerui::StringVariant scope_environment{"environment"};
  huxerui::StringVariant input_hint{"Memory content to save"};
  huxerui::StringVariant empty_toast{"Memory content cannot be empty"};
  huxerui::StringVariant action_title{"Memory action"};
  huxerui::StringVariant action_edit{"Edit"};
  huxerui::StringVariant action_delete{"Delete"};
  huxerui::StringVariant action_multi_select{"Multi-select"};
  huxerui::StringVariant selected_prefix{"Selected: "};
  huxerui::StringVariant selected_suffix{" item(s)"};
  huxerui::StringVariant current_project{"Current project: "};
  huxerui::StringVariant project_unselected{"No project selected"};
  huxerui::StringVariant empty{"No content"};
  huxerui::StringVariant source{"Source: "};
  huxerui::StringVariant used_prefix{"Used "};
  huxerui::StringVariant used_suffix{"times"};
  huxerui::StringVariant scope{"Scope: "};
  huxerui::StringVariant project_field{"Project: "};
  huxerui::StringVariant confidence{"Confidence: "};
  huxerui::StringVariant use_count{"Use count: "};
  huxerui::StringVariant created{"Created: "};
  huxerui::StringVariant updated{"Updated: "};
  huxerui::StringVariant last_used{"Last used: "};
  huxerui::StringVariant expires{"Expires: "};
  huxerui::StringVariant title_field{"Title: "};
  huxerui::StringVariant conversation{"Conversation: "};
  huxerui::StringVariant message{"Message: "};
  huxerui::StringVariant global{"Global"};
  huxerui::StringVariant not_used{"Not used yet"};
  huxerui::StringVariant no_expiry{"No expiry"};
  huxerui::StringVariant empty_value{"Empty"};
  huxerui::StringVariant unknown_time{"Unknown time"};
  huxerui::StringVariant loading{"Loading..."};
  huxerui::StringVariant retry{"Retry"};
  huxerui::StringVariant save{"Save"};
  huxerui::StringVariant cancel{"Cancel"};
  huxerui::StringVariant close{"Close"};
};

[[huxerui::composable]] huxerui::View MemoryScreen(
    std::shared_ptr<application::MemoryStore> store,
    std::string project_id,
    MemoryScreenPresentation presentation = {},
    std::string project_display = {});

} // namespace linecode::presentation
