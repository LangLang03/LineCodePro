#pragma once

#include <algorithm>
#include <array>
#include <cstddef>
#include <utility>

#include <app_resources.h>
#include <huxerui/resource.h>

#include "application/tool_settings_service.h"
#include "domain/tool_settings.h"

namespace linecode::presentation {

struct ImageModelPresentation final {
  domain::ImageModelPurpose purpose;
  huxerui::StringResource settings_title;
  huxerui::StringResource settings_description;
  huxerui::StringResource picker_title;
  huxerui::ImageResource action_icon;
  float description_minimum_height;
  std::size_t label_slot;
};

inline const std::array image_model_presentations{
    ImageModelPresentation{
        domain::ImageModelPurpose::understanding,
        app::strings::screen_tools_image_understanding_label,
        app::strings::screen_tools_image_understanding_desc,
        app::strings::screen_models_pick_image_understanding,
        app::images::paintbrush,
        52.0F,
        0,
    },
    ImageModelPresentation{
        domain::ImageModelPurpose::generation,
        app::strings::screen_tools_image_generation_label,
        app::strings::screen_tools_image_generation_desc,
        app::strings::screen_models_pick_image_generation,
        app::images::sparkles,
        34.0F,
        1,
    },
};

static_assert(image_model_presentations.size() ==
              application::image_model_setting_catalog.size());

[[nodiscard]] inline const ImageModelPresentation &
ImageModelPresentationFor(domain::ImageModelPurpose purpose) noexcept {
  const auto found = std::ranges::find(image_model_presentations, purpose,
                                       &ImageModelPresentation::purpose);
  if (found != image_model_presentations.end())
    return *found;
  std::unreachable();
}

} // namespace linecode::presentation
