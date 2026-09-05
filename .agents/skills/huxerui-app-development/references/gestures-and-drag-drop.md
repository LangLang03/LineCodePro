# Gestures and Drag-and-Drop

Use the recognizers in `<huxerui/gesture.h>` instead of rebuilding gesture state from raw pointer events.
Ordinary activation remains `.OnClick(...)` because Click also covers keyboard and accessibility invocation.

## Typed gesture lifecycle

- `MultiTapGesture` emits `MultiTapEvents::Recognized` after its configured number of successful taps.
- `LongPressGesture` emits `LongPressEvents::Started`, `Ended`, and `Canceled`.
- `DragGesture` emits `DragEvents::Started`, `Changed`, `Ended`, and `Canceled`; configure an optional axis, distance, or press duration.
- `TransformGesture` emits `TransformEvents::Started`, `Changed`, `Ended`, and `Canceled` after two or more compatible pointers participate.

Gesture coordinates use logical pixels.
Node-local coordinates remain based on the transform captured when recognition begins, while window coordinates track the host window.
Handle `Canceled` whenever accepted gesture state or visuals need cleanup.
Do not combine raw pointer handling with a built-in recognizer to observe or own the same physical operation.

## Pointer events

`PointerEvent` reports `type`, `pointer_id`, window-logical `position`, `device_kind`, `changed_button`, and `pressed_buttons`.
Its `PointerEventType` is `Down`, `Move`, `Up`, or `Cancel`, and `PointerDeviceKind` distinguishes Mouse, Touch, and Pen.
`PointerButton` is a flag enum with `Primary`, `Secondary`, `Middle`, `Back`, and `Forward`; Primary and Secondary follow system roles rather than fixed physical left and right positions.
`changed_button` is the button added by Down or removed by Up, while `pressed_buttons` is the complete post-event mask.
Use `event.IsButtonPressed(mask)` for raw chord logic.
Use `MultiTapGesture` when repeated successful taps are part of the application behavior; raw pointer events do not carry a platform click count.

`ViewEvents::Pointer` is one void notification for the complete Down, Move, Up, and Cancel lifecycle of the deepest eligible raw target.
It does not capture, bubble, return a handled result, or acquire pointer ownership.
When another recognizer accepts after raw Down, the raw target receives one Cancel update and no later event from that sequence.
Use `.OnClick(...)` for semantic activation, a built-in gesture for standard recognition, and `PointerIntercept` only for custom synchronous ownership decisions driven by pointer updates.
Built-in Click, selection, gestures, scrolling, and retained component interaction recognize only an unchorded Primary sequence.
Middle, Back, and Forward remain available to the raw Pointer handler and PointerIntercept without implicit semantic behavior.

Bind `ViewEvents::ContextMenuRequested` for context actions:

```cpp
return content.On<ViewEvents::ContextMenuRequested>([menu](Point position) {
  menu.ShowAt(position, {MenuItem("Refresh", Refresh)});
});
```

An unchorded Secondary tap invokes the deepest enabled binding after raw Up and supplies its window-logical release position.
The Context Menu key and Shift+F10 use the nearest enabled binding on the focused route and supply that View's center.
Binding presence claims the request, so do not add a handled result, manually search parents, or rebuild secondary-tap recognition from raw Down.
PlatformViews retain native context menus, and Web preserves a pointer-initiated browser menu outside HuxerUI content that declares this binding.

## Hover

Bind `ViewEvents::Hover` for direct mouse or pen presence without claiming a pointer sequence:

```cpp
return content.On<ViewEvents::Hover>([hovered](const HoverEvent& event) {
  hovered = event.type != HoverEventType::Leave;
});
```

`HoverEventType` is `Enter`, `Move`, or `Leave`.
Its `position` is local to the receiving View and `window_position` remains window logical.
Touch does not produce Hover, while disabled Views remain eligible.
Nested bound Views each receive an independent containment lifecycle rather than a bubbled event.
A Hover-only visual overlay does not become an ordinary pointer target, and a `PlatformView` owns hover over its native content.

Exact duplicate positions do not emit Move.
Final geometry changes may still emit Enter or Leave under a stationary pointer.
Use Enter and Leave for continuous presence, invert those transitions for departure-driven UI, or restart a lifecycle-bound `TaskHandle` on Enter and Move when content should appear only after the pointer remains still.
`Delay` resumes on the owning UI thread, so a delayed handler may update `State` without `Post`.
Do not invent `HoverStopped`, a timer service, or a raw Pointer Move state machine for these cases.

## Pointer cursors

Declare a portable mouse or pen cursor with the ordinary `PointerCursor` property modifier:

```cpp
return Canvas(painter).With(PointerCursor(PointerCursorKind::Crosshair));
```

The deepest explicit declaration under the pointer wins.
`PointerCursorKind::Default` is an explicit declaration that stops an ancestor cursor from applying to that region.
Disabled Views remain eligible because a cursor is presentation rather than interaction ownership.

The cursor kind may come from `State`; ordinary local recomposition updates the declaration even while the pointer is stationary.
Assigning the same state value does not recompose or resend the cursor.
A `PlatformView` owns the cursor over its native content, and a platform without a traditional pointer cursor may ignore the declaration.

Do not add a raw pointer handler, resolver callback, or retained `NodeExtension` solely to select a cursor.
Use `.With(PointerCursor(kind))` on the narrowest region whose interaction calls for that cursor, and inspect the active SDK header for the available portable kinds.

## Pointer interception

Bind `ViewEvents::PointerIntercept` when application-level pointer logic must observe a sequence and synchronously decide when to own it.
Its signature is `Event<bool(const PointerEvent&)>`:

```cpp
return content.On<ViewEvents::PointerIntercept>([](const PointerEvent& event) {
  return event.type == PointerEventType::Move && ShouldTakePointer(event.position);
});
```

Returning false keeps the recognition pending; the first deepest-to-root handler that returns true becomes the sole PointerSession owner, cancels an already-started raw target, and receives subsequent Move, Up, and Cancel outside its original bounds.
Once accepted, later return values are ignored.
Compatible recomposition keeps ownership on the same mounted View and subsequent updates use its current PointerIntercept binding.
This is one recognizer in the existing PointerSession, not event capture, bubbling, or a public pointer handle.

Use `PointerIntercept` for decisions driven by incoming pointer updates.
Use `LongPressGesture` or a delayed `DragGesture` when recognition must advance at a deadline while the pointer is stationary; a synchronous event return cannot acquire ownership when no event is being dispatched.
PlatformView ownership is still decided at initial Down and never transfers across the native boundary afterward.
An accepted PointerIntercept may continue to own multi-button chords; adding a button cancels pending or accepted standard recognition.

## Multi-pointer transform

`TransformEvent` reports incremental values rather than an authoritative accumulated transform:

- `pan` is centroid displacement since the previous update;
- `scale` is a multiplicative factor whose identity is `1.0F`;
- `rotation` is an angular delta in radians, positive clockwise;
- `centroid` and `window_centroid` identify the current center;
- `pointer_count` reports current participation.

Retain accumulated transform state in the application: add `event.pan` to the offset, multiply the scale by `event.scale`, and add `event.rotation` to the angle. Bind those updates through `TransformEvents::Changed` on the transformed content.

`Point` supports addition, subtraction, scalar multiplication, and scalar division, including their compound-assignment forms.
Adding or removing a pointer while at least two remain can produce an identity `Changed` event that rebases the calculation; do not treat every `Changed` event as nonzero motion.

## Drag and release motion

`DragEvent` exposes origin, current local and window positions, incremental `delta`, total `translation`, and recent velocity in logical pixels per second.
Use terminal velocity as input to application-owned retained motion; HuxerUI does not expose a separate fling gesture.

A positive `DragGesture::minimum_press_duration` creates press-then-drag behavior suitable for reorder interactions inside scrolling content.
An axis restricts recognition and reported local movement to that axis.
Do not combine `DragGesture` and `DragSource` on one node for the same physical operation.

## Typed in-process drag-and-drop

`DragSource(payload)` stores one immutable application value and uses ordinary `DragGesture` recognition.
An optional preview factory returns normal `View` content presented in a non-interactive layer.
`DropTarget::Accepts<T>()` accepts only the exact unqualified payload type; its optional predicate must be quick and free of side effects.

```cpp
return CardView(card)
    .With(DragSource(CardTransfer{card.id}, [card] { return CardPreview(card); }))
    .On<DragSourceEvents::Ended>([](const DragDropResult& result) {
      ReportDropResult(result.dropped);
    });
```

Targets bind `DropEvents<T>::Entered`, `Moved`, `Exited`, and `Dropped` through `.On<Key>(...)`.
Perform the authoritative application mutation from `Dropped`; the payload reference is valid only during the callback unless copied or represented by a shared-ownership value.
The source receives `DragSourceEvents::Ended` with `DragDropResult::dropped`, or `Canceled` on abnormal termination.

Compatible targets inside scroll content enable edge auto-scroll through the existing scroll hierarchy.
This contract is in-process only: it does not implicitly transfer files, text, URLs, platform drag-session values, or input ownership to a `PlatformView`.
Provide equivalent keyboard or semantic actions when drag-and-drop performs an essential operation.

## External file reception

Use `FileDropTarget` from `<huxerui/file_drop.h>` for ordinary files dragged from another application, not `DropTarget::Accepts<FileReference>()` or a custom raw-pointer recognizer. Confirm that the active SDK exposes this header before relying on it.

Attach `.With(FileDropTarget::Accepts(options))` and bind `FileDropEvents` through `.On<Key>()`. `FileDropOptions` combines filename suffixes and MIME types as alternatives; empty filters accept any ordinary file. Matching ignores ASCII case, supports compound suffixes and MIME wildcards, and validates the complete batch. Directories, empty batches, or a batch containing an ineligible file are not partially delivered. `allows_multiple = false` rejects multiple-file batches.

An optional `Accepts(options, predicate)` receives `FileDropOffer` with an optional count and possibly incomplete MIME types. Keep the predicate quick and side-effect-free, without I/O or permission requests; hover acceptance is provisional, and metadata filtering does not validate file contents.

Use `Entered`, `Moved`, and `Exited` for hover feedback. Physical drop sends `Exited` before deferred `Dropped` or `Failed`; Exited alone does not mean cancellation. Dropped supplies a borrowed `const std::vector<FileReference>&`, while Failed supplies `FileError`. Copy references to retain access, and distinguish successful reception from subsequent application I/O. Events use the Runtime UI thread and retain the target-local and window DIP coordinates from physical drop. Unmount cancels pending delivery; separate accepted drops may finish independently.

Reception negotiates Copy only and does not automatically read, import, or write files. Dropped references are read-only through reference operations; provider temporary files may need reference-owned storage. Native `PlatformView` regions own their own drops. Android reception requires API 24 and a host permission lease; `HuxerUIActivity` supplies the hook, while custom hosts must follow `HuxerUIView.setFileDropPermissionRequester` and Activity lifetime rules. Provide an equivalent FilePicker action for keyboard, touch, accessibility, and unsupported hosts. See [files and references](resources-files-network.md#references-and-local-paths) for later I/O and optional path access.

## Configuration and ownership

Recognition thresholds default to platform-provided `GestureSettings`; prefer per-gesture optional overrides when application behavior genuinely requires them.
Built-in Click, scrolling, public gestures, and retained pointer behavior share one ownership model, so one accepted recognizer cancels competing recognition and raw delivery.
Recognizer lifecycle and competition state remain mounted without recomposition. An application handler that updates `State` intentionally recomposes its subscribers; use that path when the accumulated value is authoritative application state. Use retained mounted behavior only when high-frequency visual state must advance independently of composition.
