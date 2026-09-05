#include "presentation/screens/model_management_screen.h"

#include <cstddef>
#include <optional>
#include <utility>
#include <variant>

#include <huxerui/huxerui.h>

#include "domain/app_state.h"
#include "domain/model_config.h"
#include "presentation/screens/model_add_options_screen.h"
#include "presentation/screens/model_add_screen.h"
#include "presentation/screens/model_list_screen.h"

namespace linecode::presentation {
namespace {

struct AddOptionsRoute final {
  bool operator==(const AddOptionsRoute &) const = default;
};

struct ModelFormRoute final {
  std::optional<domain::ModelProviderPreset> preset;
  std::optional<domain::ModelConfig> editing;
  bool local{};

  bool operator==(const ModelFormRoute &) const = default;
};

using ModelRoute = std::variant<AddOptionsRoute, ModelFormRoute>;

[[huxerui::composable]] huxerui::View
ModelListDestination(std::shared_ptr<application::ModelStore> store) {
  const auto app_navigation = huxerui::UseNavigation<domain::AppRoute>();
  const auto model_navigation = huxerui::UseNavigation<ModelRoute>();
  return ModelListScreen(
      std::move(store),
      {
          .on_back = [app_navigation] { app_navigation.Pop(); },
          .on_add =
              [model_navigation] { model_navigation.Push(AddOptionsRoute{}); },
          .on_edit =
              [model_navigation](domain::ModelConfig model) {
                model_navigation.Push(ModelFormRoute{
                    .editing = std::move(model),
                });
              },
      });
}

[[huxerui::composable]] huxerui::View
ModelFlowDestination(const ModelRoute &route,
                     std::shared_ptr<application::ModelStore> store,
                     std::shared_ptr<application::ModelCatalogGateway> catalog,
                     huxerui::State<std::size_t> list_revision) {
  const auto navigation = huxerui::UseNavigation<ModelRoute>();
  if (std::holds_alternative<AddOptionsRoute>(route)) {
    return ModelAddOptionsScreen({
        .on_back = [navigation] { navigation.Pop(); },
        .on_custom = [navigation] { navigation.Push(ModelFormRoute{}); },
        .on_local =
            [navigation] { navigation.Push(ModelFormRoute{.local = true}); },
        .on_preset =
            [navigation](domain::ModelProviderPreset preset) {
              navigation.Push(ModelFormRoute{.preset = preset});
            },
    });
  }

  const auto &form = std::get<ModelFormRoute>(route);
  return ModelAddScreen(
      {
          .preset = form.preset,
          .editing = form.editing,
          .local = form.local,
      },
      std::move(store), std::move(catalog),
      {
          .on_back = [navigation] { navigation.Pop(); },
          .on_saved =
              [navigation, list_revision](domain::ModelConfig) {
                list_revision += 1;
                navigation.SetPath(huxerui::NavigationPath<ModelRoute>{});
              },
      });
}

} // namespace

[[huxerui::composable]] huxerui::View ModelManagementScreen(
    std::shared_ptr<application::ModelStore> store,
    std::shared_ptr<application::ModelCatalogGateway> catalog) {
  auto path = huxerui::UseState(huxerui::NavigationPath<ModelRoute>{});
  auto list_revision = huxerui::UseState(std::size_t{0});
  auto root = [store, list_revision]() -> huxerui::View {
    return ModelListDestination(store).Key(list_revision.Get());
  };
  auto destination = [store = std::move(store), catalog = std::move(catalog),
                      list_revision](const ModelRoute &route) -> huxerui::View {
    return ModelFlowDestination(route, store, catalog, list_revision);
  };
  return huxerui::NavigationStack(std::move(root), path,
                                  std::move(destination));
}

} // namespace linecode::presentation
