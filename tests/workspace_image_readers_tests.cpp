#include "gtest_support.h"
#include <chrono>
#include <cstddef>
#include <filesystem>
#include <fstream>
#include <memory>
#include <string>
#include <utility>
#include <vector>

#include <huxerui/huxerui.h>
#include <huxerui/testing/ui_test.h>

#include "application/ports/project_workspace_controller.h"
#include "infrastructure/workspace_image_readers.h"

namespace {

namespace fs = std::filesystem;
using namespace linecode;

class TemporaryDirectory final {
public:
  TemporaryDirectory() {
    const auto suffix = std::chrono::steady_clock::now()
                            .time_since_epoch()
                            .count();
    path_ = fs::temp_directory_path() /
            ("linecode-workspace-image-" + std::to_string(suffix));
    fs::create_directories(path_ / "workspace" / "assets");
  }

  ~TemporaryDirectory() {
    std::error_code error;
    fs::remove_all(path_, error);
  }

  [[nodiscard]] const fs::path &Path() const noexcept { return path_; }

private:
  fs::path path_;
};

application::ProjectWorkspaceError Unsupported() {
  return {.code = application::ProjectWorkspaceErrorCode::io,
          .message = "unused test operation"};
}

class SelectedWorkspace final
    : public application::ProjectWorkspaceController {
public:
  explicit SelectedWorkspace(fs::path root) : root_(std::move(root)) {}

  huxerui::Task<application::ProjectWorkspaceResult<
      std::vector<domain::ProjectRecord>>>
  ListProjects() override {
    co_return std::vector{Record()};
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectedProject() override {
    co_return Record();
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  CreateManagedProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  RegisterExternalProject(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectRecord>>
  SelectProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  DeleteProject(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<domain::ProjectFileNode>>
  LoadTree(std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateFile(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  CreateDirectory(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<std::string>>
  ReadText(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  WriteText(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Rename(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Copy(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Move(std::string, std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

  huxerui::Task<application::ProjectWorkspaceResult<void>>
  Delete(std::string, std::string) override {
    co_return std::unexpected(Unsupported());
  }

private:
  [[nodiscard]] domain::ProjectRecord Record() const {
    return {.id = "fixture",
            .label = "Fixture",
            .path = root_.string(),
            .source = domain::ProjectSource::external,
            .description = {},
            .selected = true,
            .created_at = 0,
            .updated_at = 0};
  }

  fs::path root_;
};

struct Scenario final {
  TemporaryDirectory temporary;
  std::shared_ptr<SelectedWorkspace> workspace;
  std::shared_ptr<infrastructure::LocalWorkspaceImageReader> reader;
  bool finished{};
};

std::shared_ptr<Scenario> active;

huxerui::View Probe() {
  const auto scenario = active;
  const auto tasks = huxerui::UseTaskScope();
  huxerui::Lifecycle([scenario, tasks] {
    auto handle = tasks.Launch([scenario]() -> huxerui::Task<void> {
      auto valid = co_await scenario->reader->Read("assets/fixture.png");
      EXPECT_EXPRESSION(valid);
      EXPECT_EXPRESSION(valid->bytes.size() == 8U);
      EXPECT_EXPRESSION(valid->resolved_path.ends_with("assets/fixture.png"));

      auto traversal = co_await scenario->reader->Read("../outside.png");
      EXPECT_EXPRESSION(!traversal);
      EXPECT_EXPRESSION(traversal.error().code ==
             application::ImageUnderstandingErrorCode::not_found);

      auto absolute =
          co_await scenario->reader->Read(
              (scenario->temporary.Path() / "outside.png").string());
      EXPECT_EXPRESSION(!absolute);
      EXPECT_EXPRESSION(absolute.error().code ==
             application::ImageUnderstandingErrorCode::not_found);

      auto linked = co_await scenario->reader->Read("assets/link.png");
      EXPECT_EXPRESSION(!linked);
      EXPECT_EXPRESSION(linked.error().code ==
             application::ImageUnderstandingErrorCode::not_found);

      auto oversized = co_await scenario->reader->Read("assets/large.png");
      EXPECT_EXPRESSION(!oversized);
      EXPECT_EXPRESSION(oversized.error().code ==
             application::ImageUnderstandingErrorCode::too_large);
      scenario->finished = true;
    });
    return [handle] { handle.Cancel(); };
  });
  return huxerui::Text("workspace-image-reader-probe");
}

} // namespace

TEST(workspace_image_readers_tests, LegacySuite) {
  active = std::make_shared<Scenario>();
  const auto root = active->temporary.Path();
  const auto workspace = root / "workspace";
  {
    std::ofstream fixture(workspace / "assets" / "fixture.png",
                          std::ios::binary);
    constexpr unsigned char png[]{0x89, 0x50, 0x4E, 0x47,
                                  0x0D, 0x0A, 0x1A, 0x0A};
    fixture.write(reinterpret_cast<const char *>(png), sizeof(png));
  }
  {
    std::ofstream outside(root / "outside.png", std::ios::binary);
    outside << "outside";
  }
  fs::create_symlink(root / "outside.png", workspace / "assets" / "link.png");
  {
    std::ofstream large(workspace / "assets" / "large.png", std::ios::binary);
    large.put('\0');
  }
  fs::resize_file(workspace / "assets" / "large.png",
                  10U * 1024U * 1024U + 1U);

  active->workspace = std::make_shared<SelectedWorkspace>(workspace);
  active->reader =
      std::make_shared<infrastructure::LocalWorkspaceImageReader>(
          active->workspace);

  const huxerui::Application app(Probe, {.show_debug_overlay = false});
  huxerui::testing::UiTest ui(app);
  ui.PumpUntil([] { return active->finished; });
  active.reset();
}
