# Platform Views

`PlatformView` embeds a platform-owned interactive leaf control in HuxerUI layout.
It is a View, not a modifier, layout, Canvas command, or non-visual Module.

## Typed declaration

Wrap the generic declaration in a concrete component owned by the feature:

```cpp
struct WebViewProperties {
  std::string url;

  bool operator==(const WebViewProperties&) const = default;
};

struct WebViewEvents {
  struct NavigationChanged : Event<void(const NavigationState&)> {
    static constexpr std::string_view Name = "navigationChanged";
  };
};

namespace web_view {

inline constexpr char type[] = "web/WebView";

} // namespace web_view

View WebView(WebViewProperties properties) {
  return PlatformView(web_view::type, std::move(properties));
}
```

Properties are one complete immutable strongly typed snapshot.
The no-properties form is `PlatformView(name)`.
Register the exact Properties and optional Controller types with `RootContext::RegisterPlatformView()` from one RootHook.
Registration names are nonempty case-sensitive UTF-8 identities and do not require `/`.
Use one feature-owned type constant for both the component declaration and every selected platform registration rather than repeating the string literal.
Keep the name, raw `PlatformView` declaration, platform factory types, and registration inside that owning feature.
A reusable library or a feature with platform-selected implementations may expose one `InstallWebView(RootContext&)`; an app-local one-off implementation may register from its existing RootHook without adding an installer abstraction.
Page code uses `WebView(...)` and typed events or Controllers only.

Events need no separate registration list.
The component attaches ordinary typed handlers with `.On<Key>(...)`, and the platform factory receives one `PlatformEventEmitter` that calls `Emit<Key>(value)`.
When an implementation crosses a platform-language boundary, the event value type owns `Decode(const PlatformPayload&)`; direct C++ emission remains strongly typed.
A PlatformView event may return a synchronous decision through `Event<Result(Argument)>`; typed C++ emission returns `std::optional<Result>`, while Java/Kotlin, Objective-C/Swift, and JavaScript receive an optional result payload from the same `emit` operation.
Emission is synchronous and valid only on the mounted View's owning UI thread.
The platform implementation chooses its explicit fallback when no handler produces a result.
Do not use a result event for asynchronous work or Module RPC.

An optional Controller is a library-defined typed command facade.
If the component exposes one, accept it through the concrete component API, attach it internally with `.Controller(controller)`, and register that exact Controller type.
The factory connects the retained platform instance on mount or Controller replacement and disconnects before disposal.
HuxerUI does not require Controller inheritance, State, pimpl, Access, Backend, or Connection types.

## Factory and platform boundary

Use the active platform's public `platform_registry.h` factory contract:

- Windows returns a same-process, same-thread child `HWND` of the supplied parent.
- Android returns a Java `View` through either a direct JNI factory or `android::JavaPlatformViewFactory`.
- iOS returns a detached stable `UIView*` from Objective-C++ or an actual Objective-C/Swift factory object.
- macOS returns a detached stable `NSView*` from Objective-C++ or an actual Objective-C/Swift factory object.
- Web returns a detached DOM element through a direct Emscripten C++ factory or `web::JavaScriptPlatformViewFactory`.
- Linux does not currently implement PlatformView; do not present it as available or add a parallel embedding path.

`PlatformValue` is the public low-level in-process carrier used by RenderScene and platform factory adaptation to retain exact C++ Properties, Controller, and event value types.
It never crosses a platform-language boundary, and ordinary components and direct factories use their concrete types rather than constructing it themselves.

Registry installation supplies the owning `PlatformAdapter&` only to the factory's internal binding operation.
Direct create, update, Controller, and disposal callbacks receive their exact platform handles and typed values rather than the adapter.

Android's common Java/Kotlin adapter uses `.class_name` for the Java factory class.
When a Controller exists, its `.connect` callback attaches the framework-owned `PlatformChannel` to the exact C++ Controller and `.disconnect` detaches it.
Web's common JavaScript structural adapter uses `.factory` for the actual `emscripten::val` factory object; a `*_factory_name` string may only identify the module property used to obtain that object.
Properties use `Module.HuxerUI.PlatformPayload`, and events use one framework-owned emitter.
iOS and macOS expose `UIKitPlatformViewFactory` or `AppKitPlatformViewFactory`, their View protocols, payload endpoints, and cancellation endpoints through the `HuxerUIPlatform` Clang module.
The Objective-C++ RootHook sets `.factory` to the actual Objective-C or Swift factory object in `ios::ObjectiveCPlatformViewFactory<Properties, Controller>` or `macos::ObjectiveCPlatformViewFactory<Properties, Controller>`.
Its `connect` callback attaches the returned `PlatformChannel` to the exact library Controller, and `disconnect` detaches that Controller before View disposal.
Factories receive the owning `UIViewController` or `NSWindow`; they return the stable detached View and never attach it themselves.
Direct Objective-C++ factories remain available through `ios::PlatformViewFactory` and `macos::PlatformViewFactory` without a payload round trip.
Every path still registers once through the library RootHook; application hosts, delegates, and Web mount calls do not form a second registry.
The Web JavaScript `PlatformPayload` bridge does not transport `ExternalTexture`; direct Emscripten C++ PlatformView factories still retain exact Properties, and the direct C++ ExternalTexture path remains available without another data channel.

Factories own create, update, optional Controller connect/disconnect, and dispose symmetry.
Failed creation publishes no event and releases any instance or platform object already returned by the factory.
Events are accepted only after a candidate commits, and disposal invalidates delivery before releasing platform state.
Create-time, off-thread, disabled, and detached emissions invoke no application handler and produce no result.
Malformed payloads and handler failures also produce no result; PlatformView handler exceptions do not escape through a platform-language boundary.

## Geometry and behavior

Provide bounded geometry because a PlatformView has no portable intrinsic size.
The shared contract covers layout placement, rectangular visibility and clipping, composition order, focus/input integration, update, typed events, and lifecycle according to the backend.

A PlatformView owns ordinary keyboard handling while native focus is inside its subtree.
On hosts that implement native Tab-boundary reporting, exhausted native traversal re-enters Runtime's ordinary key route so `KeyIntercept`, focused-target events, and focus defaults remain one path.
Do not forward every native key through a second PlatformView event convention.

Do not promise arbitrary rotation, path clipping, group opacity, backdrop filters, or transparent mixing unless the active platform contract explicitly supports them.
Choose `ExternalTexture` when the platform only produces frames and HuxerUI should own effects, interaction, and surrounding semantics.

## Review points

- concrete typed component instead of raw names in page code;
- one feature boundary owns the stable name and registration;
- complete Properties snapshot and controlled owner updates;
- inferred typed events with no parallel event registry;
- optional Controller with deterministic connect/disconnect lifetime;
- bounded geometry and documented backend limits;
- create/update/dispose failure symmetry, focus, IME, accessibility, and unmount behavior;
- payload conversion only where a platform-language boundary exists.
