# Layout and UI

## Choose a built-in layout first

Do not derive a custom `Layout` merely to arrange ordinary application content. Select the existing layout whose measurement model matches the UI:

| Need | Built-in choice | Main configuration |
| --- | --- | --- |
| Horizontal sequence | `Row` | `Spacing`, `MainAlign`, `CrossAlign`, child `Grow` |
| Vertical sequence | `Column` | `Spacing`, `MainAlign`, `CrossAlign`, child `Grow` |
| Horizontal wrapping | `Flow` | `Spacing`, `MainAlign`, `CrossAlign`, child `Grow` |
| Overlaid children | `Stack` | `Align` |
| Flexible empty space | `Spacer` | Already grows with factor `1`; use `Grow(factor)` only to change the ratio |
| Retained page switching | `IndexedPages` | Controlled selected index |
| One ordinary scrolling subtree | `ScrollView` | `.ScrollAxis`, `.Controller`, `ScrollBar` |
| Large linear data | `VirtualList` | Item/estimated extent, cache extent, axis, controller |
| Large grid data | `VirtualGrid` | Fixed/adaptive columns, spans, row geometry, spacing, cache, controller |
| Application top bar | `TopAppBar` | Title, optional leading content and actions, title alignment |
| Adaptive start/end side surfaces | `DrawerLayout` with `StartDrawer` / `EndDrawer` | Controlled open state and responsive placement |
| Application-defined desktop title bar | `WindowTitleBar` | Platform caption insets, drag regions, interactive content |
| Different compact/expanded structure | Composition with `UseViewportClass()` | Share owner state across the selected structures |

Common requirements do not need custom measurement:

- Put content at opposite ends with `Spacer()` or `MainAlign(MainAxisAlignment::SpaceBetween)`.
- Divide remaining main-axis space with `Grow()` or weighted `Grow(factor)` on children.
- Fill the cross axis with `CrossAlign(CrossAxisAlignment::Stretch)`.
- Center or distribute a group with `MainAlign`; do not calculate offsets manually.
- Wrap actions or chips with `Flow`; do not precompute rows from viewport width.
- Overlay badges, placeholders, or surfaces with `Stack`; do not emulate overlay through negative offsets.

`TopAppBar`, drawers, navigation surfaces, and `WindowTitleBar` are semantic application-shell layouts with behavior beyond generic geometry. Use them instead of recreating those shells from `Row`, `Stack`, or a custom layout; see [navigation-and-window.md](navigation-and-window.md).

## Linear layouts

`Row` has a horizontal main axis and vertical cross axis. `Column` has a vertical main axis and horizontal cross axis.

- `Spacing(value)` adds a fixed gap between adjacent children.
- `MainAlign` accepts `Start`, `Center`, `End`, `SpaceBetween`, `SpaceAround`, or `SpaceEvenly`.
- `CrossAlign` accepts `Start`, `Center`, `End`, or `Stretch`.
- `Grow()` is immediate parent-child metadata. Growing children divide bounded remaining main-axis space according to their factors.
- `Spacer()` is an empty child with a default grow factor of `1`.

`Grow` needs a finite main-axis maximum from the parent. It cannot manufacture space inside an unbounded main axis. Apply it to the direct logical child; transparent composition boundaries preserve the value.

```cpp
return Row {
  Text("Account"),
  Spacer(),
  Button("Save"),
}.With(Spacing(8.0F), CrossAlign(CrossAxisAlignment::Center));
```

## Flow, Stack, and pages

`Flow` wraps children into horizontal lines when width is bounded. `Spacing` separates both adjacent items and lines. `MainAlign` distributes each line, `CrossAlign` aligns or stretches children within each line, and `Grow` shares remaining width within the child's line.

`Stack` measures children against the same available area and overlays them. Apply `Align(horizontal, vertical)` to the `Stack` to place its children with `Start`, `Center`, `End`, or `Stretch` on each axis.

```cpp
return Stack {
  Image(background).Fit(ImageFit::Cover),
  ProgressCircle(),
}.With(Align(HorizontalAlignment::Center, VerticalAlignment::Center));
```

`IndexedPages(pages, selected_index)` keeps page nodes in the application tree while measuring and placing the selected page. Use it for tab or destination content that must retain local state; keep the selected index owner-controlled.

## Size and internal space

- `Frame` sets optional exact width/height and minimum/maximum bounds.
- `Padding` reduces the space offered to a node's content and adds the insets back to its measured size.
- Parent constraints remain authoritative; an impossible `Frame` does not override them.
- Prefer min/max bounds and growth over fixed viewport dimensions.

`Text(...).Align(TextAlign::...)` and `TextField(...).Align(TextAlign::...)` place glyphs horizontally inside the component's text rectangle. `.VerticalAlign(TextVerticalAlign::...)` controls paragraph placement on the vertical axis. These component-specific methods do not place the View itself and may be visually unchanged when the paragraph rectangle has only natural size.

Place the entire View with `MainAlign`/`CrossAlign` on a linear parent or `.With(Align(HorizontalAlignment::..., VerticalAlignment::...))` on a `Stack`. The `Align` modifier is Stack child-placement policy and is unrelated to the `Text` and `TextField` methods with the same short name.

## Constraint discipline

Layouts measure through `LayoutContext`, obey incoming constraints, and return a constrained size plus valid placements. Do not solve a constraint problem with an unrelated wrapper. A vertical `Divider` needs bounded height. `PlatformView` has no portable intrinsic size; assign it parent constraints or a `Frame`.

## Mounted coordinate spaces

For custom layouts and retained behavior, use ViewNode's public geometry instead of manually adding offsets or reconstructing transforms:

| API | Meaning |
| --- | --- |
| `Bounds()` | Full node-local rectangle, zero origin, including Padding. |
| `ContentBounds()` | Bounds inset by resolved Padding, including consumed SafeAreaPadding; nonzero origin is possible and dimensions clamp to zero. It does not subtract Border or describe a clip or child union. |
| `LayoutOffset()` | Parent-local layout origin before presentation transforms. |
| `PresentationBounds()` | Transformed axis-aligned full-node bounds in window DIPs, not clipped visibility. |
| `LocalToWindow(point)` / `WindowToLocal(point)` | Point conversion through the resolved transform; the inverse returns an empty optional when non-invertible. |
| `LocalToWindowBounds(rect)` | Window-axis-aligned bounds of all four transformed corners, without clipping. |

These are logical coordinates, not screen coordinates or physical pixels. The current frame's final transform is available in `NodeExtension::PrepareGeometry`; earlier callbacks can observe the previous transform. Drawing remains node-local. Layout measurement already receives content constraints and places children relative to the content origin, so do not add or subtract the owning node's Padding again in a custom layout policy.

## Scrolling

Use `ScrollView` for a conventional content subtree and `VirtualList`/`VirtualGrid` for large item sets. A scrollable needs a bounded viewport and content that can exceed it. Avoid nesting same-axis scrolling unless ownership is intentional.

`ScrollView`, `VirtualList`, and `VirtualGrid` scroll vertically by default. Use `.ScrollAxis(Axis::Horizontal)` only where that interaction is intentional. Attach `ScrollBar()` as a modifier when a visible indicator is required; pass `ScrollBar{style}` only for a local scrollbar override.

Create a composition-retained controller with `UseScrollController()` or `UseScrollController(initial_offset)`, then pass it through the component's `.Controller(...)` API. `ScrollController` supports `ScrollTo`, `ScrollBy`, and `ScrollToItem`. Requests can fail when it is not connected or an item cannot be resolved; treat the returned `bool` as meaningful.

For dynamic virtual content:

- use stable semantic keys for stateful item declarations;
- provide exact extents only when they are actually fixed;
- use estimated extents for variable-size items;
- let the virtual layout own measurement and item lifetime.

`VirtualGrid` supports `GridColumns::Fixed(count)` and `GridColumns::Adaptive(minimum_width)`, per-item spans, row extents or estimates, row/column spacing, and cache extent. Prefer these capabilities to manually constructing virtual rows.

## Responsive structure

Use `UseViewportClass()` to select compact, medium, or expanded application structure. Keep the controlled destination state shared when changing between `NavigationBar`, compact `NavigationPane`, expanded `NavigationPane`, or drawers. Automatic responsiveness belongs in components that explicitly define it; arbitrary application layouts do not transform themselves.

`NavigationBar(items, selected_index)` is the compact bottom destination surface, while `NavigationPane(items, selected_index, expanded)` provides compact or expanded side navigation. `NavigationItem` supports an icon, label, selected icon, and enabled state. These surfaces select destinations; routed page ownership remains with `NavigationStack` as described in [navigation-and-window.md](navigation-and-window.md).

## System UI and focus

Use Window safe-area and content-mode APIs for status bars, navigation bars, cutouts, and custom desktop title bars. Do not emulate system insets with arbitrary root padding. Place text input in a scrollable when a narrow viewport or IME can otherwise obscure the focused field.

## Interaction and accessibility

Review keyboard focus, hover, pressed, disabled, pointer, semantics, and touch target behavior together. Use theme component styles and indication rather than hardcoded Material visuals. Honor reduced motion and avoid per-frame recomposition.

## Custom layout boundary

Derive from `Layout<Derived>` only when the UI requires a measurement or placement algorithm not expressible by the built-ins above. The custom layout owns geometry, not reconciliation, state, input, clipping, or rendering. It measures children only through `LayoutContext`, obeys incoming constraints, and returns a constrained `LayoutResult` with valid placements.

Derive from `VirtualLayout<Derived>` only for a genuinely new demand-driven virtual geometry. Prefer `VirtualList` or `VirtualGrid` whenever their linear, adaptive-column, span, extent, and cache models fit. Do not create a custom layout merely to package reusable UI; use an ordinary component function instead.
