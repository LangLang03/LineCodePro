# Fundamentals

## Components and composition

A component is an ordinary function returning `View`. The application root is supplied directly to `Application` and already owns a scope.

Use `[[huxerui::composable]]` on a reusable function that directly calls composition-bound facilities such as `UseState`, `UseEnvironment`, `UseTheme`, `UseEvents`, `UseApplication`, `UseTaskScope`, `UseNavigation`, or presentation hooks. Also mark a reusable component when it needs an independent recomposition boundary so State changes observed inside it recompose that component instead of its caller. Do not annotate every component for performance by default, and do not annotate the app root.

Define annotated functions in `.cpp`, `.cc`, or `.cxx` source files so composable code generation can transform them. Do not place an annotated definition only in a header.

Do not create a View-producing composable solely to wrap a custom `UseXxx()` hook that returns a non-View value; the hook shares its composable caller's active scope.

## View value semantics

`View` is a lightweight, copyable, copy-on-write declaration value. Copying it shares the declaration until a fluent mutation needs a unique copy. Pass Views into component functions and containers by value without adding defensive `std::move` calls.

Fluent APIs such as `.With`, `.On`, `.OnClick`, `.Key`, and component-specific configuration are rvalue-qualified. A freshly constructed component is already a temporary, so chain directly:

```cpp
return Button("Save")
    .OnClick(save)
    .With(Padding(12.0F));
```

When a named local View must be modified through an rvalue-qualified fluent API, consume it once and do not use the moved-from value afterward:

```cpp
View content = BuildContent();
return std::move(content).With(Padding(16.0F));
```

Prefer constructing that chain directly when practical. Do not move temporary Views, move every child into a container, or write `return std::move(view);`; the last form can prevent return-value optimization. Use `std::move` for a View only when the API actually requires consumption of a named value, not as a blanket performance convention.

## State

- `UseState(initial)` returns copyable `State<T>`.
- Ordinary reads can use the implicit `const T&` conversion or `state->member`; call `Get()` only when an explicit reference helps overload resolution or clarity.
- Ordinary writes use `state = value`; `+=`, `-=`, `*=`, `/=`, `%=`, `&=`, `|=`, `^=`, `<<=`, or `>>=` when supported by `T`; and prefix/postfix increment or decrement. There is no required setter call.
- Use `state.Update([](T& value) { ... })` when one custom mutation must change fields or behavior that the operators cannot express. Do not attempt to mutate through the const reference returned by `Get()`.
- Reads subscribe the current composition scope; writes invalidate subscribers.
- State identity depends on scope, source location, and occurrence at that location.
- `StateList<T>` provides observable list operations such as `PushBack`, `Insert`, `Set`, `Erase`, `Move`, `PopBack`, and `Clear`.
- Use stable `View::Key(...)` values when dynamic stateful siblings can be inserted, removed, or reordered.
- `ForEach` expands a range into `Views`; item state still needs stable component scope/key identity when order changes.

```cpp
auto count = UseState(0);

return Column {
  Text::Format("Count: {}", count),
  Button("Increment").OnClick([count] {
    count += 1;
  }),
};
```

## Events

Bind generic typed events with `view.On<Key>(handler)`. Convenience methods such as `OnClick`, `OnChanged`, and `OnSubmitted` map to typed event keys; do not wire a second callback path for the same action.

`UseEvents()` exposes an emitter for app-side component composition where a component needs to emit its own typed event. Prefer existing built-in component events over a parallel owner callback convention.
Declare the complete handler signature with `Event<Result(Arguments...)>`.
Use `Event<void(Arguments...)>` for notifications.
A value-returning event is a synchronous decision owned by its key, and `Emit<Key>(...)` returns `std::optional<Result>` so the caller must choose the fallback for a missing binding.
Do not add `VoidEvent`, result-event aliases, a second registration method, or a parallel callback convention.

For an existing retained modifier, use `NodeExtension::EmitEvent<Key>(...)` to emit through its owning View's current binding; use an explicitly supplied EventEmitter for an outer component's event. See [typed extension output](node-extensions.md#typed-output) for allowed callback phases and lifetime constraints.

### Keyboard routing

Use `ViewEvents::KeyDown` and `KeyUp` for focused-View behavior after built-in component handling, and return `true` only when the application consumed the event.
Use `ViewEvents::KeyIntercept` when a shortcut or parent policy must run before the focused component; Runtime visits the active focus-scope route from root to target and stops at the first true result without bubbling.
Unhandled keys continue to Runtime defaults and then the platform.
Prefer `OnClick` for ordinary control activation because it already includes keyboard and accessibility activation.

`KeyEvent::key` distinguishes portable keys, including main-row and numeric-keypad keys, left/right modifiers, punctuation, international keys, and named function keys, independently of layout-resolved `text`.
Release events have empty text and `repeat == false`; `Key::Unknown` remains dispatchable.
Do not add a shortcut registry, key-chord type, callback property, or a second keyboard route when these typed events are sufficient.

## Environment and theme

`Environment` is typed hierarchical propagation. `UseEnvironment<T>()` resolves the closest provided value or `T::Default()`. `ProvideEnvironment(value, content)` provides a value to descendants. `UseTheme()` is the visual specialization for `ThemeSpec`.

Environment and theme reads belong inside a composable boundary. Do not capture a reference returned by `UseEnvironment` beyond the current composition.

`UseViewportClass()` reads the current compact/medium/expanded classification. The application chooses responsive structure; HuxerUI does not silently replace arbitrary page layouts.

## Lifecycle and tasks

`Lifecycle(setup, dependencies...)` runs setup while the declaring composition lifetime is mounted and runs its cleanup on dependency change or unmount. Dependencies can be values, `State`, or `StateList`.

`UseTaskScope()` returns a lifetime-bound scope for launching `Task<void>` work. Cancellation follows the mounted composition lifetime. Use `Delay` and task APIs rather than detached threads that update UI state after unmount.

Use `RunWorker()` for owned synchronous CPU-bound or blocking work that must not run on the UI thread. Do not access State, composition, Views, or UI-affine platform objects from its callable. After `co_await RunWorker(...)`, the continuation is already back on the owning UI thread and may update State directly; do not call `TaskScope::Post()` for that continuation. Cancellation discards queued work or the result of work already running.

Use `WorkerSequence::Run()` when one application-owned blocking resource must process operations strictly one at a time. Retain or copy one WorkerSequence for that resource, accept the mandatory leading `std::stop_token`, and return promptly after cancellation; a canceled active callable keeps the sequence occupied until its cleanup finishes. WorkerSequence shares the RunWorker pool and does not provide worker-thread affinity.

Use `TaskScope::Post()` only when an external thread or callback outside a running HuxerUI Task must enqueue an owned `void` update on the scope's UI thread. A closed scope ignores late posts. Neither worker API nor `Post()` requests or guarantees mobile background execution, and Web builds without a worker execution capability report worker execution as unavailable.

Prefer concise duration literals such as `200ms` and `2s` when calling `Delay`.

```cpp
auto tasks = UseTaskScope();

tasks.Launch([]() -> Task<void> {
  co_await Delay(200ms);
  co_await Delay(2s);
});
```

`UseApplication()` returns the current Runtime's `ApplicationHandle`. Its `StartupActivation()` exposes the cold-start `ApplicationActivation`, whose alternatives are `LaunchActivation`, `UrlActivation`, and `FileActivation`. `UrlActivation::url` is a validated `Uri`, while `FileActivation` retains platform-granted `FileReference` capabilities. Its `OnActivation(...)` receives only later activations while the declaring composition lifetime is mounted.

`ApplicationHandle::LifecycleState()` reads and subscribes to the current `ApplicationLifecycleState`; use `ApplicationHandle::OnLifecycleChange(...)` when every ordered transition matters. Obtain the handle with `UseApplication()`, register callbacks during composition, and keep navigation or file-opening policy in application state.

`ApplicationHandle::CheckPermissionAsync()`, `RequestPermissionAsync()`, and `OpenPermissionSettingsAsync()` expose typed camera and microphone authorization through the owning Task execution.
Await them from a `TaskScope`; the continuation resumes on its UI thread and may update `State` directly.
Native manifests, Apple usage descriptions, entitlements, request rationale, and final feature policy remain application-shell responsibilities.
Treat `PermissionStatus::Unavailable` as a normal platform-capability result rather than inventing a fallback permission path.

## Reconciliation

`View` is a transient declaration. Retained mutable behavior belongs in state, controllers, or mounted extensions. Unkeyed siblings reconcile by position. Component functions should not retain raw node, child, or platform pointers across reconciliation.
