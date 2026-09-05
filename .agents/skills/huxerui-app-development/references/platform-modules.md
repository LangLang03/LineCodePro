# Platform Modules

Use a PlatformModule for a non-visual capability whose implementation depends on the current platform.
Do not use one for portable C++ services, embedded controls, or frame production that fits `ExternalTexture`.

## Facade and ownership

Keep the stable registration name and RootHook wiring in the feature that owns the capability.
A reusable library or a feature with platform-selected implementations normally exposes one `InstallXxx(RootContext&)` function; an app-local one-off implementation may register directly in its existing RootHook instead of adding an installer abstraction.
Application UI consumes the feature's typed service or value and never opens a raw name, handles `PlatformPayload`, or retains `PlatformChannel`.
Use root ownership only when one per-window Module instance is intentionally shared by unrelated consumers.
In that case, register, open, and provide the exact Module once, then expose an ordinary domain hook:

```cpp
namespace {

inline constexpr char audio_player_type[] = "audio/Player";

} // namespace

void InstallAudioPlayer(RootContext& root) {
  root.RegisterPlatformModule<std::shared_ptr<AudioPlayer>>(audio_player_type, CreateAudioPlayer);
  root.Provide(root.OpenPlatformModule<std::shared_ptr<AudioPlayer>>(audio_player_type));
}

std::shared_ptr<AudioPlayer> UseAudioPlayer() {
  return UseService<AudioPlayer>();
}
```

A component- or session-owned Module instead opens only from committed `Lifecycle` setup and releases from cleanup.
Choose one ownership path for a Module instance; do not add a second registry, lookup object, or application-visible transport facade.

## Typed C++ contract

The library defines the Module and optional Options types.
Register one exact factory from a RootHook:

```cpp
root.RegisterPlatformModule<AudioPlayer, AudioPlayerOptions>(
    "audio/Player",
    [](PlatformAdapter& adapter, const AudioPlayerOptions& options) {
      return CreateAudioPlayer(adapter, options);
    }
);
```

Every direct C++ factory receives the owning surface's non-owning `PlatformAdapter&`, followed by the exact Options type when present, and returns the exact Module type.
The adapter provides the existing host capabilities without introducing a second factory context; it remains owned by the surface and must not be retained beyond that surface's lifetime.
Direct C++ factories do not encode values into `PlatformPayload`.
Registration names are nonempty case-sensitive UTF-8 identities and do not require `/`.

Open a root-owned Module with `RootContext::OpenPlatformModule<Module>(name)` or its typed Options overload, then optionally expose it through `root.Provide()`.
For component lifetime, call the typed free `OpenPlatformModule<Module>(name)` or its typed Options overload only from committed `Lifecycle` setup and release the returned Module from cleanup.
There is no generic `UsePlatformModule`, public registry accessor, mandatory service base, or dynamic method list.

## Cross-language implementations

`PlatformPayload` and `PlatformChannel` belong only at a C++/platform-language boundary.
Keep them behind the library's typed Module facade.
A structured boundary type owns its static `Encode(const T&)` or `Decode(const PlatformPayload&)` operation; direct C++ implementations do not call those operations.

Android currently provides `android::JavaPlatformModuleFactory<Module, Options>` for Java or Kotlin implementations.
Set `.class_name` to the Java factory class and use `.create` to wrap one framework-owned `PlatformChannel` in the library's exact Module type.
The Java implementation receives one `HuxerUIPlatformChannel.Events` emitter and uses the SDK `PlatformPayload` value; it does not declare one JNI callback per event.

Web provides `web::JavaScriptPlatformModuleFactory<Module, Options>` for a linked JavaScript structural factory.
Set `.factory` to the actual `emscripten::val` factory object, and use `.create` to wrap the framework-owned `PlatformChannel` in the library's exact Module type.
A string such as `audio_player_factory_name` may identify the module property used to obtain that object, but the adapter field remains `.factory`; it is not a factory name.
JavaScript receives immutable `Module.HuxerUI.PlatformPayload` values and one framework-owned events endpoint; it does not require inheritance or a second name registry.
The Web JavaScript `PlatformPayload` bridge does not transport `ExternalTexture`; use the direct Emscripten C++ texture path when Web must provide frames rather than adding another bridge channel.

iOS and macOS expose their Objective-C/Swift contracts through the pure Objective-C Clang module `HuxerUIPlatform`.
An Objective-C or Swift implementation conforms to `UIKitPlatformModuleFactory` or `AppKitPlatformModuleFactory` and returns a `PlatformModule` instance.
The library's Objective-C++ RootHook sets `.factory` to the actual Objective-C or Swift factory object and uses `.create` to wrap its channel in the matching typed adapter:

```cpp
ios::ObjectiveCPlatformModuleFactory<std::shared_ptr<AudioPlayer>, AudioPlayerOptions> factory{
    .factory = actual_factory,
    .create = [](PlatformChannel channel) {
      return std::make_shared<ChannelAudioPlayer>(std::move(channel));
    },
};
root.RegisterPlatformModule<std::shared_ptr<AudioPlayer>, AudioPlayerOptions>(
    "audio/Player",
    std::move(factory)
);
```

Use `macos::ObjectiveCPlatformModuleFactory` for AppKit.
The framework does not look up a class name, generate a registrant, or require the application delegate to register the factory again.
Direct Objective-C++ implementations remain strongly typed through `ios::PlatformModuleFactory` or `macos::PlatformModuleFactory` and do not use payloads.

`PlatformChannel::Invoke` returns a request identity before scheduling the platform invocation on the owning UI thread.
Use `Invoke<Result>(method, completion)` when both the argument and result are Null; the typed C++ completion receives `PlatformResult<std::monostate>`.
Results and events return asynchronously through that dispatcher.
PlatformChannel event subscriptions remain void notifications even though the generic `Event` type supports synchronous result signatures for local code and PlatformView delegate decisions.
Keep Module request results on `Invoke`; do not model RPC as a result-returning event.
`Cancel` and `Close` invalidate C++ delivery immediately; queued invocations are skipped, in-flight cancellation runs before disposal, and late results or events are ignored.
The channel is a reusable transport convenience, not a PlatformModule base class or the Module API exposed to application UI.
Apple factory creation, invocation, cancellation, and disposal run on the UIKit or AppKit main thread; results and events may originate on any queue and resume through the owning surface dispatcher.

## Review points

- one explicit RootHook registration and no platform-host registration path;
- stable name and registration owned by one feature boundary, with an installer only when it adds value;
- application UI sees only the typed domain facade;
- exact Module and Options types on the direct C++ path;
- payload conversion confined to an actual language boundary;
- deterministic ownership, cancellation, and disposal;
- no string methods, payload maps, or channels exposed through application components;
- no PlatformModule where portable C++ already owns the capability.
