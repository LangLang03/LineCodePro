#pragma once

#include <memory>

#include <huxerui/view.h>

namespace linecode::application {
class StorageStatsRepository;
}

namespace linecode::presentation {

[[nodiscard]] huxerui::View
StorageScreen(std::shared_ptr<application::StorageStatsRepository> repository);

} // namespace linecode::presentation
