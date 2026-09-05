# Built-in Components

This is a decision-oriented catalog, not a replacement for the active SDK's public headers. Verify signatures before editing code.

## Content primitives

| Component | Important contract |
| --- | --- |
| `Text` | Takes `StringVariant` and optional `TextRole`; `.Style(TextStyle)` overrides text styling and `.Shaping(TextShapingOptions)` overrides shaping direction or locale. `.Align(TextAlign)` and `.VerticalAlign(TextVerticalAlign)` place the paragraph inside its text rectangle. `Text::Format` supports literal and resource formats. |
| `Image` | Takes `ImageVariant` or `std::shared_ptr<ExternalTexture>`; `.Fit`, `.Align`, `.Sampling`, and `.Tint` are component-specific. |
| `Canvas` | Takes a `CanvasPainter` and paints in the size assigned by layout. |
| `Divider` | Horizontal by default; pass `Axis::Vertical` only when height is bounded. |
| `SelectionArea` | Wraps content that participates in text selection. |

Use the complete container, scrolling, virtualization, navigation-shell, and responsive-layout guidance in [layout-and-ui.md](layout-and-ui.md).

## Actions and choices

| Component | Controlled value and events |
| --- | --- |
| `Button(label)` | Emits click through `.OnClick(...)`; the constructor has no separate enabled state, so use `Enabled`. |
| `IconButton(icon, semantic_label)` | Requires an accessible semantic label and emits click. |
| `Chip(label[, selected])` | Optional controlled selection; `.OnChanged(bool)` requests a new value. Icon overloads are available. |
| `Checkbox([label,] checked)` | Controlled `bool`; `.OnChanged(bool)` requests a new value. |
| `RadioButton([label,] selected)` | Controlled `bool`; group exclusivity remains application-owned. |
| `Switch([label,] checked)` | Controlled `bool`; `.OnChanged(bool)` requests a new value. |
| `SegmentedButton(items, selected_index)` | Controlled index; items may have icon/label or icon-only with semantic label. |
| `Select(items, selected_index, content)` | Controlled index for a finite non-empty range; `.OnChanged(std::size_t)` requests a different choice. |
| `ComboBox(value, suggestions[, text, content])` | Controlled `TextEditingValue` with application-owned suggestions; `.OnChanged` requests direct edits and `.OnSelected` proposes accepted text. |
| `Tabs(items, selected_index)` | Controlled index; `TabItem::Enabled` disables individual destinations. Page content is separately owned. |
| `Slider(value)` | Controlled `float`; configure `.Range` and optional `.Step`, then write `.OnChanged` values back. |
| `DatePicker(value)` | Controlled `std::chrono::year_month_day`; `.OnChanged` proposes a new date. |
| `TimePicker(value)` | Controlled `std::chrono::minutes` since midnight; `.OnChanged` proposes a new time of day. |

Do not rely on constructor overloads that accept `State<T>` to mutate state automatically; they read the current value. Bind `OnChanged` explicitly.

## Select

`Select` copies its input range into the declaration, so temporary ranges are safe.
Its content factory supplies both the selected trigger content and each popup choice; keep the factory declarative and do not rely on invocation count or side effects.
Each factory result must be a non-empty root View with a non-empty semantic label.
`Text` already supplies that label; composite content should apply `Semantics{.label = ...}` to its root.
Use `.Label(...)` for the control's accessible name, `.Validation(...)` for application-owned validation presentation, and the shared `Enabled{false}` modifier on a choice root to disable that choice.
The choice root is one interaction target and cannot contain another independent pointer or focus target.
When choices can insert, remove, or reorder while the popup is open, apply a stable semantic `.Key(...)` to each factory result; otherwise identity follows the current index.
An empty range or an out-of-range selected index throws `std::invalid_argument`.

## ComboBox

`ComboBox` reuses TextField editing, selection, composition, validation, and platform input behavior while adding an anchored suggestion popup.
Keep the complete `TextEditingValue` controlled and derive the current suggestion range in application state; do not add a second selected index because free-form text may not match an item.
The simple range form uses each string-compatible suggestion as accepted text and content.
For rich application data, pass a text projection and a View factory instead of creating a type-erased item wrapper.
Apply a stable `.Key(...)` to the factory result when suggestions can insert, remove, or reorder; otherwise identity follows position.

Write `.OnChanged(const TextEditingValue&)` proposals back after direct edits.
`.OnSelected(std::size_t, const TextEditingValue&)` supplies the accepted index and complete replacement proposal and does not also emit Changed.
`.OnSubmitted()` reports submission without an active suggestion.
Use `.EmptyContent(...)` for non-interactive loading or no-result content; without it, an empty range keeps the popup closed.
Suggestion roots may use `Enabled{false}`, but suggestion and empty-state content cannot contain another pointer action or focusable control.
Do not intercept composition or modified editing keys except for ComboBox-owned Alt+Up/Down popup control, and do not rebuild this behavior from TextField plus an app-owned Popup.

## DatePicker and TimePicker

Use these built-in inline controls before creating a calendar or clock from Canvas or a custom layout. They are not platform-native dialogs; compose them with presentation services when the application needs a dialog.

DatePicker's `.Range`, `.Minimum`, and `.Maximum` use inclusive endpoints. The controlled date must be valid and within the configured range. `.DisabledDates` limits user proposals without correcting application state; `.Validation` reports application-owned domain feedback.

TimePicker accepts minutes in `[0, 1440)`, not seconds or a timestamp. `.Step` is a positive minute interval no greater than 60 that divides 60 exactly, and the controlled value must align with it. Use `.DisabledTimes` for availability, split shifts, or overnight rules; there is no time `Range`, `Minimum`, or `Maximum`. Both pickers take `.Label` and require explicit `.OnChanged` binding even when constructed with State.

Locale supplies localized labels, reading direction, calendar week starts, and 12/24-hour presentation. Neither picker owns a time zone or combines the date and time into an instant. Applications that need an instant must resolve their selected date, time, and zone together and handle nonexistent or ambiguous local times; picker validity alone does not establish that a zoned time exists.

Use `DatePickerStyle` and `TimePickerStyle` from the active Flat or Material theme rather than reproducing a single visual system. See [theme customization](theme-animation-presentation.md) and [locale and shaping](resources-files-network.md#locale-and-text-shaping).

## Input and progress

- `TextField(TextEditingValue)` is controlled by the complete editing value. Configure `.Label`, `.Placeholder`, icons, `.Variant`, `.LineLimits`, `.MaxLength`, `.Validation`, `.Secure`, `.Shaping(TextShapingOptions)`, `.Align(TextAlign)`, `.VerticalAlign(TextVerticalAlign)`, and `.InputConfiguration`, then handle `.OnChanged` and optionally `.OnSubmitted`. Alignment applies to the editable paragraph, not the TextField View's placement.
- `TextFieldVariant` currently contains `Filled`, `Outlined`, and `Standard`.
- `ComboBox` supports the single-line, non-secure, editable subset of `TextInputConfiguration`; use `Select` for read-only finite choices.
- `ProgressCircle()` and `ProgressBar()` are indeterminate. Their `float` constructors are determinate.

## Common review points

- Add an accessible label to icon-only actions.
- Keep selected, checked, text, and progress values controlled.
- Do not hardcode Material assumptions when the current theme can be Flat or custom.
- Give `PlatformView`, `Canvas`, and vertical `Divider` meaningful constraints.
