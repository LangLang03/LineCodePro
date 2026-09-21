# Navigation and Window

## Navigation surfaces

`NavigationBar` and `NavigationPane` are destination selectors, not page routers. Keep their selected index controlled. `DrawerLayout` combines app content with optional `StartDrawer` and `EndDrawer`; each drawer's open state is controlled through `.Open` and `.OnOpenChanged`.

## Navigation stack

`NavigationStack(root_factory)` provides imperative factory-based navigation. Routed navigation uses:

- copyable, equality-comparable route values;
- `State<NavigationPath<Route>>` as authoritative path state;
- a root factory;
- a resolver from `Route` to `View`;
- `UseNavigation<Route>()` for the nearest compatible stack;
- `UseRootNavigation<Route>()` for the root compatible stack.

The controller supports `Push`, `Replace`, `Pop`, `SetPath`, `CanPop`, and `Depth`. Keep route parameters in the route value instead of side-channel global state. Nested stacks may own local flows while root navigation handles application-level destinations.

On Web, include `<huxerui/web/navigation.h>` and use `BrowserNavigationStack` with a codec whose `Decode(location)` returns an optional path and whose `Encode(path)` returns a same-document location. The codec owns URL policy. One browser navigation stack owns URL synchronization per document.

## Window content

`WindowOptions` configures the title, initial size, optional minimum size, content mode, chrome mode, preferred custom title-bar height, and caption labels exposed by the active SDK. `minimum_size` is a minimum logical client size for framework-owned resizable desktop windows; when present, initialize it with `Size{width, height}` and keep both dimensions finite and positive. An initial size below it is raised independently on each axis. Android, iOS, and Web viewports remain host-owned and may be smaller, so do not use this option as a layout constraint or clamp reported viewport metrics. `WindowChromeMode::System` leaves chrome to the platform; `WindowChromeMode::Custom` lets application content occupy the title-bar area while retaining HuxerUI's platform-specific window behavior. Use `WindowContentMode::SafeArea` for automatically inset content and `WindowContentMode::EdgeToEdge` only when application content deliberately handles insets.

`SafeAreaPadding` consumes selected safe-area edges without hardcoded platform values. Ancestor consumption affects what descendants see.

`WindowTitleBar` lays out application-defined title-bar content, consumes the platform-resolved caption insets, and marks its non-interactive area as draggable. Interactive descendants such as buttons, tabs, and text fields remain interactive inside it and need no exclusion rectangles. Do not duplicate framework- or platform-managed caption controls. Use `WindowDragRegion` only when another mounted region should drag the window.

`UseWindow()` exposes `Show`, `Hide`, `Activate`, `Minimize`, `Maximize`, `Restore`, `ToggleMaximize`, and `Close`. `Activate` restores a minimized window and requests foreground activation, subject to platform focus policy. It does not expose window dragging, state observation, platform window handles, or application-readable caption metrics.

`OnMinimizeRequest` and `OnCloseRequest` register lifecycle-bound handlers for platform and application window requests. Return `true` only after application code has handled the request; returning `false` preserves the normal platform action. Keep handlers synchronous and use the dependency arguments when captured values should replace a committed handler.

System bar background and foreground brightness come from the resolved `SystemBarsAppearance`, which is both a Theme value and a View modifier. `Light` and `Dark` describe system foreground icons and text, not the background color. Desktop custom chrome and mobile safe areas share a public window boundary but have different platform behavior.

## Activation

Use `UseApplication().StartupActivation()` for the cold-start `ApplicationActivation`. Inspect its `LaunchActivation`, `UrlActivation`, `FileActivation`, or `NotificationActivation` alternative with `std::visit` or `std::get_if`. `UrlActivation::url` is an already validated `Uri`; inspect its components without reparsing `ToString()`. `FileActivation` contains `FileReference` capabilities rather than assumed local paths. `NotificationActivation` contains the validated stable identifier supplied with a local notification. Register `UseApplication().OnActivation(...)` for later activations while the declaring composition lifetime is mounted. Route external URLs, files, or notification identifiers by updating the authoritative navigation path rather than creating a parallel page stack.

For local-notification submission, templates, scheduling, and activation data, read [local-notifications.md](local-notifications.md).

On Windows, register an application-owned custom URL scheme with `windows::RegisterUrlScheme(scheme, display_name)` from `<huxerui/system.h>` in explicit entry code, normally before `RunApplication()`. Prefer this public API over hand-written registry helpers or static-initialization side effects. It targets the current executable and current user, including the Windows 7 compatibility backend; no notification identity, COM setup, or CMake registration metadata is required. Use a bare ASCII scheme without ':', with a letter followed by letters, digits, '+', '-', or '.', and 2 to 255 characters. Registration persists after exit. Call `windows::UnregisterUrlScheme(scheme)` only for explicit removal or relocation, not from normal lifecycle cleanup. Repeated registration by the same executable is allowed; another executable or a machine-wide registration is rejected. Report conflicts rather than deleting another application's keys. This API does not override user default-app choices, register file associations, or manage all-user installation; other platforms still use their native manifest or bundle declarations. Incoming URLs continue through `UrlActivation` and require application-level validation and authorization.

## System tray

`UseApplication().SystemTray()` returns the application-level tray handle, and `UseApplication().Quit()` requests orderly application termination. Check `IsAvailable()` before hiding the last visible window because unsupported hosts and temporarily unavailable Linux tray hosts report `false`.

Declare tray presentation in `Lifecycle(...)`: call `Show(icon, options)` during setup and `Hide()` from cleanup. `SystemTrayOptions` reuses `MenuEntry`, `MenuItem`, and `MenuSection`; tray and menu icons are `ImageVariant` values that must resolve to raster `ImageAsset` values. Register primary activation through `OnActivate(...)`, commonly restoring the window with `WindowHandle::Activate()`.

Compose minimize-to-tray behavior from the tray handle and window request handlers. When the tray is unavailable, return `false` so minimize and close retain their normal platform behavior. Do not create a second menu model, a `PlatformModule`, or platform-specific tray code for ordinary application use.
