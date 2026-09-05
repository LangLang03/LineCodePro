# Node Extensions

Use a retained modifier with `NodeExtension` only when behavior needs mounted-node lifetime, frame progression, pointer/key/focus input, final presentation geometry, extra paint, or custom semantics. It is behavior attached to one mounted node, not a general plugin host.

## Public lifecycle

An app-side modifier spec exposes a nested `Extension` type. The extension must be constructible as `Extension(ViewNode&, const Spec&)` and provide `Update(ViewNode&, const Spec&)`.

- Constructor: initialize retained state for the newly mounted node.
- `Update`: accept a compatible declarative spec without resetting unrelated retained state.
- Equality-comparable specs allow reconciliation to skip redundant updates.
- `OnFrame`: advance retained animation and return `FrameResult`; use `wake_after` for a timed wakeup.
- `PrepareGeometry(ViewNode&, TextMeasurer&)`: observe final presentation geometry and report which retained paint phase changed.
- `PaintBehindContent` and `PaintAboveContent`: emit platform-neutral paint in node-local coordinates.
- `OnInteraction`, hit testing, pointer, hover, focus, key, Back, and scroll hooks: implement only the behavior the modifier owns.
- `BuildSemantics` and `OnSemanticAction`: expose and handle accessibility behavior.
- `GetTextInputClient` or `GetTextSelectionClient`: only for a genuine custom text editing surface.

Prefer `ViewEvents::Hover` for application-facing mouse or pen presence.
An extension with specialized retained hover geometry overrides `HoverHitTest` and receives complete Enter, Move, and Leave updates through `OnHover(ViewNode&, const HoverEvent&)`.
Override `HoverWhenDisabled` only when that affordance intentionally remains active on a disabled View.

`OnKey` receives the focused node's key event after `KeyIntercept` and focused text-input handling but before the node's public `KeyDown` or `KeyUp` event.
Return `true` only when the extension consumed it; Runtime then skips the public target event and later defaults.

Do not retain raw `ViewNode*`, child references, or platform objects across reconciliation. Visible retained-state changes call protected `InvalidatePaint`; semantic changes call `InvalidateSemantics`. Returning `needs_frame` schedules work but does not itself invalidate an already cached paint sequence.

## Typed output

Use protected `EmitEvent<Key>(...)` to reach `.On<Key>()` on the View carrying the extension. It reads the current binding after recomposition, does not bubble, and does not need `UseEvents()` or an extra composition scope. Notifications return void; value-returning events return `std::optional<Result>`, empty without a handler. Handler exceptions propagate.

Emit synchronously on the owning UI thread from mounted input, semantic actions, or frame updates, not construction, Update, destruction, hit testing, geometry preparation, or paint recording. Emission neither queues work nor extends the extension's lifetime. When an internal child must emit an outer component's event, explicitly pass that component's `EventEmitter` instead. Presentation handles remain explicit dependencies, not additional extension methods.

## Geometry and semantics

`PrepareGeometry` runs after final presentation transforms and before paint recording. Borrow its TextMeasurer only for synchronous retained geometry; store resulting metrics rather than the measurer. Return the exact changed `PaintInvalidation` phases. Text affecting measurement belongs in Layout because this callback does not trigger another layout pass.

Use the [mounted coordinate APIs](layout-and-ui.md#mounted-coordinate-spaces) for window-service geometry and padding-aware content bounds; paint callbacks still record node-local commands. For a self-drawn control with virtual accessible children, follow the [SemanticBuilder availability rules](text-input-and-semantics.md#semantics) rather than creating hidden Views solely for accessibility.

## Choose a simpler mechanism when possible

- ordinary visual state: `Indication`, animation modifiers, or theme style;
- semantic output: typed event;
- measurement: custom `Layout` or `VirtualLayout`;
- mount/unmount work: `Lifecycle`;
- asynchronous work: `TaskScope`;
- non-visual platform service: `PlatformModule`;
- embedded platform control: `PlatformView`.

Continue with a `NodeExtension` only when none of these mechanisms owns the required mounted behavior.

Verify the exact override set and result types in the active SDK header before implementing an extension. Do not copy a generic extension template: frame scheduling, pointer capture, cancellation, reduced motion, paint invalidation, and semantic invalidation depend on the behavior being owned. A frame-driven extension should schedule work only while active, and a custom input extension must implement complete termination and cancellation paths.
