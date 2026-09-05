# Modifiers, Events, and Keys

## Keep the APIs distinct

- `.With(modifiers...)` accepts public `ViewModifier` values.
- `.On<Key>(handler)` binds a typed event; `.OnClick`, `.OnChanged`, and `.OnSubmitted` are conveniences.
- `.Key(value)` assigns reconciliation identity among siblings.
- Component-specific fluent methods configure only that component and should not be replaced by generic modifiers.

Modifiers apply from left to right. Property modifiers alter declaration data without wrapper nodes. Retained modifiers own a `NodeExtension` that survives compatible reconciliation.

## Built-in modifier catalog

| Modifier | Main effect |
| --- | --- |
| `Enabled`, `Focusable` | Input/focus eligibility and semantics |
| `Padding`, `Frame` | Measurement and layout constraints |
| `Spacing`, `MainAlign`, `CrossAlign` | Linear and Flow container layout policy |
| `Align` | `Stack` child-placement policy; unrelated to the component-specific `Text::Align` and `TextField::Align` methods |
| `Grow` | Immediate parent-child layout metadata |
| `Background`, `Border`, `CornerRadius`, `Shadow`, `Foreground`, `Opacity` | Paint/presentation; some affect clipping or presentation bounds as defined by the active SDK |
| `FontSize` | Inherited text styling |
| `ClipChildren` | Descendant clipping without adding a wrapper |
| `ScrollPhysics`, `ScrollBar` | Scroll behavior and scrollbar appearance |
| `Indication` | Focus, hover, press, and ripple visual state |
| `PointerCursor` | Portable mouse or pen cursor declaration; see [gestures-and-drag-drop.md](gestures-and-drag-drop.md) |
| `Offset`, `Scale`, `Rotation`, `Transition` | Presentation animation without changing parent measurement |
| `SafeAreaPadding` | Consumes selected safe-area edges |
| `Semantics` | Accessibility role, label, state, actions, and collection metadata |
| `MultiTapGesture`, `LongPressGesture`, `DragGesture`, `TransformGesture` | Shared gesture recognition and typed events |
| `DragSource`, `DropTarget` | Exact-type in-process drag-and-drop; see [gestures-and-drag-drop.md](gestures-and-drag-drop.md) |
| `Tooltip`, declarative `Dialog`, `LayerAnchor`, `SceneTransitionAnchor` | Presentation integration |

Verify exact fields in the active SDK header. Do not infer a generic modifier from a component-specific style field.

Leave `Shadow::offset` at its default zero value unless the visual design explicitly calls for a directional shadow. Omit an explicit zero `offset`, and do not introduce a directional offset merely to represent theme elevation.

## Extension choice

Use built-in property modifiers for declarative configuration, and package reusable app UI as an ordinary component function. Use a retained modifier only when behavior needs mounted lifetime, frames, input, geometry, paint, or semantics. Use an event for semantic output and a stable key only for identity.

Application layouts normally configure `Align`, `Grow`, `Frame`, and related modifiers through `.With(...)`. Apply `Align` to the `Stack` whose children it places, and apply `Grow` to the direct child whose share of remaining main-axis space it controls. When implementing a custom `Layout`, `.LayoutValue<Key>(value)` remains available for uncommon typed metadata consumed only by the immediate parent layout.
