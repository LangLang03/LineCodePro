#include "presentation/screens/browser_screen.h"

#include <array>
#include <charconv>
#include <cstdint>
#include <optional>
#include <string>
#include <string_view>

#include <app_resources.h>
#include <huxerui/data.h>
#include <huxerui/huxerui.h>
#include <huxerui/webview.h>

#include "presentation/components/legacy_screen_header_layout.h"
#include "presentation/line_theme.h"

namespace linecode::presentation {
namespace {

using namespace huxerui;

TextStyle Label(float size, FontWeight weight = FontWeight::Regular,
                Color color = colors::text) {
  return TextStyle{Font::System(size).WithWeight(weight), color};
}

std::string LowerAscii(std::string_view value) {
  std::string result(value);
  for (char &character : result) {
    if (character >= 'A' && character <= 'Z') {
      character = static_cast<char>(character + ('a' - 'A'));
    }
  }
  return result;
}

std::optional<std::string> HostOf(const Uri &uri) {
  const auto authority = uri.Authority();
  if (!authority || authority->empty()) {
    return std::nullopt;
  }

  std::string_view host_port = *authority;
  if (const auto user_info = host_port.rfind('@');
      user_info != std::string_view::npos) {
    host_port.remove_prefix(user_info + 1);
  }
  if (host_port.empty()) {
    return std::nullopt;
  }

  if (host_port.front() == '[') {
    const auto closing_bracket = host_port.find(']');
    if (closing_bracket == std::string_view::npos || closing_bracket == 1) {
      return std::nullopt;
    }
    return LowerAscii(host_port.substr(1, closing_bracket - 1));
  }

  if (const auto port = host_port.rfind(':'); port != std::string_view::npos) {
    host_port = host_port.substr(0, port);
  }
  if (host_port.empty()) {
    return std::nullopt;
  }
  return LowerAscii(host_port);
}

std::optional<std::array<std::uint8_t, 4>> ParseIpv4(std::string_view host) {
  std::array<std::uint8_t, 4> octets{};
  for (std::size_t index = 0; index < octets.size(); ++index) {
    const auto separator = host.find('.');
    const std::string_view part = host.substr(0, separator);
    if (part.empty()) {
      return std::nullopt;
    }
    unsigned int value = 0;
    const auto parsed =
        std::from_chars(part.data(), part.data() + part.size(), value);
    if (parsed.ec != std::errc{} || parsed.ptr != part.data() + part.size() ||
        value > 255) {
      return std::nullopt;
    }
    octets[index] = static_cast<std::uint8_t>(value);
    if (index + 1 == octets.size()) {
      if (separator != std::string_view::npos) {
        return std::nullopt;
      }
    } else {
      if (separator == std::string_view::npos) {
        return std::nullopt;
      }
      host.remove_prefix(separator + 1);
    }
  }
  return octets;
}

bool IsLocalHttpHost(std::string_view host) {
  if (host == "localhost" || host == "::1") {
    return true;
  }
  const auto address = ParseIpv4(host);
  if (!address) {
    return false;
  }
  return (*address)[0] == 10 || (*address)[0] == 127 ||
         ((*address)[0] == 172 && (*address)[1] >= 16 && (*address)[1] <= 31) ||
         ((*address)[0] == 192 && (*address)[1] == 168);
}

bool IsAllowedBrowserUrl(std::string_view value) {
  const auto uri = Uri::Parse(value);
  if (!uri) {
    return false;
  }
  const auto host = HostOf(*uri);
  if (!host) {
    return false;
  }
  const std::string scheme = LowerAscii(uri->Scheme());
  return scheme == "https" || (scheme == "http" && IsLocalHttpHost(*host));
}

View Header(const RouteNavigationController<domain::AppRoute> &navigation) {
  return LegacyScreenHeaderLayout{
      Stack{
          Image(app::images::chevron_left)
              .Tint(colors::text)
              .With(Frame{.width = 22.0F, .height = 22.0F}),
      }
          .OnClick([navigation] { navigation.Pop(); })
          .With(Frame{.width = 36.0F, .height = 36.0F},
                Align(HorizontalAlignment::Center, VerticalAlignment::Center),
                Semantics{.role = SemanticRole::Button,
                          .label = app::strings::common_back},
                Focusable(), PointerCursor(PointerCursorKind::Hand)),
      Stack{Text(app::strings::in_app_browser_default_title)
                .Style(Label(17.0F, FontWeight::Bold))}
          .With(Grow(),
                Align(HorizontalAlignment::Center, VerticalAlignment::Center)),
      Spacer().With(Frame{.width = 36.0F, .height = 36.0F}),
  }
      .With(Frame{.min_height = 60.0F},
            Padding(EdgeInsets::Symmetric(16.0F, 12.0F)),
            Background(colors::background));
}

View UnsupportedBrowserContent() {
  return Stack{
      Text(app::strings::in_app_browser_unsupported_url)
          .Style(Label(13.0F, FontWeight::Regular, colors::secondary))
          .Align(TextAlign::Center),
  }
      .With(Grow(), Padding(28.0F),
            Align(HorizontalAlignment::Center, VerticalAlignment::Center),
            Background(colors::background));
}

} // namespace

[[huxerui::composable]] View BrowserScreen(const domain::BrowserRoute &route) {
  const auto navigation = UseNavigation<domain::AppRoute>();
  auto requested_url = UseState(route.url);
  auto browser_state = UseState(WebViewNavigationState{.url = route.url});
  const WebViewController controller = UseWebViewController();

  const std::string address =
      browser_state->url.empty() ? route.url : browser_state->url;
  View browser =
      IsAllowedBrowserUrl(route.url)
          ? WebView({.url = requested_url.Get(),
                     .java_script_enabled = route.java_script_enabled},
                    controller)
                .On<WebViewEvents::NavigationRequested>(
                    [](const WebViewNavigationRequest &request) {
                      return !request.is_main_frame ||
                             IsAllowedBrowserUrl(request.url);
                    })
                .On<WebViewEvents::NavigationChanged>(
                    [requested_url,
                     browser_state](const WebViewNavigationState &state) {
                      if (!state.url.empty()) {
                        requested_url = state.url;
                      }
                      browser_state = state;
                    })
                .With(Grow(), Frame{.min_height = 1.0F}, ClipChildren(),
                      Semantics{.label =
                                    app::strings::in_app_browser_content_desc})
          : UnsupportedBrowserContent();

  return Column{
      Header(navigation),
      Divider(),
      Text(address)
          .Style(Label(13.0F, FontWeight::Regular, colors::secondary))
          .With(Padding(EdgeInsets{
              .top = 0.0F, .right = 28.0F, .bottom = 16.0F, .left = 28.0F})),
      browser,
  }
      .With(CrossAlign(CrossAxisAlignment::Stretch),
            Background(colors::background), SafeAreaPadding{});
}

} // namespace linecode::presentation
